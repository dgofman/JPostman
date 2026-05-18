package io.jpostman;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import io.restassured.response.Response;

import static io.jpostman.Constants.*;

/**
 * Demonstrates how to execute Postman-exported collection requests with Rest Assured.
 *
 * <p>This test class intentionally uses real requests from the exported Postman
 * collection and environment files instead of duplicating URLs, headers, auth,
 * body payloads, and query parameters directly in Java code.</p>
 */
public class TestRestAssured {

	private static final Logger log = LoggerFactory.getLogger(TestRestAssured.class);

	private Collection col;
	private Environment env;
	private Folder folder;

	@BeforeClass
	public void load() throws Exception {
		// Load exported Postman collection from classpath resources.
		col = Collection.load(TestCoverage.class.getClassLoader().getResourceAsStream(COLLECTION_FILE));

		// Load exported Postman environment from classpath resources.
		env = Environment.load(TestCoverage.class.getClassLoader().getResourceAsStream(ENVIRONMENT_FILE));

		// Cache Product folder for product-related API tests.
		folder = col.getFolder(PRODUCT_FOLDER);
		folder.print();
	}

	// -------------------------------------------------------------------------
	// Rest Assured API tests
	// -------------------------------------------------------------------------
	
	@Test
	public void testRestAssuredTestLogin() {
		assertNotNull(env, "Environment not loaded");

		// Get login request template from exported Postman collection.
		Request template = col.getRequest("Login user test request");
		assertNotNull(template, "Request template not found");
		
		// Build executable request by resolving {{variables}} from environment.
		Request req = template.builder()
		        .url() // starts from: {{base_url}}/auth/{{TOKEN}}?q={{TOKEN}}
		            .set("q", "find") // updates query parameter: q={{TOKEN}} -> q=find
		            .map("TOKEN", "login") // resolves URL path token locally: /auth/{{TOKEN}} -> /auth/login
		        .auth(c -> c
		            .set("token", "UNKNOWN")) // overrides bearer token value: token={{TOKEN}} -> token=UNKNOWN
		        .body()
		            .set("password", env.get("password")) // updates JSON field: "password":"{{password}}" -> "password":"emilyspass"
		            .add("age", 21) // adds JSON field: "age":21
		            .json("TOKEN", env.get("username")) // JSON-stringifies local token: {{TOKEN}} -> "emilys"
		        .build(env); // resolves remaining environment tokens, for example {{base_url}}

		// Show template before resolution and final request after resolution.
		log.debug("REQUEST BEFORE: " + template.toString());
		log.debug("REQUEST AFTER:  " + req.toString());
		req.print();

		// Execute login request and validate access token is returned.
		Response response = req.execute(given())
				.then()
				.log().ifValidationFails()
				.statusCode(200)
				.body("accessToken", notNullValue())
				.extract()
				.response();

		// Store runtime token back into environment for dependent authenticated calls.
		log.debug("ENV BEFORE:\n" + env.toString());
		env = env.builder().add(ENV_TOKEN_KEY, response.path("accessToken")).end();
		log.debug("ENV AFTER:\n" + env.toString());
	}

	@Test
	public void testRestAssuredLogin() {
		assertNotNull(env, "Environment not loaded");

		// Get login request template from exported Postman collection.
		Request template = col.getRequest(LOGIN_GET_TOKEN);
		assertNotNull(template, "Request template not found");

		// Build executable request by resolving {{variables}} from environment.
		Request req = template.builder().build(env);

		// Show template before resolution and final request after resolution.
		log.debug("REQUEST BEFORE: " + template.toString());
		log.debug("REQUEST AFTER:  " + req.toString());

		// Execute login request and validate access token is returned.
		Response response = req.execute(given())
				.then()
				.log().ifValidationFails()
				.statusCode(200)
				.body("accessToken", notNullValue())
				.extract()
				.response();

		// Store runtime token back into environment for dependent authenticated calls.
		log.debug("ENV BEFORE:\n" + env.toString());
		env = env.builder().add(ENV_TOKEN_KEY, response.path("accessToken")).end();
		log.debug("ENV AFTER:\n" + env.toString());
	}

	@Test(dependsOnMethods = "testRestAssuredLogin")
	public void testRestAssuredGetUser() {
		assertNotNull(env);

		// Get authenticated user request from collection.
		Request template = col.getRequest(GET_AUTH_USER);
		assertNotNull(template);

		// Resolve URL/auth placeholders from current environment.
		Request req = template.builder().build(env);

		// Execute GET /auth/me using bearer token captured during login.
		req.apply(given())
				.auth().oauth2(env.get(ENV_TOKEN_KEY))
				// .log().all()
				.get(req.toUrl())
				.then()
				.log().ifValidationFails()
				.statusCode(200)
				.body("id", notNullValue())
				.extract()
				.response();
	}

	@Test
	public void testRestAssuredGetProducts() {
		assertNotNull(env);

		// Get "all products" request from Product folder.
		Request template = folder.getRequest(ALL_PRODUCTS);
		assertNotNull(template);

		// Resolve environment variables and apply request settings to Rest Assured.
		Request req = template.builder().build(env);

		// Execute product list request and verify response contains pagination limit.
		req.execute(given())
				.then()
				.log().ifValidationFails()
				.statusCode(200)
				.body("limit", notNullValue())
				.extract()
				.response();
	}

	@Test
	public void testRestAssuredGetProduct() {
		assertNotNull(env);

		// Get single product request from Product folder.
		Request template = folder.getRequest(SINGLE_PRODUCT);
		assertNotNull(template);

		// Build executable request from Postman template.
		Request req = template.builder().build(env);

		// Execute single product request and verify product id exists.
		req.execute(given())
				.then()
				.log().ifValidationFails()
				.statusCode(200)
				.body("id", notNullValue())
				.extract()
				.response();
	}

	@Test
	public void testRestAssuredImage() throws IOException {
		assertNotNull(env);

		// Get Dynamic Image folder from collection.
		Folder folder = col.getFolder(IMAGE_FOLDER);
		folder.print();

		// Get image generation request template.
		Request template = folder.getRequest(GENERATE_IMAGE);
		assertNotNull(template);
		template.print();

		// Build original image request from environment.
		Request req1 = template.builder().build(env);

		// Build modified image request by overriding only one query parameter.
		Request req2 = template.builder()
				.url(q -> q.set("text", "Hello World")) // or .queries().set("text", "Hello World").end()
				.build(env);

		// Log both requests to show how one Postman template can produce variations.
		log.debug("REQUEST 1: " + req1.toString());
		log.debug("REQUEST 2: " + req2.toString());

		// Execute original image request.
		Response response1 = req1.execute(given())
				.then()
				.log().ifValidationFails()
				.statusCode(200)
				.extract()
				.response();

		// Execute modified image request.
		Response response2 = req2.execute(given())
				.then()
				.log().ifValidationFails()
				.statusCode(200)
				.extract()
				.response();

		// Different query text should produce different image bytes.
		assertFalse(
				Arrays.equals(
						response1.asByteArray(),
						response2.asByteArray()),
				"Expected different bytes");

		// Save generated image so it can be inspected manually after test execution.
		Files.write(Path.of("src/main/resources/hello_world.png"), response2.asByteArray());
	}
}