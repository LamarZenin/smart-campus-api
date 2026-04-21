package com.smartcampus.resource;

import com.smartcampus.DataStore;
import com.smartcampus.exception.RoomNotEmptyException;
import com.smartcampus.model.Room;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Part 2 - Room Management
 * Manages /api/v1/rooms
 */
@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomResource {

    private final DataStore store = DataStore.getInstance();

    /** GET /api/v1/rooms — list all rooms */
    @GET
    public Response getAllRooms() {
        return Response.ok(store.getRooms().values()).build();
    }

    /** POST /api/v1/rooms — create a new room */
    @POST
    public Response createRoom(Room room) {
        if (room.getId() == null || room.getId().isBlank()) {
            room.setId("ROOM-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        }
        if (room.getName() == null || room.getName().isBlank()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Room name is required.");
            return Response.status(Response.Status.BAD_REQUEST).entity(err).build();
        }
        store.getRooms().put(room.getId(), room);
        return Response.status(Response.Status.CREATED).entity(room).build();
    }

    /** GET /api/v1/rooms/{roomId} — get a specific room */
    @GET
    @Path("/{roomId}")
    public Response getRoom(@PathParam("roomId") String roomId) {
        Room room = store.getRooms().get(roomId);
        if (room == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Room not found: " + roomId);
            return Response.status(Response.Status.NOT_FOUND).entity(err).build();
        }
        return Response.ok(room).build();
    }

    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        Room room = store.getRooms().get(roomId);
        if (room == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Room not found: " + roomId);
            return Response.status(Response.Status.NOT_FOUND).entity(err).build();
        }
        if (!room.getSensorIds().isEmpty()) {
            throw new RoomNotEmptyException(
                    "Room '" + roomId + "' cannot be deleted. It still has "
                    + room.getSensorIds().size() + " sensor(s) assigned.");
        }
        store.getRooms().remove(roomId);
        Map<String, Object> msg = new HashMap<>();
        msg.put("message", "Room " + roomId + " deleted successfully.");
        return Response.ok(msg).build();
    }
}
