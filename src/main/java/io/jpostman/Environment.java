package io.jpostman;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Environment {

	private static final Logger log = LoggerFactory.getLogger(Environment.class);

	private final String name;
	private final Map<String, String> params = new LinkedHashMap<>();

	public Environment(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public Map<String, String> getParams() {
		return params;
	}

	public String get(String key) {
		return params.get(key);
	}

	/**
	 * Returns a {@link Builder} pre-populated from this environment's variables.
	 */
	public ParamBuilder<Environment> builder() {
		String envName = this.name;
		Map<String, String> params = new LinkedHashMap<>(this.params);
		ParamBuilder.Builder<Environment> buildFn = () -> {
			@NonNull Environment env = new Environment(envName);
			params.forEach((String key, String value) -> env.params.put(key, value));
			return env;
		};
		return new ParamBuilder<Environment>(
				// ADD
				(String key, Object value) -> params.put(key, (String) value),
				// SET (Updates an existing key; throws if the key does not exist)
				(String key, Object value) -> {
					if (!params.containsKey(key))
						throw new IllegalArgumentException("Environment key not found: '" + key + "'");
					params.put(key, (String) value);
				},
				// RESOLVE
				vars -> vars.forEach((String key, String value) -> params.put(key, value)),
				// BUILD
				buildFn);
	}

	/**
	 * Load a Postman environment from a file path. Opens and closes the file
	 * internally.
	 *
	 * @param filePath absolute or relative path to the *.postman_environment.json
	 *                 file
	 * @return populated {@link Environment} instance
	 */
	public static Environment load(String filePath) throws IOException {
		return load(new FileInputStream(filePath));
	}

	/**
	 * Load a Postman environment from an already-open {@link Reader}. The caller
	 * retains ownership and is responsible for closing the reader.
	 *
	 * @param reader an open reader positioned at the start of the JSON
	 * @return populated {@link Environment} instance
	 */
	public static Environment load(InputStream is) throws IOException {
		try (Reader reader = new InputStreamReader(is)) {
			return load(JsonParser.parseReader(reader).getAsJsonObject());
		}
	}

	public static Environment load(JsonObject root) throws IOException {
		String envName = root.has("name") ? root.get("name").getAsString() : "Unknown Environment";
		Environment env = new Environment(envName);

		if (root.has("values")) {
			for (JsonElement el : root.getAsJsonArray("values")) {
				JsonObject var = el.getAsJsonObject();
				boolean enabled = !var.has("enabled") || var.get("enabled").getAsBoolean();
				if (enabled && var.has("key")) {
					String key = var.get("key").getAsString();
					String value = var.has("value") ? var.get("value").getAsString() : "";
					env.params.put(key, value);
				}
			}
		}
		return env;
	}

	public void print() {
		log.info(String.format("=== Environment: %s (%d variable%s) ===", name, params.size(), params.size() == 1 ? "" : "s"));
		if (params.isEmpty()) {
			log.trace("  (no variables)");
		} else {
			log.trace(this.toString());
		}
	}

	@Override
	public String toString() {
		return params.entrySet().stream().map(e -> String.format("  %-35s = %s\n", e.getKey(), e.getValue()))
				.collect(Collectors.joining());

	}
}
