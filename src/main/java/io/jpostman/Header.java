package io.jpostman;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the HTTP headers of a Postman request.
 *
 * <p>Headers are stored as an ordered key/value map. Disabled Postman headers
 * are ignored during parsing.</p>
 */
public class Header {

	private static final Logger log = LoggerFactory.getLogger(Header.class);
	
	private final Map<String, String> params = new LinkedHashMap<>();


	/** @return true when no enabled headers are present. */
	public boolean isEmpty() {
		return params.isEmpty();
	}
	
	/** @return mutable header map in insertion order. */
	public Map<String, String> getParams() {
		return params;
	}

	/**
	 * Returns a header by name.
	 *
	 * @param key header name
	 * @return header value, or {@code null} when absent
	 */
	public String get(String key) {
		return params.get(key);
	}
	
	
	// -------------------------------------------------------------------------
	// Factory
	// -------------------------------------------------------------------------

	/**
	 * Parse the {@code header} array from a Postman v2.1 {@code request} object.
	 *
	 * @param reqObj the {@code request} JSON object
	 * @return immutable list of {@link Header} entries; empty if none present
	 */
	public static @NonNull Header from(JsonObject reqObj) {
		Header header = new Header();
		if (!reqObj.has("header"))
			return header;
		JsonElement el = reqObj.get("header");
		if (!el.isJsonArray())
			return header;
		for (JsonElement item : el.getAsJsonArray()) {
			if (!item.isJsonObject())
				continue;
			JsonObject h = item.getAsJsonObject();
			String key = h.has("key") ? h.get("key").getAsString() : "";
			String value = h.has("value") ? h.get("value").getAsString() : "";
			boolean disabled = h.has("disabled") && h.get("disabled").getAsBoolean();
			if (!key.isBlank() && !disabled) {
				header.params.put(key, value);
			}
		}
		return header;
	}

	/**
	 * Returns a {@link ParamBuilder} pre-populated from this header map.
	 */
	public ParamBuilder<Header> builder() {
		Map<String, String> params = new LinkedHashMap<>(this.params);
		ParamBuilder.Builder<Header> buildFn = () -> {
			Header header = new Header();
			params.forEach(header.params::put);
			return header;
		};
		return new ParamBuilder<Header>(
				// ADD
				(String key, Object value) -> params.put(key, (String) value),
				// SET (Updates an existing key; throws if the key does not exist)
				(String key, Object value) -> {
					if (!params.containsKey(key))
						throw new IllegalArgumentException("Header key not found: '" + key + "'");
					params.put(key, (String) value);
				},
				// RESOLVE
				vars -> {
					for (String k : new ArrayList<>(params.keySet()))
						params.put(k, ParamBuilder.substituteVars(params.get(k), vars));
				},
				// BUILD
				buildFn);
	}

	/** Logs headers at TRACE level. */
	public void print() {
		log.trace(toDebugString());
	}

	/** Returns verbose diagnostic representation including details. */
	public String toDebugString() {
		if (params.isEmpty()) {
			return "  (no headers)";
		} else {
			return this.toString();
		}
	}

	@Override
	public String toString() {
		return params.entrySet().stream().map(e -> String.format("  %-35s = %s\n", e.getKey(), e.getValue()))
				.collect(Collectors.joining());

	}
}
