package com.smartcampus;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

/**
 * JAX-RS Application configuration.
 *
 * Lifecycle note: By default, JAX-RS creates a new instance of each resource class
 * per request (request-scoped). This means shared state must be held in static
 * data stores (e.g., ConcurrentHashMap) defined outside the resource class,
 * not as instance fields, to avoid data loss between requests.
 */
@ApplicationPath("/api/v1")
public class SmartCampusApplication extends Application {
    // Jersey scans packages via ResourceConfig in Main.java;
    // this class just establishes the @ApplicationPath.
}
