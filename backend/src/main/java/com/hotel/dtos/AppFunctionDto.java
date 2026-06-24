package com.hotel.dtos;

import lombok.Data;

@Data
public class AppFunctionDto {
    private Long id;
    private Long moduleId;
    private String code;
    private String name;
    private String url;
    private String icon;
    private Integer sortOrder;
    private Integer actionMask; // Used when fetching RolePermissions
}
