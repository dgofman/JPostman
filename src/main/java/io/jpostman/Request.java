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
	private final String folderName;
	private final String description;
	private final Url url;
	private final Header header;
	private final Body body;
	private final Auth auth;

	private Request(String name, String method, String folderName, String description, 
			Url url, Header header, Body body, Auth auth) {
		this.name = name;
		this.method = method;
		this.url = url;
		this.folderName = folderName;
		this.description = description;
		this.header = header;
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
		String desc = extractDescription(reqObj);
		Url url = Url.from(reqObj);
		Header header = Header.from(reqObj);
		Body body = Body.from(reqObj);
		Auth auth = Auth.from(reqObj);

		return new Request(name, method, folderName, desc,  url, header, body, auth);
	}

	/** Description may be a plain string or a {@code {content, type}} object. */
	private static String extractDescription(JsonObject reqObj) {
		if (!reqObj.has("description") || reqObj.get("description").isJsonNull()) {
			return "";
		}
		JsonElement el = reqObj.get("description");
		if (el.isJsonPrimitive()) {
			return el.getAsString();
		}
		if (el.isJsonObject()) {
			JsonObject obj = el.getAsJsonObject();
			JsonElement content = obj.get("content");
			return content != null && !content.isJsonNull() && content.isJsonPrimitive() ? content.getAsString() : "";
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

	public String getFolderName() {
		return folderName;
	}

	public String getDescription() {
		return description;
	}
	
	public String toUrl() {
		return url.toString();
	}

	public Url getUrl() {
		return url;
	}
	
	public Header getHeader() {
		return header;
	}

	public Auth getAuth() {
		return auth;
	}
	
	public Body getBody() {
		return body;
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

		private final String name;
		private final String method;
		private final String folderName;
		private final String description;
		private final ParamBuilder<Url> urlBuilder;
		private final ParamBuilder<Header> headerBuilder;
		private final ParamBuilder<Auth> authBuilder;
		private final ParamBuilder<Body> bodyBuilder;

		private RequestBuilder(Request req) {
			this.name = req.name;
			this.method = req.method;
			this.folderName = req.folderName;
			this.description = req.description;
			this.urlBuilder = req.url.builder();
			this.headerBuilder = req.header.builder();
			this.authBuilder = req.auth.builder();
			this.bodyBuilder = req.body.builder();
		}
		
		/**
		 * Returns the current request builder instance for URL customization.
		 *
		 * <pre>{@code
		 * request.builder().url().build();
		 * }</pre>
		 */
		public ParamStep url() {
			return new ParamStep(urlBuilder);
		}

		/**
		 * Overrides the request URL.
		 *
		 * <pre>{@code
		 * request.builder()
		 *     .url("http://{{base_url}}/{{version}}/users")
		 *     .build(env);
		 * }</pre>
		 *
		 * @param url request URL
		 * @return current request builder
		 */
		public RequestBuilder url(Consumer<ParamStep> customizer) {
			customizer.accept(url());
			return this;
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
			return new Request(name, method, folderName, description, urlBuilder.end(),
					headerBuilder.end(), bodyBuilder.end(), authBuilder.end());
		}

		/**
		 * Resolves all {@code {{variable}}} tokens in URL, headers, body, and auth
		 * params using the given environment's variable map.
		 */
		public Request build(Environment env) {
			Map<String, String> vars = env.getParams();
			urlBuilder.resolve(vars);
			headerBuilder.resolve(vars);
			authBuilder.resolve(vars);
			bodyBuilder.resolve(vars);
			return build();
		}

		/**
		 * Nested builder step used for headers, queries, body, and auth.
		 */
		public class ParamStep {
			private final ParamBuilder<?> delegate;

			ParamStep(ParamBuilder<?> delegate) {
				this.delegate = delegate;
			}

			/** Adds or replaces a parameter without requiring it to exist first. */
			public ParamStep add(String key, Object value) {
				delegate.add(key, value);
				return this;
			}

			/** Updates an existing parameter and throws when the key is missing. */
			public ParamStep set(String key, Object value) {
				delegate.set(key, value);
				return this;
			}

				/** Returns to the parent request builder. */
			public RequestBuilder end() {
				return RequestBuilder.this;
			}



			/**
			 * Resolves this request part with local key/value pairs, then returns to the
			 * parent request builder.
			 *
			 * @param key first key
			 * @param value first value
			 * @param rest remaining alternating key/value pairs
			 * @return parent request builder
			 */
			public RequestBuilder map(String key, Object value, Object... rest) {
				delegate.map(key, value, rest);
				return RequestBuilder.this;
			}

			/**
			 * Resolves this request part with local key/value pairs where String values are
			 * JSON-stringified, then returns to the parent request builder.
			 *
			 * @param key first key
			 * @param value first value
			 * @param rest remaining alternating key/value pairs
			 * @return parent request builder
			 */
			public RequestBuilder json(String key, Object value, Object... rest) {
				delegate.json(key, value, rest);
				return RequestBuilder.this;
			}

			/**
			 * Resolves this request part with local variables, then returns to the parent
			 * request builder. Values resolved here take priority over later
			 * request-level resolution from {@code build(env)}.
			 *
			 * @param vars local variables used only for this request part
			 * @return parent request builder
			 */
			public RequestBuilder end(Map<String, ?> vars) {
				delegate.end(vars);
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
	 * to populate headers, body, content type, and other
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
	    String url = this.url.toString();
	    
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
		log.trace(toDebugString());
	}

	/** Returns verbose diagnostic representation including details. */
	public String toDebugString() {
		StringBuilder sb = new StringBuilder();
	    sb.append(toString());
	    if (!description.isEmpty())
	    	sb.append("\nDescription: " + description);		
		if (!auth.isNoAuth())
			sb.append("\nAuth: " + auth);
		if (!header.isEmpty())
			sb.append("\nHeaders:\n" + header);
		sb.append("\nBody: " + body);
		return sb.toString();
	}

	@Override
	public String toString() {
		return String.format("[%-6s] %-40s -> %s", method, name, url);
	}
}
