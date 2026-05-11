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
 * Validates the parsed contents of DCS_Power collection and exercises
 * Request.build() / set() / setOrDefault() fluent API with Rest Assured calls.
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
		assertEquals(this.col.toString(), col.toString());
		assertEquals(this.col.getRoot(), col.getRoot());
		assertEquals(this.col.getName(), col.getName());
		assertEquals(this.col.getFolders().toString(), col.getFolders().toString());
		assertEquals(this.col.getRequests().toString(), col.getRequests().toString());
		assertEquals(col.getFolder("UNKNOWN"), null);
		assertEquals(col.getRequest("UNKNOWN"), null);
		assertEquals(col.getRequest(GET_AUTH_USER).toString(), "[GET   ] Get current auth user                    -> {{base_url}}/auth/me");
		ParamBuilder.substituteVars(null, null);
	}
	
	@Test
	public void testCollectionFromString() throws Exception {
		Collection col = Collection.load(JsonParser.parseString("{}").getAsJsonObject());
		col.print();
		
		col = Collection.load(JsonParser.parseString("{\"info\": {}}").getAsJsonObject());
		assertEquals(col.getName(), "Unnamed Collection");
		
		col = Collection.load(JsonParser.parseString("{\"item\": [{}]}").getAsJsonObject());
		assertEquals(col.getFolders().size(), 0);
		
		col = Collection.load(JsonParser.parseString("{\"item\": [{\"name\": \"" + PRODUCT_FOLDER + "\", \"item\": []}]}").getAsJsonObject());
		assertEquals(col.getFolder(PRODUCT_FOLDER).toString(), "");
		col.print();
		
		col = Collection.load(JsonParser.parseString("{\"item\": [{\"request\":{}}]}").getAsJsonObject());
		assertEquals(col.getRequests().size(), 1);
		col.print();
	}
	
	// -------------------------------------------------------------------------
	// Test Folder
	// -------------------------------------------------------------------------
	@Test
	public void testFolder() throws Exception {
		Collection col = Collection.load(JsonParser.parseString("{\"item\": [{\"name\": \"" + PRODUCT_FOLDER + "\", \"item\": []}]}").getAsJsonObject());
		Folder folder = col.getFolder(PRODUCT_FOLDER);
		assertEquals(folder.getRequests().size(), 0);
		folder.print();
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":[{\"request\":{}}]}]}").getAsJsonObject());
		folder = col.getFolder(PRODUCT_FOLDER);
		assertEquals(folder.getRequests().size(), 1);
		assertEquals(folder.getRequest("UNKOWN"), null);
		Request req = folder.getRequest("Unnamed");
		assertEquals(req.toString(), "[GET   ] Unnamed                                  -> ");
		folder.print();
	}

	// -------------------------------------------------------------------------
	// Test Request
	// -------------------------------------------------------------------------
	@Test
	public void testRequest() throws Exception {
		Request request = folder.getRequest(ALL_PRODUCTS);
		assertEquals(request.getName(), ALL_PRODUCTS);
		// request.print();
	}
	
	@Test
	public void testRequestFromString() throws Exception {
		Collection col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"request\":{\"url\": \"http://github.com\"}"
						+ "}]}]}").getAsJsonObject());
		Request req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getUrl(), "http://github.com");
		assertEquals(req.getMethod(), "GET");
		assertEquals(req.getFolderName(), PRODUCT_FOLDER);
		assertEquals(req.getQueries().isEmpty(), true);
		req.print();
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"description\": \"TODO\"}"
				+ "}]}]}").getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getDescription(), "TODO");
		req.print();
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"description\": null}"
				+ "}]}]}").getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getDescription(), "");
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"description\": {}}"
				+ "}]}]}").getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getDescription(), "");
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"description\": {\"content\": \"TODO\"}}"
				+ "}]}]}").getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getDescription(), "TODO");
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"url\": null}"
				+ "}]}]}").getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getUrl(), "");
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"url\": {}}"
				+ "}]}]}").getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getUrl(), "");

		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"auth\":{\"type\":\"basic\",\"basic\":[{\"key\":\"" + TEST_USERNAME + "\"}]}}"
				+ "}]}]}").getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getAuth().toString(), "[basic] {" + TEST_USERNAME + "=}");
		req.print();

		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"header\":[{\"key\":\"" + TEST_USERNAME + "\"}]}"
				+ "}]}]}").getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getHeader().toString(), "  " + TEST_USERNAME + "                           = \n");
		req.print();
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"body\":{}}"
				+ "}]}]}").getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getBody().toString(), "[none]");
		req.print();
		
		req.apply(RestAssured.given()); // Test Apply 
		

		col = Collection.load(JsonParser.parseString("{\"item\":[{\"request\":{\"header\":[{\"key\":\"X-Test\",\"value\":\"v\"}]," + 
				"\"body\":{\"mode\":\"raw\",\"raw\":\"[1,2,3]\", \"options\":{\"raw\":{\"language\":\"json\"}}}}}]}").getAsJsonObject());
		assertEquals(col.getRequests().size(), 1);
		req = col.getRequest("Unnamed");
		assertEquals(req.getHeader().getParams().size(), 1);
		req.apply(RestAssured.given()); // Test Apply 	
	}
	

	
	@Test(expectedExceptions = IllegalArgumentException.class, 
			expectedExceptionsMessageRegExp = "Body key not found: 'NEW_KEY'")
	public void testRequestThrowWhenKeyNotFound() throws Exception {
		Collection col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"body\":{}}"
				+ "}]}]}").getAsJsonObject());
		Request template = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		Request req = template.builder().body().add("NEW_KEY", TEST_USERNAME).end().build();
		assertEquals(req.getBody().toString(), "[none] {\"NEW_KEY\":\"" + TEST_USERNAME + "\"}");
		template.builder().body().set("NEW_KEY", TEST_USERNAME); // Throw exception
	}

	// -------------------------------------------------------------------------
	// Test Environment
	// -------------------------------------------------------------------------
	@Test
	public void testLoadFileEnvironment() throws Exception {
		Environment env = Environment.load("src/main/resources/" + ENVIRONMENT_FILE);
		assertEquals(this.env.toString(), env.toString());
		col.loadEnvironment("src/main/resources/" + ENVIRONMENT_FILE);
	}

	@Test
	public void testLoadResourceEnvironment() throws Exception {
		Environment env = Environment.load(TestCoverage.class.getClassLoader().getResourceAsStream(ENVIRONMENT_FILE));
		assertEquals(this.env.toString(), env.toString());
	}
	
	@Test
	public void testLoadStringEnvironment() throws Exception {
		Environment env;

		// name present
		env = Environment.load(JsonParser.parseString("{\"name\":\"TEST_NAME\"}").getAsJsonObject());
		assertEquals(env.getName(), "TEST_NAME");

		// name absent → default
		env = Environment.load(JsonParser.parseString("{}").getAsJsonObject());
		assertEquals(env.getName(), "Unknown Environment");
		env.print();

		// key absent → skipped
		env = Environment.load(JsonParser.parseString("{\"values\":[{\"value\":\"v\",\"enabled\":false}]}").getAsJsonObject());
		assertEquals(env.getParams().size(), 0);
		
		env = Environment.load(JsonParser.parseString("{\"values\":[{\"value\":\"v\",\"enabled\":true}]}").getAsJsonObject());
		assertEquals(env.getParams().size(), 0);

		// value absent → stored as ""
		env = Environment.load(JsonParser.parseString("{\"values\":[{\"key\":\"apikey\"}]}").getAsJsonObject());
		assertEquals(env.get("apikey"), "");
		env.print();
	}
	
	@Test(expectedExceptions = IllegalArgumentException.class, 
			expectedExceptionsMessageRegExp = "Environment key not found: 'NEW_KEY'")
	public void testEnvSetThrowWhenKeyNotFound() {
		Environment source = new Environment("Test Env");

		Environment target1 = source.builder().add("NEW_KEY", TEST_USERNAME).end();
		Environment target2 = target1.builder().set("NEW_KEY", ENV_TOKEN_KEY).end();
		Environment target3 = target2.builder().resolve(Map.of("OLD_KEY", TEST_USERNAME)).end();
		assertEquals(target1.toString(), "  NEW_KEY                             = " + TEST_USERNAME + "\n"); // the target1 value by add
		assertEquals(target2.toString(), "  NEW_KEY                             = " + ENV_TOKEN_KEY + "\n");  // the target2 value by set
		assertEquals(target3.toString(), "  NEW_KEY                             = " + ENV_TOKEN_KEY + "\n"
									   + "  OLD_KEY                             = " + TEST_USERNAME + "\n");

		assertEquals(source.getParams().size(), 0); // the source still mutable no any keys
		source.builder().set("NEW_KEY", TEST_USERNAME); // Throw exception
	}

	// -------------------------------------------------------------------------
	// Test Auth
	// -------------------------------------------------------------------------
	@Test
	public void testAuthSet() throws Exception {
		Request req, template = col.getRequest(GET_AUTH_USER);

		// Auth builder
		Auth auth = template.getAuth().builder().set("token", ENV_TOKEN_KEY).end();
		assertEquals(auth.toString(), "[bearer] {token=" + ENV_TOKEN_KEY + "}");

		// Request builder
		Environment new_env = env.builder().add("accessToken", ENV_TOKEN_KEY).end();
		req = template.builder().auth().add("TEST_USERNAME", TEST_USERNAME).end().build(new_env);
		assertEquals(req.getAuth().toString(), "[bearer] {token=" + ENV_TOKEN_KEY + ", TEST_USERNAME=" + TEST_USERNAME + "}");
		req = template.builder().auth(c ->c.add("TEST_USERNAME", TEST_USERNAME)).build(new_env);
		assertEquals(req.getAuth().toString(), "[bearer] {token=" + ENV_TOKEN_KEY + ", TEST_USERNAME=" + TEST_USERNAME + "}");

		// Validation original template no changes
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

		// auth present but type absent → defaults to "noauth"
		auth = Auth.from(JsonParser.parseString("{\"auth\":{}}").getAsJsonObject());
		assertEquals(auth.getType(), "noauth");
		
		// type present but array key absent in authObj → no params
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"noauth\"}}").getAsJsonObject());
		assertEquals(auth.getType(), "noauth");
		assertEquals(auth.toString(), "[noauth]");
		assertEquals(auth.isNoAuth(), true);
		assertEquals(auth.getParams().size(), 0);
		auth.print();

		// v2.1 array: non-object element → skipped
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"bearer\",\"bearer\":[\"not-an-object\"]}}").getAsJsonObject());
		assertEquals(auth.toString(), "[bearer] {}");

		// v2.1 array: key absent → entry skipped (key defaults to "", then skipped)
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"bearer\",\"bearer\":[{\"value\":\"tok\"}]}}").getAsJsonObject());
		assertEquals(auth.toString(), "[bearer] {}");

		// v2.1 array: value absent → stored as ""
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"bearer\",\"bearer\":[{\"key\":\"token\"}]}}").getAsJsonObject());
		assertEquals(auth.get("token"), "");
		assertEquals(auth.getParams().entrySet().stream().anyMatch(h -> "token".equals(h.getKey())), true);
		auth.print();

		// v2.0 fallback: non-primitive value → stored as ""
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"basic\",\"basic\":{\"TEST_USERNAME\":\"" + TEST_USERNAME + "\",\"nested\":{\"a\":1}}}}").getAsJsonObject());
		assertEquals(auth.get("TEST_USERNAME"), TEST_USERNAME);
		assertEquals(auth.get("nested"), "");
	}

	@Test(expectedExceptions = IllegalArgumentException.class, 
			expectedExceptionsMessageRegExp = "Auth key not found: 'NEW_KEY'")
	public void testAuthSetThrowWhenKeyNotFound() {
		Request template = col.getRequest(GET_AUTH_USER);
		// Auth builder
		Auth auth = template.getAuth().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(auth.toString(), "[bearer] {token={{accessToken}}, NEW_KEY=" + TEST_USERNAME + "}");

		template.getAuth().builder().set("NEW_KEY", TEST_USERNAME); // Throw exception
	}

	// -------------------------------------------------------------------------
	// Test Header
	// -------------------------------------------------------------------------

	@Test
	public void testHeaderSet() {
		Request req, template = col.getRequest(GET_AUTH_USER);
		
		// Header builder
		Header header = template.getHeader().builder().set("Content-Type", ENV_TOKEN_KEY).end();
		assertEquals(header.toString(), "  Content-Type                        = " + ENV_TOKEN_KEY + "\n");

		// Request builder
		req = template.builder().headers().add("token", ENV_TOKEN_KEY).end().build(env);
		assertEquals(req.getHeader().toString(),"  Content-Type                        = application/json\n"
											  + "  token                               = " + ENV_TOKEN_KEY + "\n");
		req = template.builder().headers(c -> c.add("token", ENV_TOKEN_KEY)).build(env);
		assertEquals(req.getHeader().toString(),"  Content-Type                        = application/json\n"
											  + "  token                               = " + ENV_TOKEN_KEY + "\n");

		// Validation original template no changes
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

		// header is not a JsonArray → empty
		header = Header.from(JsonParser.parseString("{\"header\":\"not-an-array\"}").getAsJsonObject());
		assertEquals(header.isEmpty(), true);

		// array element is not a JsonObject → skipped
		header = Header.from(JsonParser.parseString("{\"header\":[\"not-an-object\"]}").getAsJsonObject());
		assertEquals(header.isEmpty(), true);

		// key absent → stored under ""
		header = Header.from(JsonParser.parseString("{\"header\":[{\"value\":\"v\"}]}").getAsJsonObject());
		assertEquals(header.isEmpty(), true);

		// value absent → stored as ""
		header = Header.from(JsonParser.parseString("{\"header\":[{\"key\":\"X-NoVal\"}]}").getAsJsonObject());
		assertEquals(header.get("X-NoVal"), "");
		header.print();

		// full happy path: key + value + disabled=false
		header = Header.from(JsonParser.parseString("{\"header\":[{\"key\":\"Accept\",\"value\":\"application/json\",\"disabled\":false}]}").getAsJsonObject());
		assertEquals(header.get("Accept"), "application/json");
	}
	
	@Test(expectedExceptions = IllegalArgumentException.class, 
			expectedExceptionsMessageRegExp = "Header key not found: 'NEW_KEY'")
	public void testHeaderSetThrowWhenKeyNotFound() {
		Request template = col.getRequest(GET_AUTH_USER);
		// Auth builder
		Header header = template.getHeader().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(header.toString(), "  Content-Type                        = application/json\n"
									  + "  NEW_KEY                             = " + TEST_USERNAME + "\n");
		template.getHeader().builder().set("NEW_KEY", TEST_USERNAME); // Throw exception
	}

	// -------------------------------------------------------------------------
	// Test Query
	// -------------------------------------------------------------------------
	@Test
	public void testQuerySet() throws Exception {
		Collection col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\"}]}}}"
				+ "]}]}" ).getAsJsonObject());
		Request template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), false);
		template.print();
		
		Environment env = new Environment("Test Env").builder()
				.add("base_url", "https://dummyjson.com")
				.add("image_text", "Original")
				.end();

		Request req = template.builder()
				.queries(q -> q.set("text", "JPostman"))
				.build(env);
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}#preview\",\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\"}]}}}"
				+ "]}]}" ).getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		req = template.builder()
				.queries(q -> q.set("text", "JPostman"))
				.build(env);
		
		assertEquals(template.getQuery().get("text"), "{{image_text}}");
		assertEquals(req.getQuery().get("text"), "JPostman");
		assertEquals(req.getUrl(), env.get("base_url") + "/image?text=JPostman#preview");
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image#preview\",\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\"}]}}}"
				+ "]}]}" ).getAsJsonObject());
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
		Collection col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image\"}}}"
				+ "]}]}" ).getAsJsonObject());
		Request template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), true);
		template.getQueries().print();
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\"}]}}}"
				+ "]}]}" ).getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), false);
		template.getQueries().print();
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":null}}}"
				+ "]}]}" ).getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), true);
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[1]}}}"
				+ "]}]}" ).getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), true);
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"disabled\": true}]}}}"
				+ "]}]}" ).getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), true);
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"disabled\": false, \"key\": null, \"value\": null}]}}}"
				+ "]}]}" ).getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), true);
		
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"disabled\": true, \"key\": \"user\", \"value\": null}]}}}"
				+ "]}]}" ).getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getQueries().isEmpty(), true);
	}
	
	@Test(expectedExceptions = IllegalArgumentException.class, 
			expectedExceptionsMessageRegExp = "Query key not found: 'NEW_KEY'")
	public void testQuerySetThrowWhenKeyNotFound() throws Exception {
		Collection col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image\"}}}"
				+ "]}]}" ).getAsJsonObject());
		Request template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);

		Query query = template.getQueries().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(query.toString(), "  NEW_KEY                             = " + TEST_USERNAME + "\n");
		template.getQueries().builder().set("NEW_KEY", TEST_USERNAME); // Throw exception
	}

	// -------------------------------------------------------------------------
	// Test Body
	// -------------------------------------------------------------------------
	@Test
	public void testBodySet() throws Exception {
		Request req, template = col.getRequest(LOGIN_GET_TOKEN);
		template.print();

		// Body builder
		Body body = template.getBody().builder().set("username", TEST_USERNAME).end();
		assertEquals(body.toString(), "[raw/json] {\"username\":\"" + TEST_USERNAME + "\",\"password\":\"{{password}}\"}");

		// Request builder
		req = template.builder().body().set("username", TEST_USERNAME).end().build(env);
		assertEquals(req.getBody().toString(), "[raw/json] {\"username\":\"" + TEST_USERNAME + "\",\"password\":\"emilyspass\"}");
		req = template.builder().body(c -> c.set("username", TEST_USERNAME)).build(env);
		assertEquals(req.getBody().toString(), "[raw/json] {\"username\":\"" + TEST_USERNAME + "\",\"password\":\"emilyspass\"}");

		// Validation original template no changes
		assertEquals(template.getBody().toString(), "[raw/json] {\n\t\"username\": \"{{username}}\",\n\t\"password\": \"{{password}}\"\n}");
	}
	
	@Test
	public void testBodyNewSet() throws Exception {
		Request template = col.getRequest(GET_AUTH_USER);
		Body body = template.getBody().builder()
				.add("count", 25)
				.add("active", true)
				.add("tags", List.of(1, true, "$")).end();
		assertEquals(body.toString(), "[none] {\"count\":25,\"active\":true,\"tags\":[1,true,\"$\"]}");
	}

	@Test
	public void testBodySetFromString() throws Exception {
		Body body, resolved;

		// body is JSON null → mode "none"
		body = Body.from(JsonParser.parseString("{\"body\":null}").getAsJsonObject());
		assertEquals(body.toString(), "[none]");
		
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\": null}}").getAsJsonObject());
		assertEquals(body.toString(), "[none]");
		assertEquals(body.getMode(), "none");
		assertEquals(body.isEmpty(), true);
		body.print();

		// raw mode, raw key absent → raw=""
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\"}}").getAsJsonObject());
		assertEquals(body.toString(), "[raw] ");
		assertEquals(body.getMode(), "raw");
		assertEquals(body.getRaw(), "");
		assertEquals(body.getParsed(), null);
		assertEquals(body.isEmpty(), false);

		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"hello\",\"options\":{}}}").getAsJsonObject());
		assertEquals(body.getLanguage(), "");
		assertEquals(body.toString(), "[raw] hello");
		
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"hello\",\"options\":{\"raw\":{}}}}").getAsJsonObject());
		assertEquals(body.getLanguage(), "");

		// parsed=null path in builder() — graphql mode
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"graphql\"}}").getAsJsonObject());
		assertEquals(body.builder().end().getRaw(), "{}"); // parsed=null → new JsonObject()
		assertEquals(body.toString(), "[graphql]");
		
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"graphql\",\"graphql\":{\"query\":\"{ hero }\"}}}").getAsJsonObject());
		assertEquals(body.builder().end().getRaw(), "{}"); // parsed=null → new JsonObject()
		assertEquals(body.toString(), "[graphql]");

		// raw mode, language=json, valid JSON array → parsed non-null but not JsonObject → builder uses empty JsonObject
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"[1,2,3]\",\"options\":{\"raw\":{\"language\":\"json\"}}}}").getAsJsonObject());
		assertNotNull(body.getParsed());
		assertEquals(body.getParsed().isJsonArray(), true);
		assertEquals(body.builder().end().getRaw(), "{}"); // non-object → builder falls back to empty JsonObject
		
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"[{null}]\",\"options\":{\"raw\":{\"language\":\"json\"}}}}").getAsJsonObject());
		assertEquals(body.getMode(), "raw");

		// graphql mode, graphql key absent → raw=""
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"formdata\",\"formdata\":\"[{null}]\"}}").getAsJsonObject());
		assertEquals(body.getMode(), "formdata");
	
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"formdata\",\"formdata\":\"[{\\\"key\\\":\\\"file\\\"}]\"}}").getAsJsonObject());
		assertEquals(body.getMode(), "formdata");
		assertEquals(body.getRaw(), "");
		assertEquals(body.toString(), "[formdata] [{\"key\":\"file\"}]");

		// urlencoded mode
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"urlencoded\"}}").getAsJsonObject());
		assertEquals(body.getMode(), "urlencoded");

		// resolve: number value → not substituted (isString() = false)
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"count\\\":42}\",\"options\":{\"raw\":{\"language\":\"json\"}}}}").getAsJsonObject());
		resolved = body.builder().resolve(Map.of("count", "99")).end();
		assertEquals(resolved.getRaw(), "{\"count\":42}"); // number untouched

		// resolve: nested object value → not substituted (isJsonPrimitive() = false)
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"meta\\\":{\\\"v\\\":1}}\",\"options\":{\"raw\":{\"language\":\"json\"}}}}").getAsJsonObject());
		resolved = body.builder().resolve(Map.of("v", "replaced")).end();
		assertEquals(resolved.getRaw(), "{\"meta\":{\"v\":1}}"); // nested object untouched
	}
	
	@Test(expectedExceptions = IllegalArgumentException.class, 
			expectedExceptionsMessageRegExp = "Body key not found: 'NEW_KEY'")
	public void testBodySetThrowWhenKeyNotFound() {
		Request template = col.getRequest(LOGIN_GET_TOKEN);
		// Body builder
		Body body = template.getBody().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(body.toString(), "[raw/json] {\"username\":\"{{username}}\",\"password\":\"{{password}}\",\"NEW_KEY\":\"" + TEST_USERNAME + "\"}");
		template.getBody().builder().set("NEW_KEY", TEST_USERNAME); // Throw exception
	}
}
