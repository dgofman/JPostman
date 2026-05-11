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

public class TestRestAssured {

	private static final Logger log = LoggerFactory.getLogger(TestRestAssured.class);
	
	private Collection col;
	private Environment env;
	private Folder folder;

	@BeforeClass
	public void load() throws Exception {
		col = Collection.load(TestCoverage.class.getClassLoader().getResourceAsStream(COLLECTION_FILE));
		env = Environment.load(TestCoverage.class.getClassLoader().getResourceAsStream(ENVIRONMENT_FILE));
		folder = col.getFolder(PRODUCT_FOLDER);
		folder.print();
	}

	// -------------------------------------------------------------------------
	// Rest Assured API tests
	// -------------------------------------------------------------------------
	@Test
	public void testRestAssuredLogin() {
		assertNotNull(env, "Environment not loaded");
		Request template = col.getRequest(LOGIN_GET_TOKEN);
		assertNotNull(template, "Request template not found");

		Request req = template.builder().build(env);
		
		log.debug("REQUEST BEFORE: " + template.toString());
		log.debug("REQUEST AFTER:  " + req.toString());
		
		Response response = req.apply(given())
				//.log().all()
				.post(req.getUrl())
				.then()
				.log().ifValidationFails()
				.statusCode(200)
				.body("accessToken", notNullValue())
				.extract()
				.response();

		log.debug("ENV BEFORE:\n" + env.toString());
		env = env.builder().add(ENV_TOKEN_KEY, response.path("accessToken")).end(); // Override default environments
		log.debug("ENV AFTER:\n" + env.toString());
	}

	@Test(dependsOnMethods = "testRestAssuredLogin")
	public void testRestAssuredGetUser() {
		assertNotNull(env);
		Request template = col.getRequest(GET_AUTH_USER);
		assertNotNull(template);

		Request req = template.builder().build(env);
		req.apply(given())
				.auth().oauth2(env.get(ENV_TOKEN_KEY))
				//.log().all()
				.get(req.getUrl())
				.then()
				.log().ifValidationFails()
				.statusCode(200)
				.body("id", notNullValue())
				.extract()
				.response()
				//.prettyPrint()
				;
	}


	@Test
	public void testRestAssuredGetProducts() {
		assertNotNull(env);
		Request template = folder.getRequest(ALL_PRODUCTS);
		assertNotNull(template);

		Request req = template.builder().build(env);
		req.apply(given())
			//.log().all()
			.get(req.getUrl())
			.then()
			.log().ifValidationFails()
			.statusCode(200)
			.body("limit", notNullValue())
			.extract()
			.response()
			//.prettyPrint()
			;
	}

	@Test
	public void testRestAssuredGetProduct() {
		assertNotNull(env);
		Request template = folder.getRequest(SINGLE_PRODUCT);
		assertNotNull(template);

		Request req = template.builder().build(env);
		req.apply(given())
			//.log().all()
			.get(req.getUrl())
			.then()
			.log().ifValidationFails()
			.statusCode(200)
			.body("id", notNullValue())
			.extract()
			.response()
			//.prettyPrint()
			;
	}
	
	@Test
	public void testRestAssuredImage() throws IOException {
		assertNotNull(env);
		Folder folder = col.getFolder(IMAGE_FOLDER);
		folder.print();
		Request template = folder.getRequest(GENERATE_IMAGE);
		assertNotNull(template);
		template.print();

		Request req1 = template.builder().build(env);
		Request req2 = template.builder()
				.queries(c->c.set("text", "Hello World")) // or .queries().set("text", "Hello World").end()
				.build(env);
		
		log.debug("REQUEST 1: " + req1.toString());
		log.debug("REQUEST 2: " + req2.toString());
		
		Response response1 = req1.apply(given())
			.get(req1.getUrl())
			.then()
			.log().ifValidationFails()
			.statusCode(200)
			.extract()
			.response();
		
		Response response2 = req2.apply(given())
			.get(req2.getUrl())
			.then()
			.log().ifValidationFails()
			.statusCode(200)
			.extract()
			.response();
		
		assertFalse(
		        Arrays.equals(
		                response1.asByteArray(),
		                response2.asByteArray()),
		        "Expected different bytes");
		
		Files.write(Path.of("src/main/resources/hello_world.png"), response2.asByteArray());
	}
}
