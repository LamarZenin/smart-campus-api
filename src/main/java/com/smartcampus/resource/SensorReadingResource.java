package com.smartcampus.resource;

import com.smartcampus.DataStore;
import com.smartcampus.exception.SensorUnavailableException;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Part 4 - Sub-Resource for sensor readings.
 * Accessed via the sub-resource locator in SensorResource.
 * Handles /api/v1/sensors/{sensorId}/readings
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorReadingResource {

    private final Sensor sensor;
    private final DataStore store = DataStore.getInstance();

    public SensorReadingResource(Sensor sensor) {
        this.sensor = sensor;
    }

    /** GET /api/v1/sensors/{sensorId}/readings — fetch reading history */
    @GET
    public Response getReadings() {
        List<SensorReading> history = store.getReadings().getOrDefault(sensor.getId(), List.of());
        return Response.ok(history).build();
    }

    /**
     * POST /api/v1/sensors/{sensorId}/readings — append a new reading.
     * Blocked if sensor is in MAINTENANCE state (403 Forbidden).
     * Side effect: updates sensor.currentValue for data consistency.
     */
    @POST
    public Response addReading(SensorReading reading) {
        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus())) {
            throw new SensorUnavailableException(
                    "Sensor '" + sensor.getId() + "' is under MAINTENANCE and cannot accept new readings.");
        }
        if (reading.getId() == null || reading.getId().isBlank()) {
            reading = new SensorReading(reading.getValue());
        }
        store.getReadings().computeIfAbsent(sensor.getId(), k -> new java.util.ArrayList<>()).add(reading);

        // Side effect: keep parent sensor's currentValue in sync
        sensor.setCurrentValue(reading.getValue());
        store.getSensors().put(sensor.getId(), sensor);

        return Response.status(Response.Status.CREATED).entity(reading).build();
    }

    /** GET /api/v1/sensors/{sensorId}/readings/{readingId} */
    @GET
    @Path("/{readingId}")
    public Response getReading(@PathParam("readingId") String readingId) {
        List<SensorReading> history = store.getReadings().getOrDefault(sensor.getId(), List.of());
        return history.stream()
                .filter(r -> r.getId().equals(readingId))
                .findFirst()
                .map(r -> Response.ok(r).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Reading not found: " + readingId))
                        .build());
    }
}
