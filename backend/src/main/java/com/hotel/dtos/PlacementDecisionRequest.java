package com.hotel.dtos;

import jakarta.validation.constraints.Size;

public record PlacementDecisionRequest(@Size(max = 500) String reason) {
}

