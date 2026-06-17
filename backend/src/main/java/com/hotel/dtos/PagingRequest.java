package com.hotel.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagingRequest {

    private int pageNumber = 1;

    private int pageSize = 20;

    private String keyword;

    private String sortField;

    private String sortDirection;
}
