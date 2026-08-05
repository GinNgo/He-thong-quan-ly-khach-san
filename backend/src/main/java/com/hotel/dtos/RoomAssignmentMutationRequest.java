package com.hotel.dtos;

import java.util.List;

public record RoomAssignmentMutationRequest(List<Long> roomIds, String reason) {
}
