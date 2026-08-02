package com.hotel.services;

import com.hotel.dtos.RoomDTO;
import com.hotel.entities.Room;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoomStatePolicyTest {

    @Test
    void assignmentCheckInCheckoutAndHousekeepingFollowOneStatePath() {
        Room room = room("AVAILABLE", "CLEAN", "NONE");

        RoomStatePolicy.reserve(room);
        assertEquals("RESERVED", room.getStatus());

        RoomStatePolicy.checkIn(room);
        assertEquals("OCCUPIED", room.getStatus());

        RoomStatePolicy.checkout(room);
        assertEquals("DIRTY", room.getStatus());
        assertEquals("DIRTY", room.getHousekeepingStatus());

        RoomStatePolicy.completeHousekeeping(room);
        assertEquals("AVAILABLE", room.getStatus());
        assertEquals("CLEAN", room.getHousekeepingStatus());
    }

    @Test
    void maintenanceCommandsOwnRoomStatusAndCanReturnToDirtyState() {
        Room room = room("DIRTY", "DIRTY", "NONE");

        RoomStatePolicy.startMaintenance(room);
        assertEquals("MAINTENANCE", room.getStatus());
        assertEquals("MAINTENANCE", room.getMaintenanceStatus());

        RoomStatePolicy.completeMaintenance(room);
        assertEquals("DIRTY", room.getStatus());
        assertEquals("NONE", room.getMaintenanceStatus());
    }

    @Test
    void invalidDirectStateUpdateIsRejected() {
        Room room = room("AVAILABLE", "CLEAN", "NONE");
        RoomDTO update = new RoomDTO();
        update.setStatus("OCCUPIED");

        assertThrows(IllegalStateException.class, () -> RoomStatePolicy.requireMetadataOnlyUpdate(room, update));
    }

    @Test
    void invalidCombinationAndMaintenanceOnOccupiedRoomAreRejected() {
        Room invalid = room("AVAILABLE", "DIRTY", "NONE");
        assertThrows(IllegalStateException.class, () -> RoomStatePolicy.validate(invalid));

        Room occupied = room("OCCUPIED", "CLEAN", "NONE");
        assertThrows(IllegalStateException.class, () -> RoomStatePolicy.startMaintenance(occupied));
    }

    @Test
    void outOfServiceCannotBeReopenedThroughGenericMaintenanceCompletion() {
        Room room = room("OUT_OF_SERVICE", "CLEAN", "OUT_OF_SERVICE");
        assertThrows(IllegalStateException.class, () -> RoomStatePolicy.completeMaintenance(room));
    }

    private Room room(String status, String housekeeping, String maintenance) {
        Room room = new Room();
        room.setStatus(status);
        room.setHousekeepingStatus(housekeeping);
        room.setMaintenanceStatus(maintenance);
        return room;
    }
}
