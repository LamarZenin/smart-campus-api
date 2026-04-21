# Smart Campus Sensor & Room Management API

A RESTful API built with JAX-RS (Jersey) and an embedded Grizzly HTTP server for managing university campus rooms and IoT sensors.

---

## API Design Overview

The API follows REST architectural principles with a clear, versioned resource hierarchy:

```
/api/v1                              ← Discovery endpoint
/api/v1/rooms                        ← Room collection
/api/v1/rooms/{roomId}               ← Individual room
/api/v1/sensors                      ← Sensor collection (filterable by ?type=)
/api/v1/sensors/{sensorId}           ← Individual sensor
/api/v1/sensors/{sensorId}/readings  ← Sub-resource: reading history
```

All data is stored in-memory using `ConcurrentHashMap` and `ArrayList` — no database is used.

---

## How to Build and Run

### Prerequisites
- Java 11+
- Maven 3.6+

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/YOUR_USERNAME/smart-campus-api.git
cd smart-campus-api

# 2. Build the fat JAR
mvn clean package

# 3. Run the server
java -jar target/smart-campus-api-1.0-SNAPSHOT.jar
```

The server starts at **http://localhost:8080/api/v1**

Press `ENTER` in the terminal to stop the server.

---

## Sample curl Commands

### 1. Discover API metadata
```bash
curl -X GET http://localhost:8080/api/v1
```

### 2. Get all rooms
```bash
curl -X GET http://localhost:8080/api/v1/rooms
```

### 3. Create a new room
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"ENG-201","name":"Engineering Lab","capacity":40}'
```

### 4. Get all sensors filtered by type
```bash
curl -X GET "http://localhost:8080/api/v1/sensors?type=Temperature"
```

### 5. Register a new sensor linked to an existing room
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-002","type":"Temperature","status":"ACTIVE","currentValue":20.0,"roomId":"LIB-301"}'
```

### 6. Post a sensor reading
```bash
curl -X POST http://localhost:8080/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":23.4}'
```

### 7. Get reading history for a sensor
```bash
curl -X GET http://localhost:8080/api/v1/sensors/TEMP-001/readings
```

### 8. Attempt to delete a room that has sensors (triggers 409)
```bash
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301
```

### 9. Attempt to post a reading to a MAINTENANCE sensor (triggers 403)
```bash
curl -X POST http://localhost:8080/api/v1/sensors/OCC-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":5.0}'
```

### 10. Attempt to register a sensor with a non-existent roomId (triggers 422)
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"CO2-999","type":"CO2","status":"ACTIVE","currentValue":0.0,"roomId":"FAKE-999"}'
```

---

## Report: Answers to Coursework Questions

---

### Part 1 — Service Architecture & Setup

**Q: Explain the default lifecycle of a JAX-RS Resource class. Is a new instance created per request or is it a singleton? How does this impact data management?**

By default, JAX-RS creates a **new instance of each resource class for every incoming HTTP request** (request-scoped lifecycle). This means instance fields on a resource class are re-initialised with each request and cannot hold shared state between calls. If you stored your room or sensor data as instance fields on `RoomResource`, the data would be wiped after every request.

To work around this, shared mutable state must live **outside** the resource class in a structure that persists across instances. In this project, the `DataStore` singleton holds all data in `ConcurrentHashMap` structures. `ConcurrentHashMap` is used rather than a plain `HashMap` because multiple threads (each handling a separate request) may read and write to the store simultaneously. Using a non-thread-safe collection in a multi-threaded server risks race conditions, corrupted state, or data loss — `ConcurrentHashMap` provides atomic per-entry operations to prevent this.

**Q: Why is HATEOAS considered a hallmark of advanced RESTful design? How does it benefit client developers?**

HATEOAS (Hypermedia As The Engine Of Application State) means that API responses include links to related or next-available actions, rather than just raw data. For example, a room response might include a link to its sensors, and a sensor response might include a link to post a new reading.

This benefits client developers in several ways. First, clients do not need to hardcode API URLs — they discover them dynamically from responses, making the client more resilient to server-side URL changes. Second, the API becomes self-documenting to some extent; a developer can explore the API by following links without needing to consult external documentation for every action. Third, the server can guide the client through valid state transitions (e.g. only including a "delete" link when deletion is actually permitted), reducing the likelihood of clients sending invalid requests.

---

### Part 2 — Room Management

**Q: What are the implications of returning only IDs versus returning full room objects in a list response?**

Returning **only IDs** is bandwidth-efficient for large collections but forces the client to make N additional requests (one per room) to retrieve the data it needs — a classic "N+1 request" problem. This increases latency, server load, and client complexity.

Returning **full room objects** costs more bandwidth per response but is far more practical for most use cases. The client receives all necessary data in a single round-trip, which is faster and simpler to work with. For this campus API, returning full objects is the better default. If performance at very large scale becomes a concern, pagination (`?page=1&size=20`) or sparse fieldsets (`?fields=id,name`) can be introduced as targeted optimisations rather than degrading the default experience.

**Q: Is the DELETE operation idempotent in your implementation? Justify with examples.**

Idempotency means that making the same request multiple times produces the same server state as making it once. In terms of **server state**, `DELETE /api/v1/rooms/ENG-201` is idempotent — after the first successful deletion, the room is gone, and any subsequent DELETE leaves the server in the same state (room still absent).

However, in terms of **HTTP response codes**, the behaviour differs: the first call returns `200 OK`, while subsequent calls return `404 Not Found` because the resource no longer exists. RFC 7231 defines idempotency in terms of server state, not response codes, so a strict reading supports calling this idempotent. In practice though, clients relying on a `200` response to confirm success will see a `404` on retry and may incorrectly treat it as an error. A common pattern to achieve true response-level idempotency is to return `404` on the first call if the resource is already absent, but this coursework returns `200` on a successful delete and `404` on subsequent attempts, which is the most common real-world implementation.

---

### Part 3 — Sensor Operations & Linking

**Q: What happens if a client sends data in a format other than `application/json` to a `@Consumes(APPLICATION_JSON)` endpoint?**

JAX-RS inspects the `Content-Type` header of every incoming request and compares it against the media type declared in `@Consumes`. If the client sends `text/plain` or `application/xml` to an endpoint annotated with `@Consumes(MediaType.APPLICATION_JSON)`, the JAX-RS runtime automatically rejects the request and returns **HTTP 415 Unsupported Media Type** before the resource method is even invoked. No custom code is needed to handle this case. The client receives a clear error indicating they must send JSON, and the resource method is protected from receiving data it cannot parse.

**Q: Why is `@QueryParam` for filtering generally considered superior to embedding the filter value in the URL path (e.g. `/sensors/type/CO2`)?**

Query parameters are the semantically correct tool for filtering, searching, and sorting because they modify a view of a collection rather than identifying a distinct resource. Key reasons:

- **Optionality:** Query params are optional by nature. `/api/v1/sensors` and `/api/v1/sensors?type=CO2` refer to the same resource collection — one filtered, one not. A path segment like `/sensors/type/CO2` implies a different resource entirely.
- **Composability:** Multiple filters combine naturally: `?type=CO2&status=ACTIVE`. Achieving the same with path segments produces awkward, order-dependent URLs.
- **REST semantics:** The path should identify *what* you are accessing (the sensors collection). Query params describe *how* you want it (filtered, sorted, paginated). Mixing filter criteria into the path conflates identification with retrieval options.
- **Caching:** Intermediate caches and CDNs treat the same path as the same resource. Query params signal variation, which is the correct behaviour for filtered results.

---

### Part 4 — Sub-Resources

**Q: Discuss the architectural benefits of the Sub-Resource Locator pattern.**

The Sub-Resource Locator pattern (where a resource method returns an object rather than a `Response`, delegating further request processing to another class) provides several important architectural benefits:

**Separation of concerns:** `SensorResource` is responsible only for sensor-level operations. All logic relating to readings — fetching history, appending new readings, updating the parent sensor's `currentValue` — lives entirely within `SensorReadingResource`. Each class has a single, clearly defined responsibility.

**Reduced complexity:** Without this pattern, every nested path (`/sensors/{id}/readings`, `/sensors/{id}/readings/{rid}`) would be defined as methods in one large `SensorResource` class. As the API grows, this class becomes unwieldy and difficult to maintain. The locator pattern allows the URL hierarchy to be decomposed into a class hierarchy that mirrors it.

**Reusability and testability:** `SensorReadingResource` is a plain Java class that can be instantiated and unit tested independently of the JAX-RS runtime. It can also be reused if the same reading logic is needed from multiple access paths in the future.

**Context injection:** The locator passes the resolved `Sensor` object directly into `SensorReadingResource`'s constructor. The sub-resource therefore always operates on a validated, existing sensor — it does not need to re-fetch or re-validate the parent, eliminating redundant lookups.

---

### Part 5 — Error Handling & Logging

**Q: Why is HTTP 422 more semantically accurate than 404 when a referenced resource (e.g. roomId) doesn't exist inside a valid JSON payload?**

`404 Not Found` means the **URL being requested** does not correspond to any resource on the server. In this case, the URL `POST /api/v1/sensors` is perfectly valid — the sensors collection endpoint exists.

`422 Unprocessable Entity` means the server understood the request method and content type, and the JSON is syntactically valid, but the **semantic content of the payload** is invalid. The `roomId` field refers to a room that does not exist — the payload is logically broken even though it is structurally well-formed.

Using `404` would mislead clients into thinking the endpoint itself is missing. Using `422` accurately communicates that the request arrived at the right place but contained a reference that cannot be resolved, guiding the client to fix their payload rather than their URL.

**Q: From a cybersecurity standpoint, what are the risks of exposing Java stack traces to external consumers?**

A raw Java stack trace exposes a significant amount of information that an attacker can exploit:

- **Class and package names** reveal the internal structure of the application, including which frameworks and libraries are in use (e.g. `org.glassfish.jersey`, `com.fasterxml.jackson`).
- **Library versions** can be cross-referenced against public CVE databases (e.g. the National Vulnerability Database) to identify known exploits targeting that exact version.
- **File names and line numbers** reveal source code structure, which aids reverse engineering and helps an attacker craft targeted payloads.
- **Exception messages** often contain internal data such as SQL query fragments, file paths, or configuration values that should never be visible externally.
- **Logic flow information** from the call stack shows exactly which code paths are exercised, helping an attacker understand how to trigger specific failure modes.

The `GlobalExceptionMapper` addresses this by catching all unhandled `Throwable`s, logging the full stack trace server-side (where only authorised staff can see it), and returning only a generic `500 Internal Server Error` message to the client — revealing nothing about the internal implementation.

**Q: Why use JAX-RS filters for cross-cutting concerns like logging rather than inserting `Logger.info()` calls in every resource method?**

Manually adding logging statements to every resource method violates the **DRY (Don't Repeat Yourself) principle** and creates several maintenance problems. If the log format needs to change, every method must be updated. Developers adding new endpoints may forget to add logging, leading to gaps in observability. The resource methods themselves become cluttered with infrastructure concerns that are not related to their business purpose.

JAX-RS filters implement the **cross-cutting concerns** pattern. A single `LoggingFilter` class, registered once via `@Provider`, intercepts every request and response automatically — including those from endpoints that haven't been written yet. The resource methods remain clean and focused entirely on their logic. This approach is also consistent with how production systems handle other cross-cutting concerns such as authentication, rate limiting, and CORS — all implemented as filters without touching individual resource classes.
