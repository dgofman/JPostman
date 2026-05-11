package io.jpostman;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class Body {

	private final String mode;
	private final String raw;
	private final String language;
	private final JsonElement parsed;

	private static final Gson GSON = new Gson();
	private static final String NONE = "none";
	
	private static final Logger log = LoggerFactory.getLogger(Body.class);

	// -------------------------------------------------------------------------
	// Constructor
	// -------------------------------------------------------------------------

	Body(String mode, String raw, String language, JsonElement parsed) {
		this.mode = mode;
		this.raw = raw;
		this.language = language;
		this.parsed = parsed;
	}

	// -------------------------------------------------------------------------
	// Factory
	// -------------------------------------------------------------------------

	public static Body from(JsonObject reqObj) {
		if (!reqObj.has("body") || reqObj.get("body").isJsonNull()) {
			return new Body(NONE, "", "", null);
		}

		JsonObject bodyObj = reqObj.getAsJsonObject("body");
		String mode = get(bodyObj, "mode", NONE);

		switch (mode) {

		case "raw": {
			String raw = get(bodyObj, "raw", "");
			String language = getLanguage(bodyObj);

			JsonElement parsed = null;

			if (!raw.isBlank() && "json".equals(language)) {
				try {
					parsed = JsonParser.parseString(raw);
				} catch (JsonSyntaxException ignored) {
					// invalid JSON → keep raw only
				}
			}

			return new Body(mode, raw, language, parsed);
		}

		case "graphql": {
			String gql = bodyObj.has("graphql") ? bodyObj.get("graphql").toString() : "";
			return new Body(mode, gql, "", null);
		}

		case "formdata":
		case "urlencoded":
			// in this simplified model, we keep raw empty
			String raw = get(bodyObj, mode, "");
			JsonElement parsed = null;
			if (!raw.isBlank()) {
				try {
					parsed = JsonParser.parseString(raw);
				} catch (JsonSyntaxException ignored) {
					// invalid JSON → keep raw only
				}
			}
			return new Body(mode, "", "", parsed);

		default:
			return new Body(mode, "", "", null);
		}
	}

	private static String get(JsonObject obj, String key, String def) {
		return (obj.has(key) &&  !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : def;
	}

	private static String getLanguage(JsonObject bodyObj) {
		if (bodyObj.has("options")) {
			JsonObject options = bodyObj.getAsJsonObject("options");
			if (options.has("raw")) {
				JsonObject raw = options.getAsJsonObject("raw");
				if (raw.has("language")) {
					return raw.get("language").getAsString();
				}
			}
		}
		return "";
	}

	// -------------------------------------------------------------------------
	// Builder (JSON-aware)
	// -------------------------------------------------------------------------

	public ParamBuilder<Body> builder() {
		final String bodyMode = this.mode;
		final String bodyLang = this.language;

		// deep copy so original is not modified
		final JsonObject working = (parsed != null && parsed.isJsonObject()) ? 
				parsed.getAsJsonObject().deepCopy() : new JsonObject();

		return new ParamBuilder<Body>(
				// PUT (loose): add or replace key
				(key, value) -> working.add(key, toJsonElement(value)),
				// SET (strict): must exist
				(key, value) -> {
					if (!working.has(key)) {
						throw new IllegalArgumentException("Body key not found: '" + key + "'");
					}
					working.add(key, toJsonElement(value));
				},
				// RESOLVE (optional — string replace only)
				vars -> {
					for (String k : working.keySet()) {
						JsonElement v = working.get(k);
						if (v.isJsonPrimitive() && 
								v.getAsJsonPrimitive().isString()) {
							String updated = ParamBuilder.substituteVars(v.getAsString(), vars);
							working.addProperty(k, updated);
						}
					}
				},
				// BUILD
				() -> new Body(bodyMode, working.toString(), bodyLang, working.deepCopy()));
	}

	private static JsonElement toJsonElement(Object value) {
		return GSON.toJsonTree(value);
	}

	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------

	public String getMode() {
		return mode;
	}

	public String getRaw() {
		return raw;
	}

	public String getLanguage() {
		return language;
	}

	public JsonElement getParsed() {
		return parsed;
	}
	
	public boolean isEmpty() {
		return mode == NONE;
	}
	
	public void print() {
		log.trace(toString());
	}

	@Override
	public String toString() {
		if ("raw".equals(mode)) {
			return "[" + mode + (language.isEmpty() ? "" : "/" + language) + "] " + raw;
		}
		if (parsed != null) {
			return "[" + mode + "] " + parsed.toString();
		}
		return "[" + mode + "]";
	}
}