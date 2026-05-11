package io.jpostman;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Generic fluent builder for Postman parameter maps. Wraps domain-specific
 * set/resolve/build logic via lambdas so that {@link Header}, {@link Body},
 * {@link Auth}, and {@link Environment} all share one builder class instead of
 * four.
 *
 * @param <T> the type produced by {@link #end()}
 */
public class ParamBuilder<T> {

	@FunctionalInterface
	public interface Builder<T> {
		T build();
	}


	/**
	 * PUT (loose): adds or updates a key without validation
	 */
	private final BiConsumer<String, Object> onPut;

	/**
	 * SET (strict - recommended): updates an existing key; throws if the key does not exist
	 */
	private final BiConsumer<String, Object> onSet;
	

	/**
	 * RESOLVE: applies variable substitution or transformation to parameters
	 */
	private final Consumer<Map<String, String>> onResolve;
	

	/**
	 * BUILD: constructs the final object from current state
	 */
	private final Builder<T> onBuild;

	ParamBuilder(BiConsumer<String, Object> onPut, BiConsumer<String, Object> onSet,
			Consumer<Map<String, String>> onResolve, Builder<T> onBuild) {
		// PUT: add or update key (no validation)
		this.onPut = onPut;
		// SET: update existing key only (throws if missing)
		this.onSet = onSet;
		// RESOLVE: apply variable substitution
		this.onResolve = onResolve;
		// BUILD: create final object
		this.onBuild = onBuild;
	}

	/** Adds or overwrites the key unconditionally. */
	public ParamBuilder<T> add(String key, Object value) {
		onPut.accept(key, value);
		return this;
	}

	/** @throws IllegalArgumentException if the key is not valid (strict mode) */
	public ParamBuilder<T> set(String key, Object value) {
		onSet.accept(key, value);
		return this;
	}

	/** Substitutes all {@code {{key}}} tokens using the supplied variable map. */
	public ParamBuilder<T> resolve(Map<String, String> vars) {
		onResolve.accept(vars);
		return this;
	}

	/** Produces the final immutable object. */
	public T end() {
		return onBuild.build();
	}

	public static String substituteVars(String value, Map<String, String> vars) {
		if (value == null)
			return null;
		for (Map.Entry<String, String> e : vars.entrySet())
			value = value.replace("{{" + e.getKey() + "}}", e.getValue());
		return value;
	}
}
