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

import io.jpostman.Params.Entry;

/**
 * Represents a Postman environment and its key/value variables.
 *
 * <p>Disabled variables are preserved internally, but they are excluded from
 * {@link #getParams()} so they do not participate in {@code {{name}}}
 * substitution.</p>
 */
public class Environment {

	private static final Logger log = LoggerFactory.getLogger(Environment.class);

	private final String name;
	private final Map<String, Entry> params = new LinkedHashMap<>();

	public Environment(String name) {
		this.name = name;
	}

	/** @return environment name. */
	public String getName() {
		return name;
	}

	/** @return enabled environment variables in insertion order. */
	public Map<String, String> getParams() {
		Map<String, String> enabled = new LinkedHashMap<>();
		params.forEach((key, info) -> {
			if (info.enabled) {
				enabled.put(key, info.value);
			}
		});
		return enabled;
	}
	
	/**
	 * Looks up parameter metadata by key.
	 *
	 * @param key parameter name
	 * @return matching {@link Entry}, or {@code null} when absent
	 */
	public Entry getParam(String key) {
		return params.get(key);
	}

	/**
	 * Looks up an enabled variable by key.
	 *
	 * @param key variable name
	 * @return variable value, or {@code null} when absent or disabled
	 */
	public String get(String key) {
		Entry info = getParam(key);
		return info != null && info.enabled ? info.value : null;
	}

	/** Returns a {@link Params} pre-populated from this environment. */
	public Params<Environment> builder() {
		String envName = this.name;
		Map<String, Entry> params = new LinkedHashMap<>(this.params);
		Params.Builder<Environment> buildFn = () -> {
			@NonNull Environment env = new Environment(envName);
			params.forEach(env.params::put);
			return env;
		};
		return new Params<Environment>(
				// ADD
				(String key, Object value) -> params.put(key, new Entry(String.valueOf(value), true)),
				// SET
				(String key, Object value) -> {
					Entry info = params.get(key);
					if (info == null) {
						throw new IllegalArgumentException("Environment key not found: '" + key + "'");
					}
					params.put(key, new Entry(String.valueOf(value), info.enabled));
				},
				// RESOLVE
				vars -> vars.forEach((key, value) ->
						params.put(key, new Entry(String.valueOf(value), true))),
				// BUILD
				buildFn);
	}

	/**
	 * Load a Postman environment from a file path. Opens and closes the file
	 * internally.
	 */
	public static Environment load(String filePath) throws IOException {
		return load(new FileInputStream(filePath));
	}

	/** Load a Postman environment from an input stream. */
	public static Environment load(InputStream is) throws IOException {
		try (Reader reader = new InputStreamReader(is)) {
			return load(JsonParser.parseReader(reader).getAsJsonObject());
		}
	}

	/**
	 * Load an environment from an already parsed JSON object. Disabled entries and
	 * entries without a key are preserved/skipped respectively. Missing values are
	 * stored as empty strings.
	 *
	 * @param root environment root JSON object
	 * @return populated environment
	 */
	public static Environment load(JsonObject root) throws IOException {
		String envName = root.has("name") && !root.get("name").isJsonNull()
				? root.get("name").getAsString()
				: "Unknown Environment";
		Environment env = new Environment(envName);

		if (root.has("values") && root.get("values").isJsonArray()) {
			for (JsonElement el : root.getAsJsonArray("values")) {
				if (!el.isJsonObject()) {
					continue;
				}
				JsonObject var = el.getAsJsonObject();
				if (var.has("key") && !var.get("key").isJsonNull()) {
					String key = var.get("key").getAsString();
					String value = var.has("value") && !var.get("value").isJsonNull()
							? var.get("value").getAsString()
							: "";
					boolean enabled = !var.has("enabled") || var.get("enabled").getAsBoolean();
					env.params.put(key, new Entry(value, enabled));
				}
			}
		}
		return env;
	}

	/** Logs the environment name and enabled variables. */
	public void print() {
		log.trace(toDebugString());
	}

	/** Returns verbose diagnostic representation including enabled variables. */
	public String toDebugString() {
		Map<String, String> enabled = getParams();
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("=== Environment: %s (%d variable%s) ===", name, enabled.size(), enabled.size() == 1 ? "" : "s"));
		if (enabled.isEmpty()) {
			sb.append("\n  (no variables)");
		} else {
			sb.append('\n' + toString());
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return getParams().entrySet().stream()
				.map(e -> String.format("  %-35s = %s\n", e.getKey(), e.getValue()))
				.collect(Collectors.joining());
	}
}
