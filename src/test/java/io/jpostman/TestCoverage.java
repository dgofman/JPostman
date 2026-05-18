package io.jpostman;

import static io.jpostman.Constants.ALL_PRODUCTS;
import static io.jpostman.Constants.COLLECTION_FILE;
import static io.jpostman.Constants.ENVIRONMENT_FILE;
import static io.jpostman.Constants.ENV_TOKEN_KEY;
import static io.jpostman.Constants.GET_AUTH_USER;
import static io.jpostman.Constants.GENERATE_IMAGE;
import static io.jpostman.Constants.LOGIN_GET_TOKEN;
import static io.jpostman.Constants.PRODUCT_FOLDER;
import static io.jpostman.Constants.IMAGE_FOLDER;
import static io.jpostman.Constants.TEST_USERNAME;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.gson.JsonParser;

import io.restassured.specification.RequestSpecification;

/**
 * Validates collection parsing, component parsing, builder behavior,
 * environment resolution, and Rest Assured request application.
 */
public class TestCoverage {

	private Collection col3;
	private Environment env;
	private Folder folder;

	@BeforeClass
	public void load() throws Exception {
		col3 = Collection.load(TestCoverage.class.getClassLoader().getResourceAsStream(COLLECTION_FILE));
		col3.print();

		col3.loadEnvironment(TestCoverage.class.getClassLoader().getResourceAsStream(ENVIRONMENT_FILE));
		env = col3.getEnvironment();
		env.print();

		folder = col3.getFolder(PRODUCT_FOLDER);
		folder.print();
	}
	
	// -------------------------------------------------------------------------
	// Test Environment
	// -------------------------------------------------------------------------

	@Test
	public void testLoadFileEnvironment() throws Exception {
		// environment file: loading by path should match classpath-loaded environment
		Environment env = Environment.load("src/main/resources/" + ENVIRONMENT_FILE);
		assertEquals(this.env.toString(), env.toString());

		// collection environment: load environment from file path
		col3.loadEnvironment("src/main/resources/" + ENVIRONMENT_FILE);
	}

	@Test
	public void testLoadResourceEnvironment() throws Exception {
		// environment resource: loading by stream should match initialized environment
		Environment env = Environment.load(TestCoverage.class.getClassLoader().getResourceAsStream(ENVIRONMENT_FILE));
		assertEquals(this.env.toString(), env.toString());
	}

	@Test
	public void testLoadStringEnvironment() throws Exception {
		Environment env;

		// environment: name present → parsed name
		env = Environment.load(JsonParser.parseString("{\"name\":\"TEST_NAME\"}").getAsJsonObject());
		assertEquals(env.getName(), "TEST_NAME");

		// environment: name absent → default name
		env = Environment.load(JsonParser.parseString("{}").getAsJsonObject());
		assertEquals(env.getName(), "Unknown Environment");
		env.print();
		
		// environment values: enabled=true → initially active; disabling removes from active params view
		env = Environment.load(JsonParser.parseString("{\"values\":[{\"key\":\"apikey\", \"value\":\"v\",\"enabled\":true}]}").getAsJsonObject());
		assertEquals(env.getParams().size(), 1);		
		assertEquals(env.getParam("apikey").isEnabled(), true);
		env.getParam("apikey").setEnabled(false);
		assertEquals(env.getParams().size(), 0);
		assertEquals(env.get("unknown"), null);
		assertEquals(env.get("apikey"), null);
		assertEquals(env.getParam("apikey").toString(), "value=v, enabled=false");
		
		
		// environment values: enabled=false → initially inactive; enabling restores to active params view
		env = Environment.load(JsonParser.parseString("{\"values\":[{\"key\":\"apikey\", \"value\":\"v\",\"enabled\":false}]}").getAsJsonObject());
		assertEquals(env.getParams().size(), 0);
		assertEquals(env.getParam("apikey").isEnabled(), false);
		env.getParam("apikey").setEnabled(true);
		assertEquals(env.getParams().size(), 1);

		// environment values: key absent and enabled=false → skipped
		env = Environment.load(JsonParser.parseString("{\"values\":[{\"value\":\"v\",\"enabled\":false}]}").getAsJsonObject());
		assertEquals(env.getParams().size(), 0);

		// environment values: key absent and enabled=true → skipped
		env = Environment.load(JsonParser.parseString("{\"values\":[{\"value\":\"v\",\"enabled\":true}]}").getAsJsonObject());
		assertEquals(env.getParams().size(), 0);

		// environment values: value absent → stored as ""
		env = Environment.load(JsonParser.parseString("{\"values\":[{\"key\":\"apikey\"}]}").getAsJsonObject());
		assertEquals(env.get("apikey"), "");
		env.print();
		
		// environment values: values exists but is not array → skipped
		env = Environment.load(JsonParser.parseString("{\"values\":\"not-array\"}").getAsJsonObject());
		assertEquals(env.getParams().size(), 0);

		// environment values: non-object value entry → skipped
		env = Environment.load(JsonParser.parseString("{\"values\":[\"not-object\"]}").getAsJsonObject());
		assertEquals(env.getParams().size(), 0);

		// environment values: key is null → skipped
		env = Environment.load(JsonParser.parseString("{\"values\":[{\"key\":null,\"value\":\"v\"}]}").getAsJsonObject());
		assertEquals(env.getParams().size(), 0);

		// environment values: enabled missing → treated as enabled
		env = Environment.load(JsonParser.parseString("{\"values\":[{\"key\":\"k\",\"value\":\"v\"}]}").getAsJsonObject());
		assertEquals(env.get("k"), "v");
		
		// environment values: value is null → stored as ""
		env = Environment.load(JsonParser.parseString("{\"values\":[{\"key\":\"k\",\"value\":null}]}").getAsJsonObject());
		assertEquals(env.get("k"), "");

		// environment: name is null → default name
		env = Environment.load(JsonParser.parseString("{\"name\":null}").getAsJsonObject());
		assertEquals(env.getName(), "Unknown Environment");
	}

	@Test(expectedExceptions = IllegalArgumentException.class,
			expectedExceptionsMessageRegExp = "Environment key not found: 'NEW_KEY'")
	public void testEnvSetThrowWhenKeyNotFound() {
		Environment source = new Environment("Test Env");

		// environment builder: add creates new key
		Environment target1 = source.builder().add("NEW_KEY", TEST_USERNAME).end();

		// environment builder: set existing key updates value
		Environment target2 = target1.builder().set("NEW_KEY", ENV_TOKEN_KEY).end();

		// environment builder: resolve adds values from map
		Environment target3 = target2.builder().resolve(Map.of("OLD_KEY", TEST_USERNAME)).end();

		assertEquals(target1.toString(), "  NEW_KEY                             = " + TEST_USERNAME + "\n");
		assertEquals(target2.toString(), "  NEW_KEY                             = " + ENV_TOKEN_KEY + "\n");
		assertEquals(target3.toString(), "  NEW_KEY                             = " + ENV_TOKEN_KEY + "\n"
				+ "  OLD_KEY                             = " + TEST_USERNAME + "\n");

		// environment builder: source remains unchanged
		assertEquals(source.getParams().size(), 0);

		// environment builder: set missing key → throws
		source.builder().set("NEW_KEY", TEST_USERNAME);
	}

	// -------------------------------------------------------------------------
	// Test Collection
	// -------------------------------------------------------------------------

	@Test
	public void testCollection() throws Exception {
		Collection col = Collection.load("src/main/resources/" + COLLECTION_FILE);

		// collection file: same source loaded by path → same parsed output
		assertEquals(this.col3.toString(), col.toString());
		assertEquals(this.col3.getRoot(), col.getRoot());
		assertEquals(this.col3.getName(), col.getName());
		assertEquals(this.col3.getFolders().toString(), col.getFolders().toString());
		assertEquals(this.col3.getRequests().toString(), col.getRequests().toString());

		// collection lookup: unknown folder/request → throws exception
		assertThrows(IllegalArgumentException.class, () -> col.getFolder("UNKNOWN"));
		assertThrows(IllegalArgumentException.class, () -> col.getRequest("UNKNOWN"));

		// request summary: parsed request should match formatted output
		assertEquals(col.getRequest(GET_AUTH_USER).toString(),
				"[GET   ] Get current auth user                    -> {{base_url}}/auth/me");

		// param helper: null input should not throw
		ParamBuilder.substituteVars(null, null);
		
		// substituteVars: null vars → original value returned
		assertEquals(ParamBuilder.substituteVars("{{username}}", null), "{{username}}");
	}

	@Test
	public void testCollectionFromString() throws Exception {
		Collection col;

		// collection: empty object → default unnamed collection
		col = Collection.load(JsonParser.parseString("{}").getAsJsonObject());
		col.print();

		// collection: info present but name absent → default name
		col = Collection.load(JsonParser.parseString("{\"info\": {}}").getAsJsonObject());
		assertEquals(col.getName(), "Unnamed Collection");

		// collection: item contains unnamed request/folder object without request → skipped as folder/request
		col = Collection.load(JsonParser.parseString("{\"item\": [{}]}").getAsJsonObject());
		assertEquals(col.getFolders().size(), 0);

		// collection: folder with empty item array → folder exists with no requests
		col = Collection.load(JsonParser.parseString(
				"{\"item\": [{\"name\": \"" + PRODUCT_FOLDER + "\", \"item\": []}]}")
				.getAsJsonObject());
		assertEquals(col.getFolder(PRODUCT_FOLDER).toString(), "");
		col.print();

		// collection: root request without name → request name defaults to Unnamed
		col = Collection.load(JsonParser.parseString("{\"item\": [{\"request\":{}}]}").getAsJsonObject());
		assertEquals(col.getRequests().size(), 1);
		col.print();
		
		// collection: info exists but is null → default name
		col = Collection.load(JsonParser.parseString("{\"info\":null}").getAsJsonObject());
		assertEquals(col.getName(), "Unnamed Collection");
		
		// collection: info.name is null → default name
		col = Collection.load(JsonParser.parseString("{\"info\":{\"name\":null}}").getAsJsonObject());
		assertEquals(col.getName(), "Unnamed Collection");
		
		// collection: item exists but is not array → no folders/requests
		col = Collection.load(JsonParser.parseString("{\"item\":\"invalid\"}").getAsJsonObject());
		assertEquals(col.getFolders().size(), 0);
		assertEquals(col.getRequests().size(), 0);
	}

	// -------------------------------------------------------------------------
	// Test Folder
	// -------------------------------------------------------------------------

	@Test
	public void testFolder() throws Exception {
		Collection col;
		Folder folder;
		Request req;

		// folder: empty item array → zero requests
		col = Collection.load(JsonParser.parseString(
				"{\"item\": [{\"name\": \"" + PRODUCT_FOLDER + "\", \"item\": []}]}")
				.getAsJsonObject());
		folder = col.getFolder(PRODUCT_FOLDER);
		assertEquals(folder.getRequests().size(), 0);
		folder.print();

		// folder: request without name → request name defaults to Unnamed
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":[{\"request\":{}}]}]}")
				.getAsJsonObject());

		Folder productFolder = col.getFolder(PRODUCT_FOLDER);
		assertEquals(productFolder.getRequests().size(), 1);
		assertThrows(IllegalArgumentException.class, () -> productFolder.getRequest("UNKNOWN"));

		// folder request: unnamed empty request → default GET and empty URL
		req = productFolder.getRequest("Unnamed");
		assertEquals(req.toString(), "[GET   ] Unnamed                                  -> ");
		folder.print();
		
		col = Collection.load(JsonParser.parseString(
		        "{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":[{\"name\":\"Users\",\"request\":{\"method\":\"GET\",\"url\":\"https://example.com/users\"}}]}]}")
		        .getAsJsonObject());
		folder = col.getFolder(PRODUCT_FOLDER);
		assertEquals(folder.toDebugString(), "=== Folder: Product (1 request) ===\n"
				+ "    [GET   ] Users                                    -> https://example.com/users\n");
	}
	

	// -------------------------------------------------------------------------
	// Test Auth
	// -------------------------------------------------------------------------

	@Test
	public void testAuthSet() throws Exception {
		Request req;
		Request template = col3.getRequest(GET_AUTH_USER);

		// auth builder: set existing token value
		Auth auth = template.getAuth().builder().set("token", ENV_TOKEN_KEY).end();
		assertEquals(auth.toString(), "[bearer] {token=" + ENV_TOKEN_KEY + "}");

		// request builder: auth add with env resolution
		Environment newEnv = env.builder().add("accessToken", ENV_TOKEN_KEY).end();
		req = template.builder().auth().add("TEST_USERNAME", TEST_USERNAME).end().build(newEnv);
		assertEquals(req.getAuth().toString(),
				"[bearer] {token=" + ENV_TOKEN_KEY + ", TEST_USERNAME=" + TEST_USERNAME + "}");

		// request builder: lambda auth add with env resolution
		req = template.builder().auth(c -> c.add("TEST_USERNAME", TEST_USERNAME)).build(newEnv);
		assertEquals(req.getAuth().toString(),
				"[bearer] {token=" + ENV_TOKEN_KEY + ", TEST_USERNAME=" + TEST_USERNAME + "}");

		// auth builder: original template remains unchanged
		assertEquals(template.getAuth().toString(), "[bearer] {token={{accessToken}}}");
	}

	@Test
	public void testAuthSetFromString() throws Exception {
		Auth auth;

		// auth absent → noauth
		auth = Auth.from(JsonParser.parseString("{}").getAsJsonObject());
		assertEquals(auth.getType(), "noauth");

		// auth is JSON null → noauth
		auth = Auth.from(JsonParser.parseString("{\"auth\":null}").getAsJsonObject());
		assertEquals(auth.getType(), "noauth");

		// auth present but type absent → noauth
		auth = Auth.from(JsonParser.parseString("{\"auth\":{}}").getAsJsonObject());
		assertEquals(auth.getType(), "noauth");

		// auth type present but matching array absent → no params
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"noauth\"}}").getAsJsonObject());
		assertEquals(auth.getType(), "noauth");
		assertEquals(auth.toString(), "[noauth]");
		assertEquals(auth.isNoAuth(), true);
		assertEquals(auth.getParams().size(), 0);
		auth.print();

		// v2.1 array: non-object element → skipped
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"bearer\",\"bearer\":[\"not-an-object\"]}}")
				.getAsJsonObject());
		assertEquals(auth.toString(), "[bearer] {}");

		// v2.1 array: key absent → skipped
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"bearer\",\"bearer\":[{\"value\":\"tok\"}]}}")
				.getAsJsonObject());
		assertEquals(auth.toString(), "[bearer] {}");

		// v2.1 array: value absent → stored as ""
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"bearer\",\"bearer\":[{\"key\":\"token\"}]}}")
				.getAsJsonObject());
		assertEquals(auth.get("token"), "");
		assertEquals(auth.getParams().entrySet().stream().anyMatch(h -> "token".equals(h.getKey())), true);
		auth.print();

		// v2.0 object: primitive value → stored directly; non-primitive value → stored as ""
		auth = Auth.from(JsonParser.parseString(
				"{\"auth\":{\"type\":\"basic\",\"basic\":{\"TEST_USERNAME\":\"" + TEST_USERNAME + "\",\"nested\":{\"a\":1}}}}")
				.getAsJsonObject());
		assertEquals(auth.get("TEST_USERNAME"), TEST_USERNAME);
		assertEquals(auth.get("nested"), "");
		
		// auth exists but is not object → noauth
		auth = Auth.from(JsonParser.parseString("{\"auth\":\"invalid\"}").getAsJsonObject());
		assertEquals(auth.getType(), "noauth");
		assertEquals(auth.getParams().size(), 0);
		
		// auth type is null → defaults to noauth
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":null}}").getAsJsonObject());
		assertEquals(auth.getType(), "noauth");
		
		// auth array entry: key is null and value is null → skipped
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"bearer\",\"bearer\":[{\"key\":null,\"value\":null}]}}").getAsJsonObject());
		assertEquals(auth.getParams().size(), 0);
	}

	@Test(expectedExceptions = IllegalArgumentException.class,
			expectedExceptionsMessageRegExp = "Auth key not found: 'NEW_KEY'")
	public void testAuthSetThrowWhenKeyNotFound() {
		Request template = col3.getRequest(GET_AUTH_USER);

		// auth builder: add creates new key on cloned auth
		Auth auth = template.getAuth().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(auth.toString(), "[bearer] {token={{accessToken}}, NEW_KEY=" + TEST_USERNAME + "}");

		// auth builder: set missing key on original auth → throws
		template.getAuth().builder().set("NEW_KEY", TEST_USERNAME);
	}

	// -------------------------------------------------------------------------
	// Test Header
	// -------------------------------------------------------------------------

	@Test
	public void testHeaderSet() {
		Request req;
		Request template = col3.getRequest(GET_AUTH_USER);

		// header builder: set existing Content-Type value
		Header header = template.getHeader().builder().set("Content-Type", ENV_TOKEN_KEY).end();
		assertEquals(header.toString(), "  Content-Type                        = " + ENV_TOKEN_KEY + "\n");

		// request builder: headers add with env resolution
		req = template.builder().headers().add("token", ENV_TOKEN_KEY).end().build(env);
		assertEquals(req.getHeader().toString(),
				"  Content-Type                        = application/json\n"
						+ "  token                               = " + ENV_TOKEN_KEY + "\n");

		// request builder: lambda headers add with env resolution
		req = template.builder().headers(c -> c.add("token", ENV_TOKEN_KEY)).build(env);
		assertEquals(req.getHeader().toString(),
				"  Content-Type                        = application/json\n"
						+ "  token                               = " + ENV_TOKEN_KEY + "\n");

		// header builder: original template remains unchanged
		assertEquals(template.getHeader().toString(), "  Content-Type                        = application/json\n");
		assertEquals(template.getHeader().getParams().size(), 1);
	}

	@Test
	public void testHeaderSetFromString() throws Exception {
		Header header;

		// header field absent → empty
		header = Header.from(JsonParser.parseString("{}").getAsJsonObject());
		assertEquals(header.isEmpty(), true);
		header.print();

		// header field is not JsonArray → empty
		header = Header.from(JsonParser.parseString("{\"header\":\"not-an-array\"}").getAsJsonObject());
		assertEquals(header.isEmpty(), true);

		// v2.1 array: non-object element → skipped
		header = Header.from(JsonParser.parseString("{\"header\":[\"not-an-object\"]}").getAsJsonObject());
		assertEquals(header.isEmpty(), true);

		// v2.1 array: key absent → skipped
		header = Header.from(JsonParser.parseString("{\"header\":[{\"value\":\"v\"}]}").getAsJsonObject());
		assertEquals(header.isEmpty(), true);

		// v2.1 array: value absent → stored as ""
		header = Header.from(JsonParser.parseString("{\"header\":[{\"key\":\"X-NoVal\"}]}").getAsJsonObject());
		assertEquals(header.get("X-NoVal"), "");
		header.print();

		// v2.1 array: key + value + disabled=false → stored
		header = Header.from(JsonParser.parseString(
				"{\"header\":[{\"key\":\"Accept\",\"value\":\"application/json\",\"disabled\":false}]}")
				.getAsJsonObject());
		assertEquals(header.get("Accept"), "application/json");
		
		// v2.1 array: key is null → skipped
		header = Header.from(JsonParser.parseString(
		        "{\"header\":[{\"key\":null,\"value\":\"v\"}]}")
		        .getAsJsonObject());
		assertEquals(header.isEmpty(), true);
		
		// v2.1 array: value is null → stored as empty string
		header = Header.from(JsonParser.parseString(
		        "{\"header\":[{\"key\":\"X-NullValue\",\"value\":null}]}")
		        .getAsJsonObject());
		assertEquals(header.get("X-NullValue"), "");
		
		// header object: disabled header is stored but skipped during request preparation
		Collection col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + GET_AUTH_USER + "\","
		                + "\"request\":{\"url\":{\"raw\":\"{{base_url}}\"},"
		                + "\"header\":[{\"key\":\"Accept\",\"value\":\"application/json\",\"disabled\":true}]}}]}")
		        		.getAsJsonObject());
		Request template = col.getRequest(GET_AUTH_USER);
		Request req = template.builder().build(env);
		assertEquals(req.getHeader().get("unknonwn"), null);
		assertEquals(req.getHeader().get("Accept"), null);
		assertEquals(req.getHeader().toString(), "");
		req.getHeader().getParam("Accept").setEnabled(true); // enabled
		assertEquals(req.getHeader().get("Accept"), "application/json");
		assertEquals(req.getHeader().toString(), "  Accept                              = application/json\n");
	}

	@Test(expectedExceptions = IllegalArgumentException.class,
			expectedExceptionsMessageRegExp = "Header key not found: 'NEW_KEY'")
	public void testHeaderSetThrowWhenKeyNotFound() {
		Request template = col3.getRequest(GET_AUTH_USER);

		// header builder: add creates new key on cloned header
		Header header = template.getHeader().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(header.toString(),
				"  Content-Type                        = application/json\n"
						+ "  NEW_KEY                             = " + TEST_USERNAME + "\n");

		// header builder: set missing key on original header → throws
		template.getHeader().builder().set("NEW_KEY", TEST_USERNAME);
	}

	// -------------------------------------------------------------------------
	// Test Url
	// -------------------------------------------------------------------------

	@Test
	public void testUrlSet() throws Exception {
		Collection col;
		Request template;
		Request req;
		
		this.col3.print();

		// url object: raw URL and query array with text variable → parsed URL
		template = this.col3.getFolder(IMAGE_FOLDER).getRequest(GENERATE_IMAGE);
		assertEquals(template.getUrl().isEmpty(), false);
		
		Url url = template.getUrl().builder().set("text", "Hello World").end();
		assertEquals(url.getOriginal(), "{{base_url}}/image/400x200/008080/ffffff?text=JPostman");
		assertEquals(url.toString(), "{{base_url}}/image/400x200/008080/ffffff?text=Hello World");
		assertEquals(url.toDebugString(), "=== Original URL: {{base_url}}/image/400x200/008080/ffffff?text=JPostman ===\n"
													      + "{{base_url}}/image/400x200/008080/ffffff?text=Hello World");
		req = template.builder().url().set("text", "Hello World").end().build(env);
		req.getUrl().print();
		
		url = new Url("http://example.com/products?id=25&&type=book&");
	    assertEquals(url.getRaw(), "http://example.com/products");
	    assertEquals(url.get("id"), "25");
	    assertEquals(url.get("type"), "book");
	    assertEquals(url.getParams().size(), 2);
	    assertEquals(url.toString(), "http://example.com/products?id=25&type=book");
	    
	    url = new Url("http://example.com/products?=invalid&id=25");
	    assertEquals(url.getRaw(), "http://example.com/products");
	    assertEquals(url.get("id"), "25");
	    assertEquals(url.getParams().size(), 1);
	    assertEquals(url.toString(),"http://example.com/products?id=25");
	    
	    url = new Url("http://example.com/products?id");
	    assertEquals(url.getRaw(), "http://example.com/products");
	    assertEquals(url.get("id"), "");
	    assertEquals(url.getParams().size(), 1);
	    assertEquals(url.toString(), "http://example.com/products?id=");

		Environment env = new Environment("Test Env").builder()
				.add("base_url", "https://dummyjson.com")
				.add("image_text", "Original")
				.end();

		// url builder: set existing query parameter value
		req = template.builder()
				.url(q -> q.set("text", "JPostman"))
				.build(env);

		// url object: raw URL has url and fragment → url update preserves fragment
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}#preview\",\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\"}]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		req = template.builder()
				.url(q -> q.set("text", "JPostman"))
				.build(env);

		assertEquals(template.getUrl().get("text"), "{{image_text}}");
		assertEquals(req.getUrl().get("text"), "JPostman");
		assertEquals(req.toUrl(), env.get("base_url") + "/image?text=JPostman#preview");
		
		// url object: disabled query parameter is skipped during URL rebuild while original URL and fragment are preserved
		col = Collection.load(JsonParser.parseString(
		        "{\"item\":[{\"name\":\"" + GET_AUTH_USER + "\","
		                + "\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}#preview\","
		                + "\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\",\"disabled\":true}]}}}]}").getAsJsonObject());
		template = col.getRequest(GET_AUTH_USER);
		req = template.builder().build(env);
		assertEquals(req.getUrl().get("unknonwn"), null);
		assertEquals(req.getUrl().get("text"), null);
		assertEquals(req.getUrl().toString(), "https://dummyjson.com/image#preview");
		assertEquals(req.getUrl().getOriginal(), "{{base_url}}/image?text={{image_text}}#preview");
		assertEquals(req.toUrl(), "https://dummyjson.com/image#preview");
		req.getUrl().getParam("text").setEnabled(true); // enabled
		assertEquals(req.getUrl().get("text"), "{{image_text}}");
		assertEquals(req.getUrl().toString(), "https://dummyjson.com/image?text={{image_text}}#preview");
		assertEquals(req.getUrl().getOriginal(), "{{base_url}}/image?text={{image_text}}#preview");
		assertEquals(req.toUrl(), "https://dummyjson.com/image?text={{image_text}}#preview");

		// raw URL has no query string but url.query[] exists
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image#preview\",\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\"}]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		req = template.builder()
				.url(q -> q.set("text", "JPostman"))
				.build(env);

		assertEquals(template.getUrl().get("text"), "{{image_text}}");
		assertEquals(req.getUrl().get("text"), "JPostman");
		assertEquals(req.toUrl(), env.get("base_url") + "/image?text=JPostman#preview");
		
		url = new Url(null);
		assertEquals(url.getRaw(), "");
		assertEquals(url.getOriginal(), "");
	}

	@Test
	public void testUrlSetFromString() throws Exception {
		Collection col;
		Request template;

		// url object: raw URL without query parameters → non-empty URL with empty params
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image\"}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getUrl().isEmpty(), false);

		// url object: query array key/value → parsed
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\"}]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getUrl().isEmpty(), false);

		// url object: query field is null → raw query string still parsed
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":null}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getUrl().isEmpty(), false);

		// v2.1 url query array: non-object element skipped; raw query string still parsed
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[1]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getUrl().isEmpty(), false);

		// v2.1 url query array: missing key skipped; raw query string still parsed
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"disabled\": true}]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getUrl().isEmpty(), false);

		// v2.1 url query array: null key/value skipped; raw query string still parsed
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"disabled\": false, \"key\": null, \"value\": null}]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getUrl().isEmpty(), false);

		// v2.1 url query array: disabled key/value skipped; raw query string still parsed
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"disabled\": true, \"key\": \"user\", \"value\": null}]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getUrl().isEmpty(), false);
	}


	@Test
	public void testUrlParsingFromRawString() {
		// raw URL: base URL, query parameters, and fragment are separated and rebuilt
		Url url = new Url("http://{{base_url}}/users?id={{id}}&type=book#preview");
		assertEquals(url.getRaw(), "http://{{base_url}}/users#preview");
		assertEquals(url.get("id"), "{{id}}");
		assertEquals(url.get("type"), "book");
		assertEquals(url.isEmpty(), false);
		assertEquals(url.toString(), "http://{{base_url}}/users?id={{id}}&type=book#preview");
	}

	@Test(expectedExceptions = IllegalArgumentException.class,
			expectedExceptionsMessageRegExp = "Url key not found: 'NEW_KEY'")
	public void testUrlSetThrowWhenKeyNotFound() throws Exception {
		Collection col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image\"}}}"
						+ "]}]}")
				.getAsJsonObject());
		Request template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);

		// url builder: add creates new key on cloned url
		Url url = template.getUrl().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(url.toString(), "{{base_url}}/image?NEW_KEY=" + TEST_USERNAME);

		// url builder: set missing key on original url → throws
		template.getUrl().builder().set("NEW_KEY", TEST_USERNAME);
	}

	// -------------------------------------------------------------------------
	// Test Body
	// -------------------------------------------------------------------------

	@Test
	public void testBodySet() throws Exception {
		Request req;
		Request template = col3.getRequest(LOGIN_GET_TOKEN);
		template.print();

		// body builder: set existing username value
		Body body = template.getBody().builder().set("username", TEST_USERNAME).end();
		assertEquals(body.toString(),
				"[raw/json] {\"username\":\"" + TEST_USERNAME + "\",\"password\":\"{{password}}\"}");

		// request builder: body set with env resolution
		req = template.builder().body().set("username", TEST_USERNAME).end().build(env);
		assertEquals(req.getBody().toString(),
				"[raw/json] {\"username\":\"" + TEST_USERNAME + "\",\"password\":\"emilyspass\"}");

		// request builder: lambda body set with env resolution
		req = template.builder().body(c -> c.set("username", TEST_USERNAME)).build(env);
		assertEquals(req.getBody().toString(),
				"[raw/json] {\"username\":\"" + TEST_USERNAME + "\",\"password\":\"emilyspass\"}");

		// body builder: original template remains unchanged
		assertEquals(template.getBody().toString(),
				"[raw/json] {\n\t\"username\": \"{{username}}\",\n\t\"password\": \"{{password}}\"\n}");
	}

	@Test
	public void testBodyNewSet() throws Exception {
		Request template = col3.getRequest(GET_AUTH_USER);

		// body builder: add supports number, boolean, and list values
		Body body = template.getBody().builder()
				.add("count", 25)
				.add("active", true)
				.add("tags", List.of(1, true, "$"))
				.end();

		assertEquals(body.toString(), "[none] {\"count\":25,\"active\":true,\"tags\":[1,true,\"$\"]}");
	}

	@Test
	public void testBodySetFromString() throws Exception {
		Body body;
		Body resolved;

		// body is JSON null → mode "none"
		body = Body.from(JsonParser.parseString("{\"body\":null}").getAsJsonObject());
		assertEquals(body.toString(), "[none]");

		// body mode is null → mode "none"
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\": null}}").getAsJsonObject());
		assertEquals(body.toString(), "[none]");
		assertEquals(body.getMode(), "none");
		assertEquals(body.isEmpty(), true);
		body.print();

		// raw mode: raw key absent → raw=""
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\"}}").getAsJsonObject());
		assertEquals(body.toString(), "[raw] ");
		assertEquals(body.getMode(), "raw");
		assertEquals(body.getRaw(), "");
		assertEquals(body.getParsed(), null);
		assertEquals(body.isEmpty(), true);
		
		// getString: array value → default returned
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":[1,2,3]}}")
		        .getAsJsonObject());
		assertEquals(body.getRaw(), "");
		
		// raw mode: options is null
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"hello\",\"options\":null}}")
		        .getAsJsonObject());
		assertEquals(body.getLanguage(), "");

		// raw mode: options absent/empty → language=""
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"hello\",\"options\":{}}}")
				.getAsJsonObject());
		assertEquals(body.getLanguage(), "");
		assertEquals(body.toString(), "[raw] hello");
		
		// raw mode: options.raw exists but is not object → language defaults to ""
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"hello\",\"options\":{\"raw\":\"json\"}}}")
		        .getAsJsonObject());
		assertEquals(body.getLanguage(), "");
		assertEquals(body.toString(), "[raw] hello");

		// raw mode: options.raw exists but language absent → language=""
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"hello\",\"options\":{\"raw\":{}}}}")
				.getAsJsonObject());
		assertEquals(body.getLanguage(), "");

		// graphql mode: graphql field absent → raw graphql payload is empty; builder creates an empty object
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"graphql\"}}").getAsJsonObject());
		assertEquals(body.builder().end().getRaw(), "{}");
		assertEquals(body.toString(), "[graphql]");
		
		// graphql mode: graphql field is null → raw graphql payload is empty; builder creates an empty object
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"graphql\",\"graphql\":null}}").getAsJsonObject());
		assertEquals(body.builder().end().getRaw(), "{}");
		assertEquals(body.toString(), "[graphql]");

		// graphql mode: graphql query object is stored as raw text; builder starts from an empty object
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"graphql\",\"graphql\":{\"query\":\"{ hero }\"}}}")
				.getAsJsonObject());
		assertEquals(body.builder().end().getRaw(), "{}");
		assertEquals(body.toString(), "[graphql]");

		// raw/json: valid JSON array → parsed as JsonArray and builder preserves array body
		body = Body.from(JsonParser.parseString(
			    "{\"body\":{\"mode\":\"raw\",\"raw\":\"[\\\"{{base_url}}\\\",\\\"{{username}}\\\",\\\"{{password}}\\\"]\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
			    .getAsJsonObject());
		assertNotNull(body.getParsed());
		assertEquals(body.getParsed().isJsonArray(), true);
		assertEquals(body.builder().end().getRaw(), "[\"{{base_url}}\",\"{{username}}\",\"{{password}}\"]");
		assertEquals(ParamBuilder.substituteVars(body.builder().end().getRaw(), env.getParams()), "[\"https://dummyjson.com\",\"emilys\",\"emilyspass\"]");

		// raw/json: invalid JSON raw string → parse failure ignored, mode remains raw
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"[{null}]\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		assertEquals(body.getMode(), "raw");
		
		// formdata mode: explicit null payload → mode preserved, but body is empty
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"formdata\",\"formdata\": null}}")
				.getAsJsonObject());
		assertEquals(body.getMode(), "formdata");

		// formdata mode: invalid JSON-looking string → preserved as a string primitive
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"formdata\",\"formdata\":\"[{null}]\"}}")
				.getAsJsonObject());
		assertEquals(body.getMode(), "formdata");

		// formdata mode: JSON array encoded as a string → parsed and serialized as array
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"formdata\",\"formdata\":\"[{\\\"key\\\":\\\"file\\\"}]\"}}")
				.getAsJsonObject());
		assertEquals(body.getMode(), "formdata");
		assertEquals(body.getRaw(), "[{\"key\":\"file\"}]");
		assertEquals(body.toString(), "[formdata] [{\"key\":\"file\"}]");

		// urlencoded mode: missing payload → mode preserved with empty body
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"urlencoded\"}}").getAsJsonObject());
		assertEquals(body.getMode(), "urlencoded");
		
		// urlencoded mode: explicit null payload → mode preserved with empty body
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"urlencoded\", \"urlencoded\": null}}").getAsJsonObject());
		assertEquals(body.getMode(), "urlencoded");
		
		// urlencoded mode: Postman-style array payload → serialized as JSON array
		body = Body.from(JsonParser.parseString(
		    "{\"body\":{\"mode\":\"urlencoded\",\"urlencoded\":[{\"key\":\"username\",\"value\":\"{{username}}\"}]}}")
		    .getAsJsonObject());
		assertEquals(body.getMode(), "urlencoded");
		assertEquals(body.getRaw(), "[{\"key\":\"username\",\"value\":\"{{username}}\"}]");
		assertEquals(ParamBuilder.substituteVars(body.getRaw(), env.getParams()), "[{\"key\":\"username\",\"value\":\"emilys\"}]");
		
		// formdata mode: object payload → serialized as JSON object
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"formdata\",\"formdata\":{\"username\":\"{{username}}\"}}}")
			    .getAsJsonObject());
		assertEquals(body.getMode(), "formdata");
		assertEquals(body.getRaw(), "{\"username\":\"{{username}}\"}");
		assertEquals(ParamBuilder.substituteVars(body.getRaw(), env.getParams()), "{\"username\":\"emilys\"}");
		

		// formdata mode: primitive boolean payload → preserved as primitive fallback
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"formdata\",\"formdata\":true}}")
		        .getAsJsonObject());
		assertEquals(body.getParsed().isJsonPrimitive(), true);
		assertEquals(body.getRaw(), "true");
		
		// formdata mode: blank string payload → treated as empty body so GET requests do not send ""
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"formdata\",\"formdata\":\"\"}}")
		        .getAsJsonObject());
		assertEquals(body.getRaw(), "");
		assertEquals(body.isEmpty(), true);


		// raw/json resolve: number value → not substituted
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"count\\\":42}\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		resolved = body.builder().resolve(Map.of("count", "99")).end();
		assertEquals(resolved.getRaw(), "{\"count\":42}");

		// raw/json resolve: non-string nested value → not substituted
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"meta\\\":{\\\"v\\\":1}}\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		resolved = body.builder().resolve(Map.of("v", "replaced")).end();
		assertEquals(resolved.getRaw(), "{\"meta\":{\"v\":1}}");
		
		// raw/json resolve: array string values → recursively substituted
		body = Body.from(JsonParser.parseString(
		    "{\"body\":{\"mode\":\"raw\",\"raw\":\"[\\\"{{base_url}}\\\",\\\"{{username}}\\\"]\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
		    .getAsJsonObject());

		resolved = body.builder().resolve(env.getParams()).end();
		assertEquals(resolved.getRaw(), "[\"https://dummyjson.com\",\"emilys\"]");
		
		// body field is primitive instead of object → defaults to NONE body
		body = Body.from(JsonParser.parseString("{\"body\":\"invalid\"}").getAsJsonObject());
		assertEquals(body.getMode(), "none");
		assertEquals(body.isEmpty(), true);
		
		// raw/json resolve: null array item → preserved
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"[null,\\\"{{username}}\\\"]\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
		    .getAsJsonObject());
		resolved = body.builder().resolve(env.getParams()).end();
		assertEquals(resolved.getRaw(), "[null,\"emilys\"]");
		
		// null request object → NONE body
		body = Body.from(null);
		assertEquals(body.getMode(), "none");
		assertEquals(body.isEmpty(), true);
	}

	@Test
	public void testBodyHandlebarsTemplatesAndSimpleMutations() {
		// raw/json body can contain a template fragment that is not valid JSON by itself.
		// Handlebars resolution must preserve the original raw body instead of converting it to {}.
		Body body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"<id>{{USER_ID}}</id>\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		Body resolved = body.builder().resolve(Map.of("USER_ID", "42")).end();
		assertEquals(resolved.getRaw(), "<id>42</id>");

		// Missing variables use normal Handlebars behavior: unresolved values become empty strings.
		assertEquals(ParamBuilder.substituteVars("<id>{{UNKNOWN_ID}}</id>", Map.of("USER_ID", "42")),
				"<id></id>");
		assertEquals(ParamBuilder.substituteVars("<id>{{ USER_ID }}</id><missing>{{UNKNOWN_ID}}</missing>",
				Map.of("USER_ID", "42")), "<id>42</id><missing></missing>");
		assertEquals(ParamBuilder.substituteVars("<id>{{UNKNOWN_ID}</id>", Map.of("USER_ID", "42")),
				"<id>{{UNKNOWN_ID}</id>");

		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"id\\\":\\\"{{UNKNOWN_ID}}\\\"}\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		assertEquals(body.builder().resolve(Map.of("USER_ID", "42")).end().getRaw(),
				"{\"id\":\"\"}");

		// Body mutation is intentionally simple: top-level JSON object keys only.
		// Template replacement is delegated to Handlebars through ParamBuilder.
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"id\\\":\\\"{{USER_ID}}\\\",\\\"role\\\":\\\"{{ROLE}}\\\"}\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		resolved = body.builder()
				.set("id", "007")
				.add("traceId", "abc-123")
				.resolve(Map.of("ROLE", "admin"))
				.end();
		assertEquals(resolved.getRaw(),
				"{\"id\":\"007\",\"role\":\"admin\",\"traceId\":\"abc-123\"}");
	}



	@Test
	public void testPartLevelEndParamsResolveBeforeBuildEnv() throws Exception {
		Collection col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"Partial Resolve\",\"request\":{\"method\":\"POST\"," 
						+ "\"url\":{\"raw\":\"{{base_url}}/search?text={{text}}\",\"query\":[{\"key\":\"text\",\"value\":\"{{text}}\"}]},"
						+ "\"header\":[{\"key\":\"X-Trace\",\"value\":\"{{trace_id}}\"}],"
						+ "\"auth\":{\"type\":\"bearer\",\"bearer\":[{\"key\":\"token\",\"value\":\"{{token}}\"}]},"
						+ "\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"username\\\":\\\"{{username}}\\\",\\\"password\\\":\\\"{{password}}\\\"}\","
						+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}}]}" )
				.getAsJsonObject());

		Environment env = new Environment("Env").builder()
				.add("base_url", "https://api.example.com")
				.add("text", "env-text")
				.add("trace_id", "env-trace")
				.add("token", "env-token")
				.add("username", "env-user")
				.add("password", "env-pass")
				.end(null);

		Request template = col.getRequest("Partial Resolve");
		Request req = template.builder()
				.url()
					.set("text", "{{TEXT}}")
				.end(Map.of("TEXT", "local-text"))
				.auth()
					.set("token", "{{token}}")
				.end(Map.of("token", "local-token"))
				.headers()
					.set("X-Trace", "{{trace_id}}")
				.end(Map.of("trace_id", "local-trace"))
				.body()
					.set("username", "{{username}}")
				.end(Map.of("username", "local-user"))
				.build(env);
		
		req.print();

		assertEquals(req.toUrl(), "https://api.example.com/search?text=local-text");
		assertEquals(req.getHeader().get("X-Trace"), "local-trace");
		assertEquals(req.getAuth().get("token"), "local-token");
		assertEquals(req.getBody().getRaw(), "{\"username\":\"local-user\",\"password\":\"env-pass\"}");
	}

	@Test
	public void testParamBuilderEndWithParamsAndNullResolve() {
		Body body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"username\\\":\\\"{{username}}\\\",\\\"password\\\":\\\"{{password}}\\\"}\","
						+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}").getAsJsonObject());

		Body locallyResolved = body.builder()
				.set("username", "{{username}}")
				.end(Map.of("username", "local-user"));

		assertEquals(locallyResolved.getRaw(), "{\"username\":\"local-user\",\"password\":\"{{password}}\"}");

		Body unchanged = body.builder().resolve(null).end();
		assertEquals(unchanged.getRaw(), "{\"username\":\"{{username}}\",\"password\":\"{{password}}\"}");
	}
	
	@Test
	public void testBodyTryParseJson() {
	    Body body = Body.from(JsonParser.parseString(
	            "{\"body\":{\"mode\":\"raw\",\"raw\":\"   \",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
	            .getAsJsonObject());

	    Body resolved = body.builder().resolve(Map.of("USER_ID", "42")).end();
	    assertEquals(resolved.getRaw(), "   ");
	    assertEquals(resolved.getParsed(), null);

	    body = new Body("raw", null, "json", null);
	    resolved = body.builder().resolve(Map.of("USER_ID", "42")).end();
	    assertEquals(resolved.getRaw(), null);
	    assertEquals(resolved.getParsed(), null);
	    
	    body = Body.from(JsonParser.parseString(
	            "{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"id\\\":\\\"{{USER_ID}}\\\"}\",\"options\":{\"raw\":{\"language\":\"text\"}}}}")
	            .getAsJsonObject());
	    resolved = body.builder().resolve(Map.of("USER_ID", "42")).end();
	    assertEquals(resolved.getRaw(), "{\"id\":\"42\"}");
	    assertNotNull(resolved.getParsed());
	    assertEquals(resolved.getParsed().isJsonObject(), true);
	}

	@Test(expectedExceptions = IllegalArgumentException.class,
			expectedExceptionsMessageRegExp = "Body key not found: 'NEW_KEY'")
	public void testBodySetThrowWhenKeyNotFound() {
		Request template = col3.getRequest(LOGIN_GET_TOKEN);

		// body builder: add creates new key on cloned JSON object body
		Body body = template.getBody().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(body.toString(),
				"[raw/json] {\"username\":\"{{username}}\",\"password\":\"{{password}}\",\"NEW_KEY\":\""
						+ TEST_USERNAME + "\"}");

		// body builder: set missing key on original body → throws
		template.getBody().builder().set("NEW_KEY", TEST_USERNAME);
	}
	
	@Test(expectedExceptions = IllegalArgumentException.class,
			expectedExceptionsMessageRegExp = "Body builder add/set requires a JSON object body")
	public void testBodyAddRequiresObjectBody() {
		Body body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"[1,2,3]\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				 .getAsJsonObject());
		body.builder().add("x", 1).end();
	}
	
	// -------------------------------------------------------------------------
	// Test Request
	// -------------------------------------------------------------------------

	@Test
	public void testRequest() throws Exception {
		Request request = folder.getRequest(ALL_PRODUCTS);

		// request: real collection request should preserve name
		assertEquals(request.getName(), ALL_PRODUCTS);
	}

	@Test
	public void testRequestFromString() throws Exception {
		Collection col;
		Request req;

		// request: url string → parsed directly
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"request\":{\"url\": \"http://github.com\"}"
						+ "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.toUrl(), "http://github.com");
		assertEquals(req.getMethod(), "GET");
		assertEquals(req.getFolderName(), PRODUCT_FOLDER);
		assertEquals(req.getUrl().isEmpty(), false);
		req.print();

		// request: description string → parsed directly
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"request\":{\"description\": \"TODO\"}"
						+ "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getDescription(), "TODO");
		req.print();

		// request: description null → empty string
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"request\":{\"description\": null}"
						+ "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getDescription(), "");

		// request: description object without content → empty string
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"request\":{\"description\": {}}"
						+ "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getDescription(), "");

		// request: description object with content → content string
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"request\":{\"description\": {\"content\": \"TODO\"}}"
						+ "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getDescription(), "TODO");

		// request: url null → empty string
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"request\":{\"url\": null}"
						+ "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.toUrl(), "");

		// request: url object without raw → empty string
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"request\":{\"url\": {}}"
						+ "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.toUrl(), "");

		// request auth: v2.1 basic array value absent → stored as ""
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"request\":{\"auth\":{\"type\":\"basic\",\"basic\":[{\"key\":\"" + TEST_USERNAME + "\"}]}}"
						+ "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getAuth().toString(), "[basic] {" + TEST_USERNAME + "=}");
		req.print();

		// request header: value absent → stored as ""
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"request\":{\"header\":[{\"key\":\"" + TEST_USERNAME + "\"}]}"
						+ "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getHeader().toString(), "  " + TEST_USERNAME + "                           = \n");
		req.print();

		// request body: empty body object → none body
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"request\":{\"body\":{}}"
						+ "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getBody().toString(), "[none]");
		req.print();

		// request apply: empty request should apply without throwing
		req.apply(mock(RequestSpecification.class));

		// request apply: header + raw/json body → Rest Assured spec receives parsable request
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"request\":{\"header\":[{\"key\":\"X-Test\",\"value\":\"v\"}],"
						+ "\"body\":{\"mode\":\"raw\",\"raw\":\"[1,2,3]\","
						+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}}]}")
				.getAsJsonObject());
		assertEquals(col.getRequests().size(), 1);
		req = col.getRequest("Unnamed");
		assertEquals(req.getHeader().getParams().size(), 1);
		req.apply(mock(RequestSpecification.class));
		
		// request url: url field is array → unsupported URL shape returns ""
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"request\":{\"url\":[\"http://github.com\"]}}]}")
		        .getAsJsonObject());
		req = col.getRequest("Unnamed");
		assertEquals(req.toUrl(), "");
		
		// request url object: raw is null → empty URL
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"request\":{\"url\":{\"raw\":null}}}]}")
		        .getAsJsonObject());
		req = col.getRequest("Unnamed");
		assertEquals(req.toUrl(), "");
		
		// request url object: raw is object → empty URL
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"request\":{\"url\":{\"raw\":{\"value\":\"http://github.com\"}}}}]}")
		        .getAsJsonObject());
		req = col.getRequest("Unnamed");
		assertEquals(req.toUrl(), "");
		
		// request: description array → empty string
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"request\":{\"description\":[\"TODO\"]}}]}")
		        .getAsJsonObject());
		req = col.getRequest("Unnamed");
		assertEquals(req.getDescription(), "");
		
		// request: description object with content null → empty string
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"request\":{\"description\":{\"content\":null}}}]}")
		        .getAsJsonObject());
		req = col.getRequest("Unnamed");
		assertEquals(req.getDescription(), "");
		
		// request: description object with content object → empty string
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"request\":{\"description\":{\"content\":{\"text\":\"TODO\"}}}}]}")
		        .getAsJsonObject());
		req = col.getRequest("Unnamed");
		assertEquals(req.getDescription(), "");
	}



	@Test(expectedExceptions = IllegalArgumentException.class,
			expectedExceptionsMessageRegExp = "Body key not found: 'NEW_KEY'")
	public void testRequestThrowWhenKeyNotFound() throws Exception {
		Collection col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"request\":{\"body\":{}}"
						+ "}]}]}")
				.getAsJsonObject());

		Request template = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");

		// request builder: body add creates new key on cloned request
		Request req = template.builder().body().add("NEW_KEY", TEST_USERNAME).end().build();
		assertEquals(req.getBody().toString(), "[none] {\"NEW_KEY\":\"" + TEST_USERNAME + "\"}");

		// request builder: body set missing key → throws
		template.builder().body().set("NEW_KEY", TEST_USERNAME);
	}
	
	private Request request(String method) throws IOException {
		String url = env.get("base_url");
	    return Collection.load(JsonParser.parseString(
	            "{\"item\":[{\"request\":{\"method\":\"" + method + "\",\"url\":\"" + url + "\"}}]}"
	    ).getAsJsonObject()).getRequest("Unnamed");
	}
	
	@Test
	public void testExecuteAllHttpMethods() throws IOException {
		RequestSpecification spec = mock(RequestSpecification.class);
	    request("GET").execute(spec);
	    request("POST").execute(spec);
	    request("PUT").execute(spec);
	    request("PATCH").execute(spec);
	    request("DELETE").execute(spec);
	    request("HEAD").execute(spec);
	    request("OPTIONS").execute(spec);
	    assertThrows(IllegalArgumentException.class, () -> request("TODO").execute(spec));
	}
}