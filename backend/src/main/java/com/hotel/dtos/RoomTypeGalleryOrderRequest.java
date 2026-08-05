package com.hotel.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RoomTypeGalleryOrderRequest(@NotEmpty List<@NotNull Long> imageIds) {
}
