package com.hotel.controllers;

import com.hotel.dtos.AppModuleDto;
import com.hotel.dtos.UpdateRolePermissionsRequest;
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
    public ResponseEntity<List<AppModuleDto>> getRolePermissionsAsTree(@PathVariable Long roleId) {
        return ResponseEntity.ok(rolePermissionService.getRolePermissionsAsTree(roleId));
    }

    @PostMapping("/{roleId}")
    public ResponseEntity<Void> updateRolePermissions(@PathVariable Long roleId, @RequestBody UpdateRolePermissionsRequest request) {
        rolePermissionService.updateRolePermissions(roleId, request);
        return ResponseEntity.ok().build();
    }
}
