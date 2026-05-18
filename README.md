# JPostman

![Java](https://img.shields.io/badge/Java-11%2B-orange)
[![Build](https://github.com/dgofman/JPostman/actions/workflows/build.yml/badge.svg)](https://github.com/dgofman/JPostman/actions/workflows/build.yml)
![Maven](https://img.shields.io/badge/Maven-3.x-blue)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.dgofman/jpostman)](https://central.sonatype.com/artifact/io.github.dgofman/jpostman)
![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/dgofman/JPostman)
![Coverage](https://codecov.io/gh/dgofman/JPostman/branch/main/graph/badge.svg)
![License](https://img.shields.io/github/license/dgofman/JPostman)

<a href="https://repo1.maven.org/maven2/io/github/dgofman/jpostman/"><img src="logo.png" width="100"></a>

**JPostman** is a small Java helper library that lets you reuse exported **Postman collections** and **Postman environments** directly in Java tests.

Instead of copying request URLs, headers, authentication, URL query parameters, auth configuration, and request bodies from Postman into Java code, you keep Postman as the single source of truth. Export the collection and environment, load them in Java, optionally override only the values that are different for a test, and execute the request with Rest Assured.

---

## Why JPostman?

When API tests are written manually, the same request details often exist in two places:

1. Postman collection
2. Java test code

That creates duplication and maintenance problems.

If a header, body, URL, token, query parameter, or auth configuration changes in Postman, you also need to remember to update Java tests. JPostman reduces that duplication by letting Java tests load the same exported Postman resources.

With JPostman:

- Keep request definitions in Postman.
- Export the Postman collection and environment.
- Load them from Java.
- Build requests using environment values.
- Override only what is different for the test.
- Execute with Rest Assured.
- Resolve Postman-style `{{variable}}` templates in URLs, headers, auth, and bodies.

This is especially useful when Postman is already used by developers, QA, or API teams.

---

## Exporting From Postman

JPostman works with exported Postman collections and environments.

### Export Collection

In Postman, right-click your collection and export it:

![Postman collection export](collections.png)

### Export Environment

Export your environment the same way:

![Postman environment export](environments.png)

Place the exported files under project resources, for example:

```text
src/main/resources/DummyJSON.postman_collection.json
src/main/resources/DummyJSON.postman_environment.json
```

---

## Installation

Add JPostman to your Maven project:

```xml
<dependency>
    <groupId>io.github.dgofman</groupId>
    <artifactId>jpostman</artifactId>
    <version>1.3.0</version>  <!-- replace with latest Maven Central version -->
</dependency>
```

Use the latest version available from Maven Central if a newer release exists.

---

## Supported Request Parts

JPostman parses and applies common Postman request components:

- Collection folders
- Requests
- URLs
- URL query parameters
- Headers
- Auth parameters
- Raw JSON bodies
- Raw text/XML/template bodies
- Form-data and URL-encoded body payloads as exported by Postman
- Environment variables
- Variable replacement such as `{{base_url}}`, `{{username}}`, `{{password}}`, and `{{accessToken}}`

---

## Template Replacement

JPostman uses Handlebars-style template replacement for Postman variables.

Example Postman value:

```text
{{base_url}}/auth/login
```

Environment value:

```java
Environment env = new Environment("Local")
        .builder()
        .add("base_url", "https://dummyjson.com")
        .end();
```

Resolved result:

```text
https://dummyjson.com/auth/login
```

Template replacement is applied when you build a request with an environment:

```java
Request req = template.builder().build(env);
```

### Unknown Variables

If a template variable is not present in the environment, Handlebars renders it as an empty value.

Example:

```java
String result = ParamBuilder.substituteVars(
        "<id>{{UNKNOWN_ID}}</id>",
        Map.of("USER_ID", "42"));
```

Result:

```xml
<id></id>
```

This is normal Handlebars behavior.

---

## Basic Usage

Load a collection and environment:

```java
Collection col = Collection.load(
        TestRestAssured.class.getClassLoader()
                .getResourceAsStream("DummyJSON.postman_collection.json"));

Environment env = Environment.load(
        TestRestAssured.class.getClassLoader()
                .getResourceAsStream("DummyJSON.postman_environment.json"));
```

Get a request template from the collection:

```java
Request template = col.getRequest("Login user and get tokens");
```

Build a resolved request using the environment:

```java
Request req = template.builder().build(env);
```

Execute it with Rest Assured:

```java
Response response = req.apply(given())
        .post(req.getUrl())
        .then()
        .log().ifValidationFails()
        .statusCode(200)
        .body("accessToken", notNullValue())
        .extract()
        .response();
```

---

## Request Execution API

JPostman separates **request configuration** from **request execution**.

### Apply Request Configuration

```java
req.apply(given())
```

This applies the parsed Postman configuration to a Rest Assured request specification. It is useful when additional Rest Assured customization is needed:

```java
req.apply(given())
        .auth().oauth2(token)
        .log().all()
        .when()
        .get(req.getUrl());
```

### Configure and Execute

```java
req.execute(given())
```

This automatically:

1. applies request configuration
2. executes the HTTP method defined in the Postman collection

Supported methods:

- GET
- POST
- PUT
- PATCH
- DELETE
- HEAD
- OPTIONS

Example:

```java
Response response = req.execute(given())
        .then()
        .statusCode(200)
        .extract()
        .response();
```

---

## Fluent Request Overrides

You can override values from Postman without rewriting the whole request.

The builder API is intended to be consistent across request parts:

```java
Request req = template.builder()
        .url(u -> u.set("text", "Hello World"))
        .headers(h -> h.add("X-Test", "123"))
        .auth(a -> a.set("token", "my-token"))
        .body(b -> b.set("username", "emilys"))
        .build(env);
```

---

## Part-Level Template Resolution

You can resolve templates for only one request part by passing a local variable map to `.end(...)`.

Part-level resolution has higher priority than the final `build(env)` resolution because the local value replaces the `{{KEY}}` token first. After the token is gone, `build(env)` cannot override it.

Example:

```java
Request req = template.builder()
        .url()
            .set("text", "{{text}}")
        .end(Map.of("text", "local-text"))
        .body()
            .set("username", "{{username}}")
        .end(Map.of("username", "local-user"))
        .build(env);
```

If `env` also contains `text` or `username`, the local values win for those fields. Part-level `.end(params)` resolves only the keys present in `params`; other `{{KEY}}` tokens stay unchanged so they can still be resolved later by `build(env)`.

This works for all request part builders:

```java
.url().end(localParams)
.headers().end(localParams)
.auth().end(localParams)
.body().end(localParams)
```

Use this when one request part needs test-specific values but the rest of the request should still be resolved from the shared environment.

---

## Update URL Query Parameters

Query parameters are updated through the URL builder.

```java
Request req = template.builder()
        .url(u -> u.set("text", "Hello World"))
        .build(env);
```

Alternative nested style:

```java
Request req = template.builder()
        .url()
            .set("text", "Hello World")
        .end()
        .build(env);
```

Use `url(...)`, not `queries(...)`.

The URL builder controls both the base URL template and query parameter values. For example, a Postman URL like this:

```text
{{base_url}}/image/400x200?text=JPostman
```

can be changed to:

```java
Request req = template.builder()
        .url(u -> u.set("text", "Hello World"))
        .build(env);
```

---

## Update Headers

Use `headers(...)` to add or update request headers.

```java
Request req = template.builder()
        .headers(h -> h.add("X-Test", "123"))
        .build(env);
```

Nested style:

```java
Request req = template.builder()
        .headers()
            .add("X-Test", "123")
        .end()
        .build(env);
```

Use `add(...)` when you want to create or overwrite a header. Use `set(...)` when the header must already exist in the Postman request.

```java
Request req = template.builder()
        .headers(h -> h.set("Content-Type", "application/json"))
        .build(env);
```

---

## Update Auth Parameters

Use `auth(...)` to override parsed Postman auth parameters.

```java
Request req = template.builder()
        .auth(a -> a.set("token", "my-token"))
        .build(env);
```

This is useful when the collection contains auth like:

```text
Bearer {{accessToken}}
```

and a test needs to override or inject a token.

---

## Update JSON Body Fields

Use `body(...)` to update **top-level fields in a valid JSON object body**.

Example Postman raw JSON body:

```json
{
    "username": "{{username}}",
    "password": "{{password}}"
}
```

Override one JSON field:

```java
Request req = template.builder()
        .body(b -> b.set("username", "emilys"))
        .build(env);
```

Resulting body:

```json
{
    "username": "emilys",
    "password": "resolved-from-environment"
}
```

Important rule:

```text
body(b -> b.set("username", "emilys"))
```

means **set the JSON object field named `username`**.

It does **not** mean “replace the template variable `{{username}}` everywhere.”

---

## Raw Text/XML Body Templates

For raw text or XML bodies, use environment/template resolution instead of `body().set(...)`.

Example Postman raw body:

```xml
<id>{{USER_ID}}</id>
```

Correct usage:

```java
Collection col = Collection.load(...);

Environment env = new Environment("Test Env")
        .builder()
        .add("USER_ID", "42")
        .end();

Request template = col.getRequest("Unnamed");
Request req = template.builder().build(env);
```

Resolved body:

```xml
<id>42</id>
```

Do not use this for raw XML/text bodies:

```java
Request req = template.builder()
        .body(b -> b.set("USER_ID", "42"))
        .build();
```

That tries to update a JSON object field named `USER_ID`. It will fail if the body is not a JSON object.

---

## Body Builder Rules

The body builder supports two different concepts:

### 1. Template Resolution

Template resolution replaces `{{KEY}}` values using the environment.

```java
Request req = template.builder().build(env);
```

This works for:

- JSON bodies
- raw text bodies
- XML bodies
- URL values
- headers
- auth values

### 2. JSON Object Mutation

JSON object mutation changes fields in a parsed JSON object body.

```java
Request req = template.builder()
        .body(b -> b.set("username", "emilys"))
        .build(env);
```

This requires the raw body to be valid JSON object text.

Valid:

```json
{
    "username": "{{username}}"
}
```

Not valid for `body().set(...)`:

```xml
<id>{{USER_ID}}</id>
```

Not valid for `body().set(...)`:

```json
[
    "{{username}}",
    "{{password}}"
]
```

For non-object bodies, use environment values and call `build(env)`.

---

## Add New Body Fields

For JSON object bodies, you can add new top-level fields:

```java
Request req = template.builder()
        .body(b -> b.add("traceId", "abc-123"))
        .build(env);
```

Example result:

```json
{
    "username": "emilys",
    "password": "resolved-from-environment",
    "traceId": "abc-123"
}
```

---

## Environment Overrides

You can create or modify environments in Java.

```java
Environment env = Environment.load(
        TestRestAssured.class.getClassLoader()
                .getResourceAsStream("DummyJSON.postman_environment.json"));

Environment testEnv = env.builder()
        .set("username", "emilys")
        .set("password", "emilyspass")
        .add("USER_ID", "42")
        .end();

Request req = template.builder().build(testEnv);
```

Use environment values when you want template replacement:

```java
{{username}}
{{password}}
{{USER_ID}}
```

Use builder `set(...)` on request parts when you want to mutate a known parsed field or parameter.

---

## `add(...)` vs `set(...)`

The fluent builders use two similar methods:

### `add(...)`

Adds a new value or overwrites an existing value.

```java
.headers(h -> h.add("X-Test", "123"))
.body(b -> b.add("traceId", "abc-123"))
```

### `set(...)`

Updates an existing value and may fail if the key is missing.

```java
.headers(h -> h.set("Content-Type", "application/json"))
.body(b -> b.set("username", "emilys"))
.auth(a -> a.set("token", "my-token"))
.url(u -> u.set("text", "Hello World"))
```

Use `set(...)` when the value should already exist in the Postman export. This helps catch accidental typos.

---

## Full Example

```java
Collection col = Collection.load(
        TestRestAssured.class.getClassLoader()
                .getResourceAsStream("DummyJSON.postman_collection.json"));

Environment env = Environment.load(
        TestRestAssured.class.getClassLoader()
                .getResourceAsStream("DummyJSON.postman_environment.json"));

Request template = col.getRequest("Login user and get tokens");

Environment testEnv = env.builder()
        .set("username", "emilys")
        .set("password", "emilyspass")
        .end();

Request req = template.builder()
        .headers(h -> h.set("Content-Type", "application/json"))
        .body(b -> b.set("username", "emilys"))
        .build(testEnv);

Response response = req.execute(given())
        .then()
        .statusCode(200)
        .body("accessToken", notNullValue())
        .extract()
        .response();
```

---

## Summary

JPostman makes Java API tests easier to maintain when Postman requests change.

Recommended usage:

- Keep request definitions in Postman.
- Use environments for `{{KEY}}` template replacement.
- Use `.url(...)` for URL/query overrides.
- Use `.headers(...)` for headers.
- Use `.auth(...)` for auth values.
- Use `.body(...)` only for valid JSON object body fields.
- Use `.end(params)` for part-level template resolution when local values should win.
- Use `build(env)` to resolve remaining templates across the request.
