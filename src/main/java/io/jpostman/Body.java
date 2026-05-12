package io.jpostman;

import java.util.ArrayList;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

/**
 * Represents a Postman request body.
 *
 * <p>The class keeps the original body mode ({@code raw}, {@code formdata},
 * {@code urlencoded}, {@code graphql}, or {@code none}), the raw text that can
 * be sent to Rest Assured, and, when possible, a parsed {@link JsonElement}.
 * Parsed JSON is used by the fluent builder so variable substitution works
 * recursively inside JSON objects and arrays.</p>
 */
public class Body {

    private static final Gson GSON = new Gson();
    private static final String NONE = "none";
    private static final Logger log = LoggerFactory.getLogger(Body.class);

    private final String mode;
    private final String raw;
    private final String language;
    private final JsonElement parsed;

    Body(String mode, String raw, String language, JsonElement parsed) {
        this.mode = mode;
        this.raw = raw;
        this.language = language;
        this.parsed = parsed;
    }

    /**
     * Parses the {@code body} section from a Postman request JSON object.
     *
     * @param reqObj request JSON object that may contain a {@code body} field
     * @return parsed body; returns {@code none} body when the field is absent,
     *         null, or not a JSON object
     */
    public static Body from(JsonObject reqObj) {
        if (reqObj == null || 
        		!reqObj.has("body") || 
        		reqObj.get("body").isJsonNull()
                || !reqObj.get("body").isJsonObject()) {
            return new Body(NONE, "", "", null);
        }

        JsonObject bodyObj = reqObj.getAsJsonObject("body");
        String mode = getString(bodyObj, "mode", NONE);

        switch (mode) {
        case "raw":
            return parseRawBody(bodyObj, mode);
        case "graphql":
            return parseGraphqlBody(bodyObj, mode);
        case "formdata":
        case "urlencoded":
            return parseStructuredBody(bodyObj, mode);
        default:
            return new Body(mode, "", "", null);
        }
    }

    private static Body parseRawBody(JsonObject bodyObj, String mode) {
        String raw = getString(bodyObj, "raw", "");
        String language = getLanguage(bodyObj);
        JsonElement parsed = null;

        if (!raw.isBlank() && "json".equals(language)) {
            try {
                parsed = JsonParser.parseString(raw);
            } catch (JsonSyntaxException ignored) {
                // Keep invalid JSON as raw text so callers can still send it unchanged.
            }
        }
        return new Body(mode, raw, language, parsed);
    }

    private static Body parseGraphqlBody(JsonObject bodyObj, String mode) {
        String graphql = bodyObj.has("graphql") && !bodyObj.get("graphql").isJsonNull()
                ? bodyObj.get("graphql").toString()
                : "";
        return new Body(mode, graphql, "", null);
    }

    private static Body parseStructuredBody(JsonObject bodyObj, String mode) {
        JsonElement payload = bodyObj.has(mode) && !bodyObj.get(mode).isJsonNull()
                ? bodyObj.get(mode)
                : null;
        JsonElement parsed = parseBodyPayload(payload);
        String raw = parsed == null ? "" : parsed.toString();
        return new Body(mode, raw, "", parsed);
    }

    /**
     * Normalizes Postman {@code formdata} and {@code urlencoded} payloads.
     *
     * <p>Normal Postman exports store these payloads as arrays. Some tests and
     * older/custom exports may store JSON-looking text. A non-blank string is
     * parsed as JSON when possible; invalid strings are preserved as primitives.
     * Blank strings are treated as no body so GET-like requests do not send an
     * accidental {@code ""} payload.</p>
     */
    private static JsonElement parseBodyPayload(JsonElement payload) {
        if (payload == null) {
            return null;
        }

        if (payload.isJsonPrimitive() && payload.getAsJsonPrimitive().isString()) {
            String raw = payload.getAsString();
            if (raw.isBlank()) {
                return null;
            }
            try {
                return JsonParser.parseString(raw);
            } catch (JsonSyntaxException ignored) {
                return new JsonPrimitive(raw);
            }
        }
        return payload.deepCopy();
    }

    private static String getString(JsonObject obj, String key, String def) {
    	JsonElement value = obj.get(key);
        return value != null && !value.isJsonNull() &&
                value.isJsonPrimitive() ? value.getAsString() : def;
    }

    private static String getLanguage(JsonObject bodyObj) {
        if (bodyObj.has("options") && bodyObj.get("options").isJsonObject()) {
            JsonObject options = bodyObj.getAsJsonObject("options");
            if (options.has("raw") && options.get("raw").isJsonObject()) {
                return getString(options.getAsJsonObject("raw"), "language", "");
            }
        }
        return "";
    }

    /**
     * Creates a JSON-aware builder for this body.
     *
     * <p>{@code add(...)} and {@code set(...)} mutate only JSON object bodies.
     * Arrays and primitive JSON values are preserved and can still be resolved,
     * but cannot receive object-key mutations.</p>
     */
    public ParamBuilder<Body> builder() {
        final String bodyMode = this.mode;
        final String bodyLang = this.language;
        final JsonElement[] working = new JsonElement[] { parsed == null ? new JsonObject() : parsed.deepCopy() };

        return new ParamBuilder<>(
                (key, value) -> asObjectForMutation(working[0]).add(key, toJsonElement(value)),
                (key, value) -> {
                    JsonObject obj = asObjectForMutation(working[0]);
                    if (!obj.has(key)) {
                        throw new IllegalArgumentException("Body key not found: '" + key + "'");
                    }
                    obj.add(key, toJsonElement(value));
                },
                vars -> working[0] = substituteJson(working[0], vars),
                () -> {
                    JsonElement result = working[0].deepCopy();
                    return new Body(bodyMode, result.toString(), bodyLang, result);
                });
    }

    private static JsonObject asObjectForMutation(JsonElement el) {
        if (!el.isJsonObject()) {
            throw new IllegalArgumentException("Body builder add/set requires a JSON object body");
        }
        return el.getAsJsonObject();
    }

    private static JsonElement substituteJson(JsonElement el, Map<String, String> vars) {
        if (el.isJsonNull()) {
            return el;
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return GSON.toJsonTree(ParamBuilder.substituteVars(el.getAsString(), vars));
        }
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, substituteJson(arr.get(i), vars));
            }
            return arr;
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            for (String key : new ArrayList<>(obj.keySet())) {
                obj.add(key, substituteJson(obj.get(key), vars));
            }
            return obj;
        }
        return el;
    }

    private static JsonElement toJsonElement(Object value) {
        return GSON.toJsonTree(value);
    }

    /** @return Postman body mode, for example {@code raw}, {@code formdata}, or {@code none}. */
    public String getMode() {
        return mode;
    }

    /** @return raw body text that will be sent by {@link Request#apply}. */
    public String getRaw() {
        return raw;
    }

    /** @return raw body language from Postman options, for example {@code json}. */
    public String getLanguage() {
        return language;
    }

    /** @return parsed JSON body when available; otherwise {@code null}. */
    public JsonElement getParsed() {
        return parsed;
    }

    /** @return true when there is no sendable body content. */
    public boolean isEmpty() {
        return NONE.equals(mode) || raw.isEmpty();
    }

    /** Logs this body at TRACE level. */
    public void print() {
        log.trace(toDebugString());
    }
    
    /** Returns verbose diagnostic representation including details. */
	public String toDebugString() {
	    return toString();
	}

    @Override
    public String toString() {
        if ("raw".equals(mode)) {
            return "[" + mode + (language.isEmpty() ? "" : "/" + language) + "] " + raw;
        }
        if (parsed != null) {
            return "[" + mode + "] " + parsed;
        }
        return "[" + mode + "]";
    }
}
