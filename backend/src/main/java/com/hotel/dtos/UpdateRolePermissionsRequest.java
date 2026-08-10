package com.hotel.dtos;

import lombok.Data;
import java.util.List;

@Data
public class UpdateRolePermissionsRequest {
    private Long expectedVersion;
    private String reason;
    private List<PermissionEntry> permissions;

    @Data
    public static class PermissionEntry {
        private Long functionId;
        private Integer actionMask;
    }
}
