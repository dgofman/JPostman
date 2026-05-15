package io.jpostman;

import java.util.Map;
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
public class ParamBuilder<T> {

	@FunctionalInterface
	public interface Builder<T> {
		T build();
	}

	/** Adds or updates a key without validation. */
	private final BiConsumer<String, Object> onPut;

	/** Updates an existing key and lets the target object throw when missing. */
	private final BiConsumer<String, Object> onSet;

	/** Resolves variable placeholders using the supplied environment variables. */
	private final Consumer<Map<String, String>> onResolve;

	/** Builds the final target object from the builder state. */
	private final Builder<T> onBuild;

	ParamBuilder(BiConsumer<String, Object> onPut, BiConsumer<String, Object> onSet,
			Consumer<Map<String, String>> onResolve, Builder<T> onBuild) {
		this.onPut = onPut;
		this.onSet = onSet;
		this.onResolve = onResolve;
		this.onBuild = onBuild;
	}

	/** Adds or overwrites the key unconditionally. */
	public ParamBuilder<T> add(String key, Object value) {
		onPut.accept(key, value);
		return this;
	}

	/**
	 * Updates an existing key.
	 *
	 * @throws IllegalArgumentException if the target object requires the key to
	 *                                  exist and the key is missing
	 */
	public ParamBuilder<T> set(String key, Object value) {
		onSet.accept(key, value);
		return this;
	}

	/** Substitutes all {@code {{key}}} tokens using the supplied variable map. */
	public ParamBuilder<T> resolve(Map<String, String> vars) {
		onResolve.accept(vars);
		return this;
	}

	/** Produces the final object. */
	public T end() {
		return onBuild.build();
	}

	/**
	 * Replaces all {@code {{key}}} tokens in {@code value} with entries from
	 * {@code vars}. Unknown tokens are left unchanged.
	 *
	 * @param value source text; may be {@code null}
	 * @param vars variable map; may be {@code null}
	 * @return substituted text, or {@code null} when {@code value} is null
	 */
	public static String substituteVars(String value, Map<String, String> vars) {
		if (value == null || vars == null) {
			return value;
		}
		for (Map.Entry<String, String> e : vars.entrySet()) {
			value = value.replace("{{" + e.getKey() + "}}", e.getValue());
		}
		return value;
	}

	/**
	 * Stores one Postman parameter value together with its enabled/disabled state.
	 *
	 * <p>Postman can keep disabled headers, query parameters, and environment
	 * variables in the exported JSON. Keeping this metadata lets the parser preserve
	 * the original collection structure while the public API can still expose only
	 * enabled values for execution and variable substitution.</p>
	 */
	static public class ParamInfo {

		/** Raw parameter value. Disabled parameters keep their value here too. */
		final String value;

		/** Whether this parameter should participate in execution/resolution output. */
		boolean enabled;

		/**
		 * Creates parameter metadata.
		 *
		 * @param value parameter value; converted to an empty string when {@code null}
		 * @param enabled true when the parameter should be active
		 */
		ParamInfo(String value, boolean enabled) {
			this.value = value;
			this.enabled = enabled;
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
		 * @param enabled {@code true} to enable the parameter;
		 *                {@code false} to disable it
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
