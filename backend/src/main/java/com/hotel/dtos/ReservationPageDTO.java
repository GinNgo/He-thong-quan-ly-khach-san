package com.hotel.dtos;

import org.springframework.data.domain.Page;

import java.util.List;

public record ReservationPageDTO(
        List<ReservationDTO> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static ReservationPageDTO from(Page<ReservationDTO> result) {
        return new ReservationPageDTO(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
