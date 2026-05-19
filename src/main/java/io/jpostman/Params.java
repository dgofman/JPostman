package io.jpostman;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.google.gson.Gson;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Generic fluent builder for Postman parameter maps. Wraps domain-specific
 * add/set/resolve/build logic via lambdas so that {@link Header}, {@link Body},
 * {@link Auth}, {@link Url}, and {@link Environment} can share one builder
 * class instead of duplicating the same fluent API.
 *
 * @param <T> the type produced by {@link #end()}
 */
public class Params<T> {

	private static final Gson GSON = new Gson();
	private static final Handlebars HANDLEBARS = new Handlebars().with(EscapingStrategy.NOOP);
	private static final Pattern HANDLEBARS_TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*\\}\\}");
	private static final ThreadLocal<Boolean> PARTIAL_RESOLVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

	@FunctionalInterface
	public interface Builder<T> {
		T build();
	}

	/** Adds or overwrites a request-part value. */
	private final BiConsumer<String, Object> onPut;

	/** Updates an existing key and lets the target object throw when missing. */
	private final BiConsumer<String, Object> onSet;

	/** Resolves template placeholders using the supplied variables. */
	private final Consumer<Map<String, ?>> onResolve;

	/** Builds the final target object from the builder state. */
	private final Builder<T> onBuild;

	Params(BiConsumer<String, Object> onPut, BiConsumer<String, Object> onSet, Consumer<Map<String, ?>> onResolve,
			Builder<T> onBuild) {
		this.onPut = onPut;
		this.onSet = onSet;
		this.onResolve = onResolve;
		this.onBuild = onBuild;
	}

	/** Adds or overwrites the key unconditionally. */
	public Params<T> add(String key, Object value) {
		onPut.accept(key, value);
		return this;
	}

	/**
	 * Updates an existing key.
	 *
	 * @throws IllegalArgumentException if the target object requires the key to
	 *                                  exist and the key is missing
	 */
	public Params<T> set(String key, Object value) {
		onSet.accept(key, value);
		return this;
	}

	/** Substitutes all {@code {{key}}} tokens using the supplied variable map. */
	public Params<T> resolve(Map<String, ?> vars) {
		if (vars != null) {
			onResolve.accept(vars);
		}
		return this;
	}

	/** Produces the final object. */
	public T end() {
		return onBuild.build();
	}

	/**
	 * Resolves only variables present in the local parameter map, then produces the
	 * final object.
	 *
	 * <p>
	 * Values resolved here have priority over later request-level resolution
	 * because replaced tokens are no longer available for {@code build(env)}.
	 * Variables not present in {@code vars} are intentionally left unchanged so
	 * {@code build(env)} can resolve them later.
	 * </p>
	 *
	 * @param vars local variables used only for this builder
	 * @return final object
	 */
	public T end(Map<String, ?> vars) {
		if (vars != null) {
			PARTIAL_RESOLVE.set(Boolean.TRUE);
			try {
				onResolve.accept(vars);
			} finally {
				PARTIAL_RESOLVE.remove();
			}
		}
		return end();
	}

	/**
	 * Creates an ordered variable map from alternating key/value pairs.
	 *
	 * @param key   first variable name
	 * @param value first variable value
	 * @param rest  remaining key/value pairs
	 * @return ordered variable map
	 */
	public static Map<String, Object> asMap(String key, Object value, Object... rest) {
		return toMap(false, key, value, rest);
	}

	/**
	 * Creates an ordered variable map and JSON-stringifies String values.
	 *
	 * @param key   first variable name
	 * @param value first variable value
	 * @param rest  remaining key/value pairs
	 * @return ordered JSON-ready variable map
	 */
	public static Map<String, Object> asJson(String key, Object value, Object... rest) {
		return toMap(true, key, value, rest);
	}

	/**
	 * Creates a mutable ordered map by merging the supplied maps.
	 *
	 * <p>
	 * When the same key exists in multiple maps, the later map wins.
	 * </p>
	 *
	 * @param maps source maps; null maps are ignored
	 * @return mutable merged map
	 */
	@SafeVarargs
	public static Map<String, Object> copy(Map<String, ?>... maps) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map<String, ?> map : maps) {
			if (map != null) {
				result.putAll(map);
			}
		}
		return result;
	}

	/**
	 * Creates a mutable list from the supplied values.
	 *
	 * @param values list values
	 * @param <T> value type
	 * @return mutable list containing the supplied values
	 */
	@SafeVarargs
	public static <T> List<T> asList(T... values) {
	    List<T> result = new ArrayList<>();
        for (T value : values) {
            result.add(value);
        }
	    return result;
	}

	/**
	 * Resolves variables using local key/value pairs, then produces the final
	 * object.
	 *
	 * <p>
	 * This is a convenience alias for {@code end(Map.of(...))} without the
	 * {@code Map.of(...)} wrapper.
	 * </p>
	 *
	 * @param key   first key
	 * @param value first value
	 * @param rest  remaining alternating key/value pairs
	 * @return final object
	 */
	public T map(String key, Object value, Object... rest) {
		return end(toMap(false, key, value, rest));
	}

	/**
	 * Resolves variables using local key/value pairs where String values are
	 * JSON-stringified, then produces the final object.
	 *
	 * <p>
	 * Use this for raw JSON templates that need quoted string values, for example
	 * {@code {"username":{{username}}}}.
	 * </p>
	 *
	 * @param key   first key
	 * @param value first value
	 * @param rest  remaining alternating key/value pairs
	 * @return final object
	 */
	public T json(String key, Object value, Object... rest) {
		return end(toMap(true, key, value, rest));
	}

	private static Map<String, Object> toMap(boolean stringifyStrings, String key, Object value, Object... rest) {
		int restLength = rest == null ? 0 : rest.length;
		if (restLength % 2 != 0) {
			throw new IllegalArgumentException("Key/value arguments must be pairs.");
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put(key, convertValue(value, stringifyStrings));

		for (int i = 0; i < restLength; i += 2) {
			Object rawKey = rest[i];
			Object rawValue = rest[i + 1];
			if (!(rawKey instanceof String)) {
				throw new IllegalArgumentException("Key must be String. Found: " + rawKey);
			}
			result.put((String) rawKey, convertValue(rawValue, stringifyStrings));
		}
		return result;
	}

	private static Object convertValue(Object value, boolean stringifyStrings) {
		return stringifyStrings && value instanceof String ? GSON.toJson(value) : value;
	}

	/**
	 * Replaces all {@code {{key}}} tokens in {@code value} with entries from
	 * {@code vars} using Handlebars. Unknown tokens use normal Handlebars behavior
	 * and render as empty strings.
	 *
	 * @param value source text; may be {@code null}
	 * @param vars  variable map; may be {@code null}
	 * @return substituted text, or {@code null} when {@code value} is null
	 */
	public static String substituteVars(String value, Map<String, ?> vars) {
		if (value == null || vars == null) {
			return value;
		}
		if (Boolean.TRUE.equals(PARTIAL_RESOLVE.get())) {
			return renderProvidedTokensOnly(value, vars);
		}
		return renderHandlebars(value, vars);
	}

	private static String renderProvidedTokensOnly(String value, Map<String, ?> vars) {
		Matcher matcher = HANDLEBARS_TOKEN.matcher(value);
		StringBuffer resolved = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(1);
			if (vars.containsKey(key)) {
				matcher.appendReplacement(resolved, Matcher.quoteReplacement(String.valueOf(vars.get(key))));
			} else {
				matcher.appendReplacement(resolved, Matcher.quoteReplacement(matcher.group(0)));
			}
		}
		matcher.appendTail(resolved);
		return resolved.toString();
	}

	private static String renderHandlebars(String value, Map<String, ?> vars) {
		try {
			Template template = HANDLEBARS.compileInline(value);
			return template.apply(vars);
		} catch (Exception ex) {
			// Keep the old deterministic behavior as a safe fallback if a template
			// contains syntax Handlebars cannot compile.
			String resolved = value;
			for (Map.Entry<String, ?> e : vars.entrySet()) {
				resolved = resolved.replace("{{" + e.getKey() + "}}", String.valueOf(e.getValue()));
			}
			return resolved;
		}
	}

	/**
	 * Stores one Postman parameter value together with its enabled/disabled state.
	 *
	 * <p>
	 * Postman can keep disabled headers, query parameters, and environment
	 * variables in the exported JSON. Keeping this metadata lets the parser
	 * preserve the original collection structure while the public API can still
	 * expose only enabled values for execution and variable substitution.
	 * </p>
	 */
	static public class Entry {

		/** Raw parameter value. Disabled parameters keep their value here too. */
		final String value;

		/** Whether this parameter should participate in execution/resolution output. */
		boolean enabled;

		/**
		 * Creates parameter metadata.
		 *
		 * @param value   parameter value; converted to an empty string when
		 *                {@code null}
		 * @param enabled true when the parameter should be active
		 */
		Entry(String value, boolean enabled) {
			this.value = value;
			this.enabled = enabled;
		}

		/**
		 * Returns the raw parameter value.
		 *
		 * @return parameter value
		 */
		public String getValue() {
			return value;
		}

		/**
		 * Returns whether this parameter is enabled.
		 *
		 * @return {@code true} when enabled
		 */
		public boolean isEnabled() {
			return enabled;
		}

		/**
		 * Enables or disables this parameter.
		 *
		 * @param enabled {@code true} to enable the parameter; {@code false} to disable
		 *                it
		 */
		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		@Override
		public String toString() {
			return String.format("value=%s, enabled=%b", value, enabled);
		}
	}
}
