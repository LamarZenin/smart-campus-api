package com.smartcampus.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Part 1 - Discovery Endpoint
 * GET /api/v1  ->  API metadata + HATEOAS links
 */
@Path("/")
public class DiscoveryResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response discover() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode info = mapper.createObjectNode();
        info.put("name", "Smart Campus Sensor & Room Management API");
        info.put("version", "1.0");
        info.put("description", "RESTful API for managing campus rooms and IoT sensors.");
        info.put("contact", "admin@smartcampus.ac.uk");

        ObjectNode links = mapper.createObjectNode();
        links.put("rooms", "/api/v1/rooms");
        links.put("sensors", "/api/v1/sensors");
        info.set("resources", links);

        return Response.ok(info).build();
    }
}
