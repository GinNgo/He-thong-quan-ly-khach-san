package com.hotel.controllers;

import com.hotel.dtos.AppModuleDto;
import com.hotel.dtos.UpdateRolePermissionsRequest;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.RolePermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/role-permissions")
@CrossOrigin(origins = "*")
public class RolePermissionController {

    @Autowired
    private RolePermissionService rolePermissionService;

    @GetMapping("/tree/{roleId}")
    @Permission(function = FunctionCode.ROLE_PERMISSION, action = ActionCode.VIEW)
    public ResponseEntity<List<AppModuleDto>> getRolePermissionsAsTree(@PathVariable Long roleId) {
        return ResponseEntity.ok(rolePermissionService.getRolePermissionsAsTree(roleId));
    }

    @PostMapping("/{roleId}")
    @Permission(function = FunctionCode.ROLE_PERMISSION, action = ActionCode.UPDATE)
    public ResponseEntity<Void> updateRolePermissions(@PathVariable Long roleId, @RequestBody UpdateRolePermissionsRequest request) {
        rolePermissionService.updateRolePermissions(roleId, request);
        return ResponseEntity.ok().build();
    }
}
