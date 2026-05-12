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

/**
 * Represents a Postman environment and its key/value variables.
 * Variables are later used to resolve {@code {{name}}} placeholders in
 * requests, headers, queries, bodies, and auth fields.
 */
public class Environment {

	private static final Logger log = LoggerFactory.getLogger(Environment.class);

	private final String name;
	private final Map<String, String> params = new LinkedHashMap<>();

	public Environment(String name) {
		this.name = name;
	}

	/** @return environment name. */
	public String getName() {
		return name;
	}

	/** @return environment variables in insertion order. */
	public Map<String, String> getParams() {
		return params;
	}

	/**
	 * Looks up a variable by key.
	 *
	 * @param key variable name
	 * @return variable value, or {@code null} when absent
	 */
	public String get(String key) {
		return params.get(key);
	}

	/**
	 * Returns a {@link ParamBuilder} pre-populated from this environment's variables.
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
	 * Load a Postman environment from an input stream. The stream is closed by this method.
	 *
	 * @param is input stream positioned at the start of the environment JSON
	 * @return populated {@link Environment} instance
	 */
	public static Environment load(InputStream is) throws IOException {
		try (Reader reader = new InputStreamReader(is)) {
			return load(JsonParser.parseReader(reader).getAsJsonObject());
		}
	}

	/**
	 * Load an environment from an already parsed JSON object. Disabled entries and
	 * entries without a key are skipped. Missing values are stored as empty strings.
	 *
	 * @param root environment root JSON object
	 * @return populated environment
	 */
	public static Environment load(JsonObject root) throws IOException {
		String envName = root.has("name") && 
				!root.get("name").isJsonNull()
				? root.get("name").getAsString()
				: "Unknown Environment";
		Environment env = new Environment(envName);

		if (root.has("values") && root.get("values").isJsonArray()) {
			for (JsonElement el : root.getAsJsonArray("values")) {
				if (!el.isJsonObject()) {
					continue;
				}
				JsonObject var = el.getAsJsonObject();
				boolean enabled = !var.has("enabled") || var.get("enabled").getAsBoolean();
				if (enabled && var.has("key") && !var.get("key").isJsonNull()) {
					String key = var.get("key").getAsString();
					String value = var.has("value") && 
							!var.get("value").isJsonNull()
							? var.get("value").getAsString()
							: "";
					env.params.put(key, value);
				}
			}
		}
		return env;
	}

	/** Logs the environment name and variables. */
	public void print() {
		log.trace(toDebugString());
	}

	/** Returns verbose diagnostic representation including details. */
	public String toDebugString() {
		StringBuilder sb = new StringBuilder();
	    sb.append(String.format("=== Environment: %s (%d variable%s) ===", name, params.size(), params.size() == 1 ? "" : "s"));
		if (params.isEmpty()) {
			sb.append("\n  (no variables)");
		} else {
	        sb.append(toString());
		}
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return params.entrySet().stream().map(e -> String.format("  %-35s = %s\n", e.getKey(), e.getValue()))
				.collect(Collectors.joining());

	}
}
