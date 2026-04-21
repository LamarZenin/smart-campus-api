package com.smartcampus;

import javax.ws.rs.core.Application;

/**
 * JAX-RS Application configuration.
 * The base path /api/v1 is set directly in Main.java via the Grizzly URI.
 * By default, JAX-RS creates a new instance of each resource class per request
 * (request-scoped lifecycle). Shared state is held in the DataStore singleton
 * using ConcurrentHashMap to ensure thread safety across concurrent requests.
 */
public class SmartCampusApplication extends Application {
}
