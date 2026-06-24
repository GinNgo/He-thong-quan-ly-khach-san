package com.hotel.dtos;

import lombok.Data;
import java.util.List;

@Data
public class AppModuleDto {
    private Long id;
    private String code;
    private String name;
    private List<AppFunctionDto> functions;
}
