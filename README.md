# JPostman

![Java](https://img.shields.io/badge/Java-11%2B-orange)
[![Build](https://github.com/dgofman/JPostman/actions/workflows/build.yml/badge.svg)](https://github.com/dgofman/JPostman/actions/workflows/build.yml)
![Maven](https://img.shields.io/badge/Maven-3.x-blue)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.dgofman/jpostman)](https://central.sonatype.com/artifact/io.github.dgofman/jpostman)
![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/dgofman/JPostman)
![Coverage](https://codecov.io/gh/dgofman/JPostman/branch/main/graph/badge.svg)
![License](https://img.shields.io/github/license/dgofman/JPostman)

<a href="https://repo1.maven.org/maven2/io/github/dgofman/jpostman/"><img src="logo.png" width="100"></a>

**JPostman** is a lightweight Java helper library that reuses exported **Postman collections** and **Postman environments** directly in Java and Rest Assured API tests.

Instead of copying request URLs, headers, authentication, query parameters, and request bodies into Java code, JPostman keeps Postman as the source of truth. Export the collection and environment, load them in Java, override only what your test needs, resolve Postman-style templates, and execute the final request with Rest Assured.

---

## Installation

```xml
<dependency>
    <groupId>io.github.dgofman</groupId>
    <artifactId>jpostman</artifactId>
    <version>1.3.1</version> <!-- replace with latest Maven Central version -->
</dependency>
```

---

## Why JPostman?

JPostman helps when API details are already maintained in Postman but tests are written in Java.

With JPostman you can:

- Load exported Postman collections and environments.
- Resolve `{{variable}}` templates from environments or local request-part values.
- Override URL query parameters, headers, auth, and JSON body fields fluently.
- Reuse Postman request definitions in Rest Assured tests.
- Avoid duplicating request configuration across Postman and Java.

---

## Exporting From Postman

Export your Postman collection and environment, then place them under project resources:

```text
src/main/resources/DummyJSON.postman_collection.json
src/main/resources/DummyJSON.postman_environment.json
```
### Export Collection
![Postman collection export](collections.png)

### Export Environment
![Postman environment export](environments.png)
---

## Supported Request Parts

JPostman parses and applies common Postman request components:

- Collection folders and requests
- URLs and URL query parameters
- Headers
- Auth parameters
- Raw JSON bodies
- Raw text/XML/template bodies
- Postman form-data and URL-encoded body payloads
- Environment variables
- Postman-style template replacement such as `{{base_url}}`, `{{username}}`, `{{password}}`, and `{{accessToken}}`

---

## Basic Usage

```java
Collection col = Collection.load(
        TestRestAssured.class.getClassLoader()
                .getResourceAsStream("DummyJSON.postman_collection.json"));

Environment env = Environment.load(
        TestRestAssured.class.getClassLoader()
                .getResourceAsStream("DummyJSON.postman_environment.json"));

Request template = col.getRequest("Login user and get tokens");
Request req = template.builder().build(env);

Response response = req.execute(given())
        .then()
        .statusCode(200)
        .extract()
        .response();
```

`build(env)` resolves remaining `{{variable}}` templates using the supplied environment.

---

## Request Execution API

JPostman separates **request configuration** from **request execution**.

Use `apply(...)` when you want to customize Rest Assured execution yourself:

```java
req.apply(given())
        .log().all()
        .when()
        .post(req.getUrl());
```

Use `execute(...)` when you want JPostman to apply the request configuration and execute the HTTP method from the Postman request:

```java
Response response = req.execute(given())
        .then()
        .statusCode(200)
        .extract()
        .response();
```

Supported methods:

- GET
- POST
- PUT
- PATCH
- DELETE
- HEAD
- OPTIONS

---

## Fluent Request Overrides

You can override only the values needed for a test:

```java
Request req = template.builder()
        .url(u -> u.set("text", "Hello World"))
        .headers(h -> h.add("X-Test", "123"))
        .auth(a -> a.set("token", "my-token"))
        .body(b -> b.set("username", "emilys"))
        .build(env);
```

Use `add(...)` to create or overwrite a value. Use `set(...)` when the key must already exist in the Postman export.

---

## URL Query Parameters

Query parameters are updated through the URL builder:

```java
Request req = template.builder()
        .url(u -> u.set("text", "Hello World"))
        .build(env);
```

Nested style is also supported:

```java
Request req = template.builder()
        .url()
            .set("text", "Hello World")
        .end()
        .build(env);
```

Use `url().add(...)` to create a new query parameter and `url().set(...)` to update an existing one.

---

## Headers and Auth

Headers:

```java
Request req = template.builder()
        .headers(h -> h.set("Content-Type", "application/json"))
        .build(env);
```

Auth:

```java
Request req = template.builder()
        .auth(a -> a.set("token", "my-token"))
        .build(env);
```

---

## Body Handling

### JSON Body Field Mutation

Use `body().set(...)` and `body().add(...)` for top-level JSON object fields.

Postman raw JSON body:

```json
{
    "username": "{{username}}",
    "password": "{{password}}"
}
```

Builder:

```java
Request req = template.builder()
        .body()
            .set("username", "emilys")
            .add("age", 21)
        .build(env);
```

Final body:

```json
{
    "username": "emilys",
    "password": "resolved-from-environment",
    "age": 21
}
```

### Deferred JSON Body Mutation

JPostman can queue `body().set(...)` and `body().add(...)` when the raw body is not valid JSON yet because of an unquoted template token.

Postman raw body:

```json
{
    "username": {{TOKEN}},
    "password": "{{password}}"
}
```

Builder:

```java
Request req = template.builder()
        .body()
            .set("password", "emilyspass")
            .add("age", 21)
            .json("TOKEN", "emilys")
        .build();
```

Final body:

```json
{
    "username": "emilys",
    "password": "emilyspass",
    "age": 21
}
```

If the body never becomes a JSON object, `add(...)` or `set(...)` throws an error explaining that a JSON object body is required.

### Raw Text/XML Body Templates

For raw text or XML bodies, use template resolution instead of JSON field mutation.

```xml
<id>{{USER_ID}}</id>
```

```java
Environment env = new Environment("Test Env")
        .builder()
        .add("USER_ID", "42")
        .end();

Request req = template.builder().build(env);
```

Resolved body:

```xml
<id>42</id>
```

`body().set(...)` means “update a JSON object field,” not “replace any template variable.”

---

## Local Template Values with `map(...)` and `json(...)`

Local request-part values are resolved before `build(env)`, so they have higher priority than environment values. Tokens that are not provided locally remain available for final environment resolution.

### `map(...)`

Use `map(...)` for normal template replacement.

```java
Request req = template.builder()
        .url()
            .set("q", "find")
            .map("TOKEN", "login")
        .build(env);
```

Result:

```text
{{TOKEN}} -> login
```

For JSON bodies, use `map(...)` when the placeholder is already inside quotes:

```json
{
    "age": "{{age}}"
}
```

```java
Request req = template.builder()
        .body()
            .map("age", 25)
        .build();
```

Final body:

```json
{
    "age": "25"
}
```

### `json(...)`

Use `json(...)` when a raw JSON body has unquoted template placeholders and string values must become JSON-safe strings.

Raw body:

```json
{
    "username": {{username}},
    "age": {{age}},
    "single": {{single}}
}
```

Builder:

```java
Request req = template.builder()
        .body()
            .json("username", "emmy", "age", 25, "single", true)
        .build();
```

Final body:

```json
{
    "username": "emmy",
    "age": 25,
    "single": true
}
```

Rule of thumb:

```json
"username": "{{username}}"
```

Use `map(...)`.

```json
"username": {{username}}
```

Use `json(...)`.

---

## Advanced Login Example

This example demonstrates URL overrides, local token resolution, auth override, deferred JSON body mutation, JSON-stringified token replacement, and final environment resolution.

```java
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
```

Example Postman raw body for this pattern:

```json
{
    "username": {{TOKEN}},
    "password": "{{password}}"
}
```

Example final body:

```json
{
    "username": "emilys",
    "password": "emilyspass",
    "age": 21
}
```

---

## Environment Overrides

You can create or modify environments in Java:

```java
Environment testEnv = env.builder()
        .set("username", "emilys")
        .set("password", "emilyspass")
        .add("USER_ID", "42")
        .end();

Request req = template.builder().build(testEnv);
```

Use environment values for request-wide template replacement. Use part-level `map(...)` or `json(...)` when only one request part should receive a local value before final environment resolution.

---

## Troubleshooting

### `Body builder add/set requires a JSON object body`

This means `body().add(...)` or `body().set(...)` was used on a body that did not become a JSON object.

Valid for body mutation:

```json
{
    "username": "{{username}}"
}
```

Also valid after `json(...)` resolution:

```json
{
    "username": {{username}}
}
```

Not valid for body mutation:

```xml
<id>{{USER_ID}}</id>
```

For XML/text, use environment or part-level template resolution, not JSON body mutation.

### `Body key not found: 'KEY'`

This means `body().set("KEY", value)` was used, but the resolved JSON body did not contain that field. Use `add(...)` if you want to create a new field.

### `URL query parameter not found: 'KEY'`

This means `url().set("KEY", value)` was used, but the query parameter does not exist in the Postman URL/query list. Use `url().add(...)` if you want to create a new query parameter.

### Unknown template variables become empty

If a template variable is missing from the supplied map/environment, Handlebars renders it as an empty value:

```java
Params.substituteVars("<id>{{UNKNOWN_ID}}</id>", Map.of("USER_ID", "42"));
```

Result:

```xml
<id></id>
```

---

## Summary

Recommended usage:

- Keep request definitions in Postman.
- Use `build(env)` for request-wide `{{KEY}}` resolution.
- Use `.url(...)` for URL/query overrides.
- Use `.headers(...)` for headers.
- Use `.auth(...)` for auth values.
- Use `.body(...)` for JSON body field mutation.
- Use `.map(...)` for normal local token replacement.
- Use `.json(...)` for unquoted raw JSON placeholders that need JSON-safe string values.
