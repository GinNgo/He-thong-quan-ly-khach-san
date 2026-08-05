package com.hotel.dtos;

import java.util.List;

public record ChatPageDTO<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int number,
        int size,
        boolean first,
        boolean last,
        int retentionDays) {
}
