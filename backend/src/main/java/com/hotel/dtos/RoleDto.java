package com.hotel.dtos;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RoleDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String status;
    private Boolean systemRole;
    private Long userCount;
    private String roleType;
    private LocalDateTime updatedAt;
}
