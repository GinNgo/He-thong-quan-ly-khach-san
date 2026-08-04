package com.hotel.controllers;

import com.hotel.dtos.RoleDto;
import com.hotel.dtos.RoleCreateRequest;
import com.hotel.dtos.RoleLifecycleRequest;
import com.hotel.dtos.RoleUpdateRequest;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.RoleService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@CrossOrigin(origins = "*")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @GetMapping
    @Permission(function = FunctionCode.ROLE, action = ActionCode.VIEW)
    public ResponseEntity<List<RoleDto>> getAllRoles() {
        return ResponseEntity.ok(roleService.getAllRoles());
    }

    @GetMapping("/{id}")
    @Permission(function = FunctionCode.ROLE, action = ActionCode.VIEW)
    public ResponseEntity<RoleDto> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRoleById(id));
    }

    @PostMapping
    @Permission(function = FunctionCode.ROLE, action = ActionCode.CREATE)
    public ResponseEntity<RoleDto> createRole(@Valid @RequestBody RoleCreateRequest request) {
        return ResponseEntity.ok(roleService.createRole(request));
    }

    @PutMapping("/{id}")
    @Permission(function = FunctionCode.ROLE, action = ActionCode.UPDATE)
    public ResponseEntity<RoleDto> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request) {
        return ResponseEntity.ok(roleService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    @Permission(function = FunctionCode.ROLE, action = ActionCode.DELETE)
    public ResponseEntity<Void> deleteRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleLifecycleRequest request) {
        roleService.deactivateRole(id, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reactivate")
    @Permission(function = FunctionCode.ROLE, action = ActionCode.UPDATE)
    public ResponseEntity<RoleDto> reactivateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleLifecycleRequest request) {
        return ResponseEntity.ok(roleService.reactivateRole(id, request));
    }
}
