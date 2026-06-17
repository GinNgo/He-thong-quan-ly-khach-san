package com.hotel.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagingResponse<T> {

    private List<T> items;

    private int pageNumber;

    private int pageSize;

    private long totalItems;

    private int totalPages;
}
