package com.hotel.services;

import com.hotel.dtos.RoomDTO;
import com.hotel.entities.Room;

import java.util.Set;

/** Central room-state transitions used by inventory, stay, checkout, and housekeeping commands. */
public final class RoomStatePolicy {
    public static final String AVAILABLE = "AVAILABLE";
    public static final String RESERVED = "RESERVED";
    public static final String OCCUPIED = "OCCUPIED";
    public static final String DIRTY = "DIRTY";
    public static final String CLEANING = "CLEANING";
    public static final String MAINTENANCE = "MAINTENANCE";
    public static final String OUT_OF_SERVICE = "OUT_OF_SERVICE";

    public static final String CLEAN = "CLEAN";
    public static final String INSPECTED = "INSPECTED";
    public static final String NONE = "NONE";

    private static final Set<String> ROOM_STATES = Set.of(
            AVAILABLE, RESERVED, OCCUPIED, DIRTY, CLEANING, MAINTENANCE, OUT_OF_SERVICE);
    private static final Set<String> HOUSEKEEPING_STATES = Set.of(CLEAN, DIRTY, CLEANING, INSPECTED);
    private static final Set<String> MAINTENANCE_STATES = Set.of(NONE, MAINTENANCE, OUT_OF_SERVICE);
    private static final Set<String> CLEAN_STATES = Set.of(CLEAN, INSPECTED);

    private RoomStatePolicy() {
    }

    public static void initialize(Room room) {
        room.setStatus(AVAILABLE);
        room.setHousekeepingStatus(CLEAN);
        room.setMaintenanceStatus(NONE);
    }

    public static void requireInitialState(RoomDTO dto) {
        if (dto == null) return;
        requireRequestedValue("status", dto.getStatus(), AVAILABLE);
        requireRequestedValue("housekeepingStatus", dto.getHousekeepingStatus(), CLEAN);
        requireRequestedValue("maintenanceStatus", dto.getMaintenanceStatus(), NONE);
    }

    public static void requireInitialStatus(String status) {
        requireRequestedValue("status", status, AVAILABLE);
    }

    public static void requireMetadataOnlyUpdate(Room room, RoomDTO dto) {
        requireRequestedValue("status", dto.getStatus(), room.getStatus());
        requireRequestedValue("housekeepingStatus", dto.getHousekeepingStatus(), room.getHousekeepingStatus());
        requireRequestedValue("maintenanceStatus", dto.getMaintenanceStatus(), room.getMaintenanceStatus());
        validate(room);
    }

    public static void reserve(Room room) {
        validate(room);
        if (!isAssignable(room)) {
            throw new IllegalStateException("Room is not clean and available for assignment.");
        }
        room.setStatus(RESERVED);
    }

    public static void checkIn(Room room) {
        validate(room);
        if (!Set.of(AVAILABLE, RESERVED).contains(room.getStatus())
                || !NONE.equals(room.getMaintenanceStatus())
                || !CLEAN_STATES.contains(room.getHousekeepingStatus())) {
            throw new IllegalStateException("Room is not clean and available for check-in.");
        }
        room.setStatus(OCCUPIED);
        room.setHousekeepingStatus(CLEAN);
    }

    public static void releaseReservation(Room room) {
        validate(room);
        if (OCCUPIED.equals(room.getStatus())) {
            throw new IllegalStateException("An occupied room must be checked out before release.");
        }
        room.setStatus(derivedOperationalStatus(room));
    }

    public static void checkout(Room room) {
        validate(room);
        if (!OCCUPIED.equals(room.getStatus()) || !NONE.equals(room.getMaintenanceStatus())) {
            throw new IllegalStateException("Only an occupied room can be checked out.");
        }
        room.setHousekeepingStatus(DIRTY);
        room.setStatus(DIRTY);
    }

    public static void startMaintenance(Room room) {
        validate(room);
        if (MAINTENANCE.equals(room.getMaintenanceStatus())) {
            return;
        }
        if (OCCUPIED.equals(room.getStatus())) {
            throw new IllegalStateException("An occupied room cannot enter maintenance.");
        }
        if (OUT_OF_SERVICE.equals(room.getMaintenanceStatus())) {
            throw new IllegalStateException("An out-of-service room cannot enter maintenance.");
        }
        room.setMaintenanceStatus(MAINTENANCE);
        room.setStatus(MAINTENANCE);
    }

    public static void completeMaintenance(Room room) {
        validate(room);
        if (!MAINTENANCE.equals(room.getMaintenanceStatus())) {
            throw new IllegalStateException("Room is not under maintenance.");
        }
        room.setMaintenanceStatus(NONE);
        room.setStatus(derivedOperationalStatus(room));
    }

    public static void deactivate(Room room) {
        validate(room);
        if (OUT_OF_SERVICE.equals(room.getMaintenanceStatus())) {
            return;
        }
        if (OCCUPIED.equals(room.getStatus())) {
            throw new IllegalStateException("An occupied room cannot be taken out of service.");
        }
        room.setMaintenanceStatus(OUT_OF_SERVICE);
        room.setStatus(OUT_OF_SERVICE);
    }

    public static void completeHousekeeping(Room room) {
        validate(room);
        if (!Set.of(DIRTY, CLEANING).contains(room.getHousekeepingStatus())) {
            throw new IllegalStateException("Room is not awaiting housekeeping completion.");
        }
        room.setHousekeepingStatus(CLEAN);
        room.setStatus(derivedOperationalStatus(room));
    }

    public static boolean isAssignable(Room room) {
        return room != null
                && AVAILABLE.equals(room.getStatus())
                && NONE.equals(room.getMaintenanceStatus())
                && CLEAN_STATES.contains(room.getHousekeepingStatus());
    }

    public static void validate(Room room) {
        String status = room.getStatus();
        String maintenance = room.getMaintenanceStatus();
        String housekeeping = room.getHousekeepingStatus();
        if (status == null || maintenance == null || housekeeping == null) {
            throw new IllegalStateException("Room state is incomplete.");
        }
        if (!ROOM_STATES.contains(status)) {
            throw new IllegalStateException("Unsupported room status: " + status);
        }
        if (!MAINTENANCE_STATES.contains(maintenance)) {
            throw new IllegalStateException("Unsupported maintenance status: " + maintenance);
        }
        if (!HOUSEKEEPING_STATES.contains(housekeeping)) {
            throw new IllegalStateException("Unsupported housekeeping status: " + housekeeping);
        }
        if (OUT_OF_SERVICE.equals(maintenance) && !OUT_OF_SERVICE.equals(status)) {
            throw new IllegalStateException("Out-of-service maintenance state must own room status.");
        }
        if (MAINTENANCE.equals(maintenance) && !MAINTENANCE.equals(status)) {
            throw new IllegalStateException("Maintenance state must own room status.");
        }
        if (NONE.equals(maintenance)) {
            if (DIRTY.equals(housekeeping) && !DIRTY.equals(status)) {
                throw new IllegalStateException("Dirty housekeeping state must own room status.");
            }
            if (CLEANING.equals(housekeeping) && !CLEANING.equals(status)) {
                throw new IllegalStateException("Cleaning housekeeping state must own room status.");
            }
            if (CLEAN_STATES.contains(housekeeping)
                    && !Set.of(AVAILABLE, RESERVED, OCCUPIED).contains(status)) {
                throw new IllegalStateException("Clean room has an unsupported operational status.");
            }
        }
    }

    private static String derivedOperationalStatus(Room room) {
        if (OUT_OF_SERVICE.equals(room.getMaintenanceStatus())) return OUT_OF_SERVICE;
        if (MAINTENANCE.equals(room.getMaintenanceStatus())) return MAINTENANCE;
        if (DIRTY.equals(room.getHousekeepingStatus())) return DIRTY;
        if (CLEANING.equals(room.getHousekeepingStatus())) return CLEANING;
        if (CLEAN_STATES.contains(room.getHousekeepingStatus())) return AVAILABLE;
        throw new IllegalStateException("Unsupported housekeeping state: " + room.getHousekeepingStatus());
    }

    private static void requireRequestedValue(String field, String requested, String expected) {
        if (requested != null && !requested.equals(expected)) {
            throw new IllegalStateException(field + " can only be changed through a room-state command.");
        }
    }
}
