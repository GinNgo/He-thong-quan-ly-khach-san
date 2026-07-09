package com.hotel.controllers;

import com.hotel.dtos.AppModuleDto;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.AppModuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/modules")
@CrossOrigin(origins = "*")
public class AppModuleController {

    @Autowired
    private AppModuleService appModuleService;

    @GetMapping
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.VIEW)
    public ResponseEntity<List<AppModuleDto>> getAllModules() {
        return ResponseEntity.ok(appModuleService.getAllModules());
    }

    @GetMapping("/{id}")
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.VIEW)
    public ResponseEntity<AppModuleDto> getModuleById(@PathVariable Long id) {
        return ResponseEntity.ok(appModuleService.getModuleById(id));
    }

    @PostMapping
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.CREATE)
    public ResponseEntity<AppModuleDto> createModule(@RequestBody AppModuleDto dto) {
        return ResponseEntity.ok(appModuleService.createModule(dto));
    }

    @PutMapping("/{id}")
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.UPDATE)
    public ResponseEntity<AppModuleDto> updateModule(@PathVariable Long id, @RequestBody AppModuleDto dto) {
        return ResponseEntity.ok(appModuleService.updateModule(id, dto));
    }

    @DeleteMapping("/{id}")
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.DELETE)
    public ResponseEntity<Void> deleteModule(@PathVariable Long id) {
        appModuleService.deleteModule(id);
        return ResponseEntity.ok().build();
    }
}
