package com.hotel.services;

import com.hotel.entities.Room;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoomAvailabilityPolicy {

    private static final List<String> CURRENT_ROOM_STATUSES = List.of("AVAILABLE");
    private static final List<String> DATED_ROOM_STATUSES = List.of("AVAILABLE", "RESERVED", "OCCUPIED");
    private static final List<String> ELIGIBLE_HOUSEKEEPING_STATUSES = List.of("CLEAN", "INSPECTED");

    public List<String> roomStatuses(boolean datedStay) {
        return datedStay ? DATED_ROOM_STATUSES : CURRENT_ROOM_STATUSES;
    }

    public List<String> housekeepingStatuses() {
        return ELIGIBLE_HOUSEKEEPING_STATUSES;
    }

    public boolean isInAvailabilityPool(Room room, boolean datedStay) {
        return room != null
                && room.getStatus() != null
                && room.getHousekeepingStatus() != null
                && roomStatuses(datedStay).contains(room.getStatus())
                && ELIGIBLE_HOUSEKEEPING_STATUSES.contains(room.getHousekeepingStatus())
                && "NONE".equals(room.getMaintenanceStatus());
    }

    public String sqlPredicate(String roomAlias, boolean datedStay) {
        String statuses = quoted(roomStatuses(datedStay));
        String housekeepingStatuses = quoted(ELIGIBLE_HOUSEKEEPING_STATUSES);
        return roomAlias + ".status IN (" + statuses + ")"
                + " AND " + roomAlias + ".housekeeping_status IN (" + housekeepingStatuses + ")"
                + " AND " + roomAlias + ".maintenance_status='NONE'";
    }

    private String quoted(List<String> values) {
        return values.stream().map(value -> "'" + value + "'").reduce((left, right) -> left + "," + right)
                .orElseThrow();
    }
}
