# Smart Campus Sensor & Room Management API

A RESTful API made with JAX-RS (Jersey) and an embedded Grizzly HTTP server for keeping track of university campus rooms and IoT sensors.

---

## API Design Overview

The API follows REST architectural principles with a clear, versioned resource hierarchy:


/api/v1                              ← Discovery endpoint
/api/v1/rooms                        ← Room collection
/api/v1/rooms/{roomId}               ← Individual room
/api/v1/sensors                      ← Sensor collection (filterable by ?type=)
/api/v1/sensors/{sensorId}           ← Individual sensor
/api/v1/sensors/{sensorId}/readings  ← Sub-resource: reading history


There is no database; all data is stored in memory with ConcurrentHashMap and ArrayList.

---

## How to Build and Run

### Prerequisites
- Java 11+
- Maven 3.6+

### Steps

bash
# 1. Clone the repository
git clone https://github.com/YOUR_USERNAME/smart-campus-api.git
cd smart-campus-api

# 2. Build the fat JAR
mvn clean package

# 3. Run the server
java -jar target/smart-campus-api-1.0-SNAPSHOT.jar


The server starts at **http://localhost:8080/api/v1**

Press ENTER in the terminal to stop the server.



## Sample curl Commands

### 1. Discover API metadata
bash
curl -X GET http://localhost:8080/api/v1

### 2. Get all rooms
bash
curl -X GET http://localhost:8080/api/v1/rooms


### 3. Create a new room
bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"ENG-201","name":"Engineering Lab","capacity":40}'


### 4. Get all sensors filtered by type
bash
curl -X GET "http://localhost:8080/api/v1/sensors?type=Temperature"


### 5. Register a new sensor linked to an existing room
bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-002","type":"Temperature","status":"ACTIVE","currentValue":20.0,"roomId":"LIB-301"}'


### 6. Post a sensor reading
bash
curl -X POST http://localhost:8080/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":23.4}'


### 7. Get reading history for a sensor
bash
curl -X GET http://localhost:8080/api/v1/sensors/TEMP-001/readings


### 8. Attempt to delete a room that has sensors (triggers 409)
bash
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301


### 9. Attempt to post a reading to a MAINTENANCE sensor (triggers 403)
bash
curl -X POST http://localhost:8080/api/v1/sensors/OCC-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":5.0}'


### 10. Attempt to register a sensor with a non-existent roomId (triggers 422)
bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"CO2-999","type":"CO2","status":"ACTIVE","currentValue":0.0,"roomId":"FAKE-999"}'


---

## Report: Answers to Coursework Questions

---

### Part 1 — Service Architecture & Setup

**Q: Explain the default lifecycle of a JAX-RS Resource class. Is a new instance created per request or is it a singleton? How does this impact data management?**

By default, JAX-RS makes a new instance of each resource class for each incoming HTTP request. This is called a request-scoped lifecycle. This means that instance fields on a resource class are set up again with each request and can't hold shared state between calls. If you kept your room or sensor data as instance fields on RoomResource, it would be erased every time you made a request.

To work around this, shared mutable state must live outside the resource class in a structure that survives between instances. The DataStore singleton in this project stores all of its data in ConcurrentHashMap structures. ConcurrentHashMap is utilized rather than a plain HashMap because many threads (each processing a distinct request) may read and write to the store simultaneously. Using a non-thread-safe collection in a multithreaded server risks race situations, corrupted state, or data loss. ConcurrentHashMap provides atomic per entry operations to prevent this.

**Q: Why is HATEOAS considered a hallmark of advanced RESTful design? How does it benefit client developers?**

HATEOAS (Hypermedia  As The Engine Of Application State) means that API answers include links to related or next available actions, rather than just plain data. For example, a room response might include a link to its sensors, and a sensor response might include a link to publish a new reading.

This assists client developers in various ways. First, clients do not need to hardcode API URLs they find them dynamically from responses, making the client more resilient to server-side URL changes.  Second, the API becomes self-documenting to some level; a developer can explore the API by following links without needing to reference external documentation for every action. Third, the server can guide the client through valid state transitions (e.g. only including a "delete" link when deletion is truly permitted), lowering the risk of clients delivering erroneous requests.

---

### Part 2 — Room Management

**Q: What are the implications of returning only IDs versus returning full room objects in a list response?**

Returning **only IDs** is bandwidth-efficient for big collections but compels the client to make N additional requests (one per room) to retrieve the data it needs a typical "N+1 request" dilemma.  This increases latency, server burden, and client complexity.

Returning full room objects costs more bandwidth per response but is significantly more feasible for most use scenarios. The client receives all relevant data in a single round-trip, which is faster and simpler to work with. For this campus API, returning entire objects is the preferred default. If performance at very large scale becomes an issue, pagination (?page=1&size=20) or sparse fieldsets (?fields=id,name) can be implemented as targeted optimisations rather than harming the default experience.

**Q: Is the DELETE operation idempotent in your implementation? Justify with examples.**

Idempotency indicates that making the same request several times produces the same server state as doing it once. In terms of server state, DELETE /api/v1/rooms/ENG-201 is idempotent, after the first successful deletion, the room is gone, and any subsequent DELETE leaves the server in the same state (room still absent).

However, in terms of HTTP response codes, the behaviour differs: the first call returns 200 OK, whereas subsequent calls return 404 Not Found because the resource no longer exists. RFC 7231 defines idempotency in terms of server state, not response codes, hence a strict reading justifies calling this idempotent. In practice though, clients relying on a 200 response to certify success may get a 404 on retry and may falsely regard it as an error. A popular pattern to achieve true response-level idempotency is to return 404 on the first call if the resource is already absent, but this approach returns 200 on a successful delete and 404 on future attempts, which is the most common real-world implementation.

---

### Part 3 — Sensor Operations & Linking

**Q: What happens if a client sends data in a format other than application/json to a @Consumes(APPLICATION_JSON) endpoint?**

JAX-RS inspects the Content-Type header of every incoming request and matches it against the media type indicated in @Consumes. If the client sends text/plain or application/xml to an endpoint annotated with @Consumes(MediaType.APPLICATION_JSON), the JAX-RS runtime immediately rejects the request and returns HTTP 415 Unsupported Media Type before the resource method is even executed. No custom code is needed to handle this circumstance. The client receives a clear error indicating they must supply JSON, and the resource method is protected from getting data it cannot interpret.

**Q: Why is @QueryParam for filtering generally considered superior to embedding the filter value in the URL path (e.g. /sensors/type/CO2)?**

Query parameters are the semantically right tool for filtering, searching, and sorting since they modify a view of a collection rather than identifying a distinct resource. Key reasons:

- **Optionality:** Query params are optional by nature. /api/v1/sensors and /api/v1/sensors?type=CO2 refer to the same resource collection, one filtered, one not.  A path segment like /sensors/type/CO2 denotes a distinct resource entirely. 
- **Composability:** Multiple filters combine naturally: ?type=CO2&status=ACTIVE. Achieving the same with path segments yields ugly, order-dependent URLs. 
- **REST semantics:** The path should identify what you are accessing (the sensors collection). Query params specify how you want it (filtered, sorted, paginated). Mixing filter criteria into the pipeline conflates identification with retrieval alternatives. 
- **Caching:** Intermediate caches and CDNs treat the same path as the same resource. Query params indicate fluctuation, which is the correct behaviour for filtered results.

---

### Part 4 — Sub-Resources

**Q: Discuss the architectural benefits of the Sub-Resource Locator pattern.**

The Sub-Resource Locator design (where a resource method returns an object rather than a Response, outsourcing further request processing to another class) provides several major architectural benefits:

**Separation of concerns:** SensorResource is responsible only for sensor-level operations. All functionality connected to readings obtaining history, appending new readings, changing the parent sensor's currentValue lives solely within SensorReadingResource. Each class has a single, well defined responsibility. 

**Reduced complexity:** Without this pattern, every nested path (/sensors/{id}/readings, /sensors/{id}/readings/{rid}) would be defined as methods in one huge SensorResource class. As the API grows, this class becomes bulky and difficult to maintain. The locator pattern allows the URL hierarchy to be dissected into a class hierarchy that mirrors it. 

**Reusability and testability:** SensorReadingResource is a standard Java class that can be instantiated and unit tested independently of the JAX-RS runtime. It can also be reused if the same reading logic is needed from various access paths in the future. 

**Context injection:**  The locator feeds the resolved Sensor object directly into SensorReadingResource's constructor. The sub-resource consequently always acts on a verified, existing sensor it does not need to refetch or re-validate the parent, eliminating repetitive lookups.

---

### Part 5 — Error Handling & Logging

**Q: Why is HTTP 422 more semantically accurate than 404 when a referenced resource (e.g. roomId) doesn't exist inside a valid JSON payload?**

404 Not Found signifies the URL being requested does not correspond to any resource on the server. In this situation, the URL POST /api/v1/sensors is absolutely legitimate the sensors collection endpoint exists. 422 Unprocessable Entity implies the server understood the request method and content type, and the JSON is syntactically valid, but the semantic content of the payload is wrong. The roomId field refers to a room that does not exist the payload is logically broken even though it is structurally well-formed. 

Using 404 might mislead customers into thinking the endpoint itself is missing. Using 422 appropriately indicates that the request arrived at the right spot but contains a reference that cannot be resolved, guiding the client to fix their payload rather than their URL.

**Q: From a cybersecurity standpoint, what are the risks of exposing Java stack traces to external consumers?**

A raw Java stack trace reveals a substantial amount of information that an attacker can exploit: 

- **Class and package names** expose the internal structure of the application, including which frameworks and libraries are in use (e.g. org.glassfish.jersey, com.fasterxml.jackson).
-  **Library versions** can be cross-referenced to public CVE databases (e.g. the National Vulnerability Database) to find known exploits targeting that precise version. 
- **File names and line numbers** expose source code structure, which assists reverse engineering and enables an attacker create targeted payloads. 
- **Exception messages** often contain internal data such as SQL query fragments, file paths, or configuration information that should never be seen externally. 
- **Logic flow information** from the call stack exposes exactly which code pathways are exercised, allowing an attacker understand how to induce specific failure scenarios. 

The GlobalExceptionMapper handles this by collecting all unhandled Throwables, logging the full stack trace server-side (where only authorised staff may access it), and returning just a generic 500 Internal Server Error message to the client disclosing nothing about the internal implementation.

**Q: Why use JAX-RS filters for cross-cutting concerns like logging rather than inserting Logger.info() calls in every resource method?**

Manually adding logging lines to every resource method violates the DRY (Don't Repeat Yourself) principle and poses various maintenance concerns. If the log format needs to change, every method must be modified.  Developers adding additional endpoints may neglect to add logging, resulting to gaps in observability. The resource methods themselves get clogged with infrastructure problems that are not connected to their business objective. 

JAX-RS filters employ the **cross-cutting concerns** pattern. A single LoggingFilter class, registered once via @Provider, intercepts every request and response automatically including those from endpoints that haven't been developed yet. The resource methods remain tidy and focused completely on their rationale. This technique is also consistent with how production systems manage other cross-cutting concerns such as authentication, rate restriction, and CORS all handled as filters without touching individual resource classes.
