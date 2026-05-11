package io.jpostman;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.List;
import java.util.Map;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.gson.JsonParser;

import io.restassured.RestAssured;

import static io.jpostman.Constants.*;

/**
 * Validates collection parsing, component parsing, builder behavior,
 * environment resolution, and Rest Assured request application.
 */
public class TestCoverage {

	private Collection col;
	private Environment env;
	private Folder folder;

	@BeforeClass
	public void load() throws Exception {
		col = Collection.load(TestCoverage.class.getClassLoader().getResourceAsStream(COLLECTION_FILE));
		col.print();

		col.loadEnvironment(TestCoverage.class.getClassLoader().getResourceAsStream(ENVIRONMENT_FILE));
		env = col.getEnvironment();
		env.print();

		folder = col.getFolder(PRODUCT_FOLDER);
		folder.print();
	}

	// -------------------------------------------------------------------------
	// Test Collection
	// -------------------------------------------------------------------------

	@Test
	public void testCollection() throws Exception {
		Collection col = Collection.load("src/main/resources/" + COLLECTION_FILE);

		// collection file: same source loaded by path → same parsed output
		assertEquals(this.col.toString(), col.toString());
		assertEquals(this.col.getRoot(), col.getRoot());
		assertEquals(this.col.getName(), col.getName());
		assertEquals(this.col.getFolders().toString(), col.getFolders().toString());
		assertEquals(this.col.getRequests().toString(), col.getRequests().toString());

		// collection lookup: unknown folder/request → null
		assertEquals(col.getFolder("UNKNOWN"), null);
		assertEquals(col.getRequest("UNKNOWN"), null);

		// request summary: parsed request should match formatted output
		assertEquals(col.getRequest(GET_AUTH_USER).toString(),
				"[GET   ] Get current auth user                    -> {{base_url}}/auth/me");

		// param helper: null input should not throw
		ParamBuilder.substituteVars(null, null);
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
		folder = col.getFolder(PRODUCT_FOLDER);
		assertEquals(folder.getRequests().size(), 1);

		// folder lookup: unknown request → null
		assertEquals(folder.getRequest("UNKOWN"), null);

		// folder request: unnamed empty request → default GET and empty URL
		req = folder.getRequest("Unnamed");
		assertEquals(req.toString(), "[GET   ] Unnamed                                  -> ");
		folder.print();
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
		assertEquals(req.getUrl(), "http://github.com");
		assertEquals(req.getMethod(), "GET");
		assertEquals(req.getFolderName(), PRODUCT_FOLDER);
		assertEquals(req.getQueries().isEmpty(), true);
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
		assertEquals(req.getUrl(), "");

		// request: url object without raw → empty string
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"request\":{\"url\": {}}"
						+ "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getUrl(), "");

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
		req.apply(RestAssured.given());

		// request apply: header + raw/json body → Rest Assured spec receives parsable request
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"request\":{\"header\":[{\"key\":\"X-Test\",\"value\":\"v\"}],"
						+ "\"body\":{\"mode\":\"raw\",\"raw\":\"[1,2,3]\","
						+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}}]}")
				.getAsJsonObject());
		assertEquals(col.getRequests().size(), 1);
		req = col.getRequest("Unnamed");
		assertEquals(req.getHeader().getParams().size(), 1);
		req.apply(RestAssured.given());
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

	// -------------------------------------------------------------------------
	// Test Environment
	// -------------------------------------------------------------------------

	@Test
	public void testLoadFileEnvironment() throws Exception {
		// environment file: loading by path should match classpath-loaded environment
		Environment env = Environment.load("src/main/resources/" + ENVIRONMENT_FILE);
		assertEquals(this.env.toString(), env.toString());

		// collection environment: load environment from file path
		col.loadEnvironment("src/main/resources/" + ENVIRONMENT_FILE);
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
	// Test Auth
	// -------------------------------------------------------------------------

	@Test
	public void testAuthSet() throws Exception {
		Request req;
		Request template = col.getRequest(GET_AUTH_USER);

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
	}

	@Test(expectedExceptions = IllegalArgumentException.class,
			expectedExceptionsMessageRegExp = "Auth key not found: 'NEW_KEY'")
	public void testAuthSetThrowWhenKeyNotFound() {
		Request template = col.getRequest(GET_AUTH_USER);

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
		Request template = col.getRequest(GET_AUTH_USER);

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
	}

	@Test(expectedExceptions = IllegalArgumentException.class,
			expectedExceptionsMessageRegExp = "Header key not found: 'NEW_KEY'")
	public void testHeaderSetThrowWhenKeyNotFound() {
		Request template = col.getRequest(GET_AUTH_USER);

		// header builder: add creates new key on cloned header
		Header header = template.getHeader().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(header.toString(),
				"  Content-Type                        = application/json\n"
						+ "  NEW_KEY                             = " + TEST_USERNAME + "\n");

		// header builder: set missing key on original header → throws
		template.getHeader().builder().set("NEW_KEY", TEST_USERNAME);
	}

	// -------------------------------------------------------------------------
	// Test Query
	// -------------------------------------------------------------------------

	@Test
	public void testQuerySet() throws Exception {
		Collection col;
		Request template;
		Request req;

		// url object: query array with text variable → parsed query
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\"}]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), false);
		template.print();

		Environment env = new Environment("Test Env").builder()
				.add("base_url", "https://dummyjson.com")
				.add("image_text", "Original")
				.end();

		// query builder: set existing query value
		req = template.builder()
				.queries(q -> q.set("text", "JPostman"))
				.build(env);

		// url object: raw URL has query and fragment → query update preserves fragment
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}#preview\",\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\"}]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		req = template.builder()
				.queries(q -> q.set("text", "JPostman"))
				.build(env);

		assertEquals(template.getQuery().get("text"), "{{image_text}}");
		assertEquals(req.getQuery().get("text"), "JPostman");
		assertEquals(req.getUrl(), env.get("base_url") + "/image?text=JPostman#preview");

		// url object: raw URL has fragment but no query → URL remains fragment-only
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image#preview\",\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\"}]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		req = template.builder()
				.queries(q -> q.set("text", "JPostman"))
				.build(env);

		assertEquals(template.getQuery().get("text"), "{{image_text}}");
		assertEquals(req.getQuery().get("text"), "JPostman");
		assertEquals(req.getUrl(), env.get("base_url") + "/image#preview");
	}

	@Test
	public void testQuerySetFromString() throws Exception {
		Collection col;
		Request template;

		// url object: query field absent → empty
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image\"}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), true);
		template.getQueries().print();

		// url object: query array key/value → parsed
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\"}]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), false);
		template.getQueries().print();

		// url object: query is null → empty
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":null}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), true);

		// v2.1 query array: non-object element → skipped
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[1]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), true);

		// v2.1 query array: disabled=true and key absent → skipped
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"disabled\": true}]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), true);

		// v2.1 query array: key null and value null → skipped
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"disabled\": false, \"key\": null, \"value\": null}]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), true);

		// v2.1 query array: disabled=true with key/value → skipped
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"disabled\": true, \"key\": \"user\", \"value\": null}]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), true);
	}

	@Test(expectedExceptions = IllegalArgumentException.class,
			expectedExceptionsMessageRegExp = "Query key not found: 'NEW_KEY'")
	public void testQuerySetThrowWhenKeyNotFound() throws Exception {
		Collection col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image\"}}}"
						+ "]}]}")
				.getAsJsonObject());
		Request template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);

		// query builder: add creates new key on cloned query
		Query query = template.getQueries().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(query.toString(), "  NEW_KEY                             = " + TEST_USERNAME + "\n");

		// query builder: set missing key on original query → throws
		template.getQueries().builder().set("NEW_KEY", TEST_USERNAME);
	}

	// -------------------------------------------------------------------------
	// Test Body
	// -------------------------------------------------------------------------

	@Test
	public void testBodySet() throws Exception {
		Request req;
		Request template = col.getRequest(LOGIN_GET_TOKEN);
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
		Request template = col.getRequest(GET_AUTH_USER);

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
		assertEquals(body.isEmpty(), false);

		// raw mode: options absent/empty → language=""
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"hello\",\"options\":{}}}")
				.getAsJsonObject());
		assertEquals(body.getLanguage(), "");
		assertEquals(body.toString(), "[raw] hello");

		// raw mode: options.raw exists but language absent → language=""
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"hello\",\"options\":{\"raw\":{}}}}")
				.getAsJsonObject());
		assertEquals(body.getLanguage(), "");

		// graphql mode: parsed null → builder falls back to empty JsonObject
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"graphql\"}}").getAsJsonObject());
		assertEquals(body.builder().end().getRaw(), "{}");
		assertEquals(body.toString(), "[graphql]");

		// graphql mode: graphql query present but unsupported for parsed body builder → empty JsonObject
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"graphql\",\"graphql\":{\"query\":\"{ hero }\"}}}")
				.getAsJsonObject());
		assertEquals(body.builder().end().getRaw(), "{}");
		assertEquals(body.toString(), "[graphql]");

		// raw/json: valid JSON array → parsed non-null but builder falls back to empty JsonObject
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"[1,2,3]\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		assertNotNull(body.getParsed());
		assertEquals(body.getParsed().isJsonArray(), true);
		assertEquals(body.builder().end().getRaw(), "{}");

		// raw/json: invalid JSON raw string → parse failure ignored, mode remains raw
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"[{null}]\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		assertEquals(body.getMode(), "raw");

		// formdata mode: string formdata payload → mode preserved
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"formdata\",\"formdata\":\"[{null}]\"}}")
				.getAsJsonObject());
		assertEquals(body.getMode(), "formdata");

		// formdata mode: file key entry → raw remains empty; toString includes formdata payload
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"formdata\",\"formdata\":\"[{\\\"key\\\":\\\"file\\\"}]\"}}")
				.getAsJsonObject());
		assertEquals(body.getMode(), "formdata");
		assertEquals(body.getRaw(), "");
		assertEquals(body.toString(), "[formdata] [{\"key\":\"file\"}]");

		// urlencoded mode: mode preserved
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"urlencoded\"}}").getAsJsonObject());
		assertEquals(body.getMode(), "urlencoded");

		// raw/json resolve: number value → not substituted
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"count\\\":42}\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		resolved = body.builder().resolve(Map.of("count", "99")).end();
		assertEquals(resolved.getRaw(), "{\"count\":42}");

		// raw/json resolve: nested object value → not substituted
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"meta\\\":{\\\"v\\\":1}}\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		resolved = body.builder().resolve(Map.of("v", "replaced")).end();
		assertEquals(resolved.getRaw(), "{\"meta\":{\"v\":1}}");
	}

	@Test(expectedExceptions = IllegalArgumentException.class,
			expectedExceptionsMessageRegExp = "Body key not found: 'NEW_KEY'")
	public void testBodySetThrowWhenKeyNotFound() {
		Request template = col.getRequest(LOGIN_GET_TOKEN);

		// body builder: add creates new key on cloned body
		Body body = template.getBody().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(body.toString(),
				"[raw/json] {\"username\":\"{{username}}\",\"password\":\"{{password}}\",\"NEW_KEY\":\""
						+ TEST_USERNAME + "\"}");

		// body builder: set missing key on original body → throws
		template.getBody().builder().set("NEW_KEY", TEST_USERNAME);
	}
}