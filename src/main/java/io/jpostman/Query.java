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
 * Represents URL query parameters from a Postman request URL object.
 *
 * <p>Postman stores query parameters under {@code request.url.query} as an
 * array of objects such as {@code {"key":"page", "value":"1"}}. Disabled
 * query parameters are ignored.</p>
 */
public class Query {

    private static final Logger log = LoggerFactory.getLogger(Query.class);

    private final Map<String, String> params = new LinkedHashMap<>();

    /** @return true when no enabled query parameters are present. */
    public boolean isEmpty() {
        return params.isEmpty();
    }

    /** @return query parameters in insertion order. */
    public Map<String, String> getParams() {
        return params;
    }

    /**
     * Looks up a query parameter by key.
     *
     * @param key query parameter name
     * @return query value, or {@code null} when absent
     */
    public String get(String key) {
        return params.get(key);
    }

    /**
     * Parse {@code request.url.query} from a Postman v2.1 request object.
     *
     * @param reqObj the {@code request} JSON object
     * @return query parameters, or an empty {@link Query} when absent
     */
    public static @NonNull Query from(JsonObject reqObj) {
        Query query = new Query();
        if (!reqObj.has("url") || !reqObj.get("url").isJsonObject()) {
            return query;
        }

        JsonObject urlObj = reqObj.getAsJsonObject("url");
        if (!urlObj.has("query") || 
        		!urlObj.get("query").isJsonArray()) {
            return query;
        }

        for (JsonElement item : urlObj.getAsJsonArray("query")) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject q = item.getAsJsonObject();
            String key = q.has("key") && !q.get("key").isJsonNull() ? q.get("key").getAsString() : "";
            String value = q.has("value") && !q.get("value").isJsonNull() ? q.get("value").getAsString() : "";
            boolean disabled = q.has("disabled") && q.get("disabled").getAsBoolean();
            if (!key.isBlank() && !disabled) {
                query.params.put(key, value);
            }
        }
        return query;
    }

    /** Returns a builder pre-populated from this query parameter set. */
    public ParamBuilder<Query> builder() {
        Map<String, String> params = new LinkedHashMap<>(this.params);
        ParamBuilder.Builder<Query> buildFn = () -> {
            Query query = new Query();
            params.forEach(query.params::put);
            return query;
        };
        return new ParamBuilder<>(
        		// ADD
                (key, value) -> params.put(key, String.valueOf(value)),
                // SET (Updates an existing key; throws if the key does not exist)
                (key, value) -> {
                    if (!params.containsKey(key)) {
                        throw new IllegalArgumentException("Query key not found: '" + key + "'");
                    }
                    params.put(key, String.valueOf(value));
                },
                // RESOLVE
                vars -> {
                    for (String k : new ArrayList<>(params.keySet())) {
                        params.put(k, ParamBuilder.substituteVars(params.get(k), vars));
                    }
                },
                // BUILD
                buildFn);
    }

    /**
     * Rebuilds the URL query string from the parsed {@link Query} object.
     * Existing raw query text is removed because Postman's {@code url.query[]}
     * array is treated as the source of truth. URL fragments are preserved and
     * remain after the rebuilt query string.
     *
     * @param sourceUrl raw URL before query rebuild
     * @param query parsed query parameters
     * @return URL with rebuilt query string
     */
    public static String applyQueriesToUrl(String sourceUrl, Query query) {
		if (sourceUrl.isBlank() || query.isEmpty()) {
			return sourceUrl;
		}

		String base = sourceUrl;
		String fragment = "";
		int fragmentIndex = base.indexOf('#');
		if (fragmentIndex >= 0) {
			fragment = base.substring(fragmentIndex);
			base = base.substring(0, fragmentIndex);
		}

		int queryIndex = base.indexOf('?');
		if (queryIndex >= 0) {
			base = base.substring(0, queryIndex);
		}

		String joined = query.getParams().entrySet().stream()
				.map(e -> e.getKey() + "=" + e.getValue())
				.collect(java.util.stream.Collectors.joining("&"));
		return base + "?" + joined + fragment;
	}
    
    /** Logs query parameters at TRACE level. */
    public void print() {
    	log.trace(toDebugString());
	}

	/** Returns verbose diagnostic representation including details. */
	public String toDebugString() {
        if (params.isEmpty()) {
            return "  (no query parameters)";
        } else {
        	return this.toString();
        }
    }

    @Override
    public String toString() {
        return params.entrySet().stream()
                .map(e -> String.format("  %-35s = %s\n", e.getKey(), e.getValue()))
                .collect(Collectors.joining());
    }
}
