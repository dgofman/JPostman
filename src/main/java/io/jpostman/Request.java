package io.jpostman;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a single Postman request item parsed from a Collection v2.1
 * export. Use {@link #from(String, String, JsonObject)} to construct from raw
 * JSON.
 */
public class Request {

	private static final Logger log = LoggerFactory.getLogger(Request.class);

	private final String name;
	private final String method;
	private final String url;
	private final String folderName;
	private final String description;
	private final Header header;
	private final Query query;
	private final Body body;
	private final Auth auth;

	private Request(String name, String method, String url, String folderName, String description, 
			Header header, Query query, Body body, Auth auth) {
		this.name = name;
		this.method = method;
		this.url = url;
		this.folderName = folderName;
		this.description = description;
		this.header = header;
		this.query = query;
		this.body = body;
		this.auth = auth;
	}

	// -------------------------------------------------------------------------
	// Factory
	// -------------------------------------------------------------------------

	/**
	 * Build a {@link Request} from a Postman v2.1 {@code request} JSON object.
	 *
	 * @param name       the item name from the collection
	 * @param folderName parent folder name, or {@code "(root)"} for top-level items
	 * @param reqObj     the {@code request} object inside the item JSON
	 */
	public static Request from(String name, String folderName, JsonObject reqObj) {
		String method = reqObj.has("method") ? reqObj.get("method").getAsString() : "GET";
		String url = extractUrl(reqObj);
		String desc = extractDescription(reqObj);

		Header header = Header.from(reqObj);
		Query query = Query.from(reqObj);
		Body body = Body.from(reqObj);
		Auth auth = Auth.from(reqObj);

		return new Request(name, method, url, folderName, desc, header, query, body, auth);
	}

	// -------------------------------------------------------------------------
	// Extraction helpers
	// -------------------------------------------------------------------------

	/** Handles both string and object URL forms used in Postman v2.0 and v2.1. */
	private static String extractUrl(JsonObject reqObj) {
		if (!reqObj.has("url"))
			return "";
		JsonElement urlEl = reqObj.get("url");
		if (urlEl.isJsonPrimitive())
			return urlEl.getAsString();
		if (urlEl.isJsonObject()) {
			JsonObject urlObj = urlEl.getAsJsonObject();
			return urlObj.has("raw") ? urlObj.get("raw").getAsString() : "";
		}
		return "";
	}

	/** Description may be a plain string or a {@code {content, type}} object. */
	private static String extractDescription(JsonObject reqObj) {
		if (!reqObj.has("description"))
			return "";
		JsonElement el = reqObj.get("description");
		if (el.isJsonPrimitive())
			return el.getAsString();
		if (el.isJsonObject()) {
			JsonObject obj = el.getAsJsonObject();
			return obj.has("content") ? obj.get("content").getAsString() : "";
		}
		return "";
	}

	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------

	public String getName() {
		return name;
	}

	public String getMethod() {
		return method;
	}

	public String getUrl() {
		return url;
	}

	public String getFolderName() {
		return folderName;
	}

	public String getDescription() {
		return description;
	}

	public Header getHeader() {
		return header;
	}

	public Query getQuery() {
		return query;
	}

	public Query getQueries() {
		return query;
	}

	public Body getBody() {
		return body;
	}

	public Auth getAuth() {
		return auth;
	}

	/**
	 * Returns a {@link RequestBuilder} pre-populated from this request. Override
	 * headers, body, and auth params before calling {@link RequestBuilder#build()}.
	 *
	 * <pre>{@code
	 * Request built = request.build().header().set("X-AUTH-APIKEY", "my-key").done().body()
	 * 		.add("raw", "{\"user_name\":\"admin\"}").done().auth().set("token", "my-bearer").done().build();
	 * }</pre>
	 */
	public RequestBuilder builder() {
		return new RequestBuilder(this);
	}

	// -------------------------------------------------------------------------
	// RequestBuilder
	// -------------------------------------------------------------------------

	/**
	 * Fluent builder wrapping a copied {@link Request}. Use {@link #headers()},
	 * {@link #body()}, {@link #auth()} to enter sub-builders, then call
	 * {@code .done()} to return here, and finally {@link #build()} to get the
	 * immutable {@link Request}.
	 */
	public static class RequestBuilder {

		private String url;
		private final String name;
		private final String method;
		private final String folderName;
		private final String description;
		private final ParamBuilder<Header> headerBuilder;
		private final ParamBuilder<Query> queryBuilder;
		private final ParamBuilder<Body> bodyBuilder;
		private final ParamBuilder<Auth> authBuilder;

		private RequestBuilder(Request req) {
			this.name = req.name;
			this.method = req.method;
			this.url = req.url;
			this.folderName = req.folderName;
			this.description = req.description;
			this.bodyBuilder = req.body.builder();
			this.authBuilder = req.auth.builder();
			this.headerBuilder = req.header.builder();
			this.queryBuilder = req.query.builder();
		}

		/**
		 * Configures auth using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder()
		 *     .auth()
		 *         .set("username", "dgofman")
		 *         .set("password", "secret").end()
		 *     .build();
		 * }</pre>
		 */
		public ParamStep auth() {
			return new ParamStep(authBuilder);
		}

		/**
		 * Configures auth using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder()
		 *     .auth(c -> c
		 *         .set("username", "dgofman")
		 *         .set("password", "secret"))
		 *     .build();
		 * }</pre>
		 */
		public RequestBuilder auth(Consumer<ParamStep> customizer) {
			customizer.accept(auth());
			return this;
		}
		
		/**
		 * Configures headers using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder()
		 *     .headers()
		 *         .set("Authorization", token)
		 *         .set("Content-Type", "application/json").end()
		 *     .build();
		 * }</pre>
		 */
		public ParamStep headers() {
			return new ParamStep(headerBuilder);
		}

		/**
		 * Configures headers using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder()
		 *     .headers(c -> c
		 *         .set("Authorization", token)
		 *         .set("Content-Type", "application/json"))
		 *     .build();
		 * }</pre>
		 */
		public RequestBuilder headers(Consumer<ParamStep> customizer) {
			customizer.accept(headers());
			return this;
		}

		/**
		 * Configures queries using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder()
		 *     .queries()
		 *         .set("test", "JPostman")
		 *         .set("fontSize", "20").end()
		 *     .build();
		 * }</pre>
		 */
		public ParamStep queries() {
			return new ParamStep(queryBuilder);
		}

		/**
		 * Configures queries using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder()
		 *     .queries(c -> c
		 *         .set("test", "JPostman")
		 *         .set("fontSize", "20"))
		 *     .build();
		 * }</pre>
		 */
		public RequestBuilder queries(Consumer<ParamStep> customizer) {
			customizer.accept(queries());
			return this;
		}

		/**
		 * Configures body using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder()
		 *     .body()
		 *         .set("username", "dgofman")
		 *         .set("password", "secret").end()
		 *     .build();
		 * }</pre>
		 */
		public ParamStep body() {
			return new ParamStep(bodyBuilder);
		}

		/**
		 * Configures body using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder()
		 *     .body(c -> c
		 *         .set("username", "dgofman")
		 *         .set("password", "secret"))
		 *     .build();
		 * }</pre>
		 */
		public RequestBuilder body(Consumer<ParamStep> customizer) {
			customizer.accept(body());
			return this;
		}

		/** Builds and returns the final immutable {@link Request}. */
		public Request build() {
			Query query = queryBuilder.end();
			return new Request(name, method, Query.applyQueriesToUrl(url, query), folderName, description, 
					headerBuilder.end(), query, bodyBuilder.end(), authBuilder.end());
		}

		/**
		 * Resolves all {@code {{variable}}} tokens in URL, headers, body, and auth
		 * params using the given environment's variable map.
		 */
		public Request build(Environment env) {
			Map<String, String> vars = env.getParams();
			url = ParamBuilder.substituteVars(url, vars);
			headerBuilder.resolve(vars);
			queryBuilder.resolve(vars);
			bodyBuilder.resolve(vars);
			authBuilder.resolve(vars);
			return build();
		}

		public class ParamStep {
			private final ParamBuilder<?> delegate;

			ParamStep(ParamBuilder<?> delegate) {
				this.delegate = delegate;
			}

			public ParamStep add(String key, String value) {
				delegate.add(key, value);
				return this;
			}

			public ParamStep set(String key, String value) {
				delegate.set(key, value);
				return this;
			}

			public RequestBuilder end() {
				return RequestBuilder.this;
			}
		}
	}
	
	/**
	 * Applies all headers to Rest Assured request specification.
	 *
	 * @param spec target request specification
	 * @return same specification for chaining
	 */
	public RequestSpecification apply(RequestSpecification spec) {
		header.getParams().forEach(spec::header);
		if ("json".equals(body.getLanguage())) {
			spec.contentType(ContentType.JSON);
		}
		if (!body.isEmpty()) {
			spec.body(body.getRaw());
		}
	    return spec;
	}
	
	/**
	 * Applies the request configuration and executes the HTTP request
	 * using the configured request method.
	 *
	 * <p>This method internally delegates to {@link #apply(RequestSpecification)}
	 * to populate headers, body, content type, cookies, queries, and other
	 * request settings before executing the final HTTP call.</p>
	 *
	 * <p>Supported HTTP methods:</p>
	 * <ul>
	 *   <li>GET</li>
	 *   <li>POST</li>
	 *   <li>PUT</li>
	 *   <li>PATCH</li>
	 *   <li>DELETE</li>
	 *   <li>HEAD</li>
	 *   <li>OPTIONS</li>
	 * </ul>
	 *
	 * @param spec the Rest Assured request specification to configure
	 * @return the executed Rest Assured response
	 * @throws IllegalArgumentException when the HTTP method is unsupported
	 */
	public Response execute(RequestSpecification spec) {
	    RequestSpecification applied = apply(spec);

	    switch (method.toUpperCase()) {
	        case "GET":
	            return applied.get(url);
	        case "POST":
	            return applied.post(url);
	        case "PUT":
	            return applied.put(url);
	        case "PATCH":
	            return applied.patch(url);
	        case "DELETE":
	            return applied.delete(url);
	        case "HEAD":
	            return applied.head(url);
	        case "OPTIONS":
	            return applied.options(url);
	        default:
	            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
	    }
	}

	// -------------------------------------------------------------------------
	// Print
	// -------------------------------------------------------------------------

	/**
	 * Detailed multi-line output including description, auth, headers, and body.
	 */
	public void print() {
		log.debug(toString());
		if (!description.isEmpty())
			log.trace("Description: " + description);
		if (!auth.isNoAuth())
			log.trace("Auth: " + auth);
		if (!header.isEmpty())
			log.trace("Headers:\n" + header);
		if (!query.isEmpty())
			log.trace("Queries:\n" + query);
		log.trace("Body: " + body);
	}

	@Override
	public String toString() {
		return String.format("[%-6s] %-40s -> %s", method, name, url);
	}
}
