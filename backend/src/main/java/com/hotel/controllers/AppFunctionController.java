package com.hotel.controllers;

import com.hotel.dtos.AppFunctionDto;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.AppFunctionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/functions")
@CrossOrigin(origins = "*")
public class AppFunctionController {

    @Autowired
    private AppFunctionService appFunctionService;

    @GetMapping
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.VIEW)
    public ResponseEntity<List<AppFunctionDto>> getAllFunctions() {
        return ResponseEntity.ok(appFunctionService.getAllFunctions());
    }

    @GetMapping("/module/{moduleId}")
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.VIEW)
    public ResponseEntity<List<AppFunctionDto>> getFunctionsByModuleId(@PathVariable Long moduleId) {
        return ResponseEntity.ok(appFunctionService.getFunctionsByModuleId(moduleId));
    }

    @GetMapping("/{id}")
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.VIEW)
    public ResponseEntity<AppFunctionDto> getFunctionById(@PathVariable Long id) {
        return ResponseEntity.ok(appFunctionService.getFunctionById(id));
    }

    @PostMapping
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.CREATE)
    public ResponseEntity<AppFunctionDto> createFunction(@RequestBody AppFunctionDto dto) {
        return ResponseEntity.ok(appFunctionService.createFunction(dto));
    }

    @PutMapping("/{id}")
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.UPDATE)
    public ResponseEntity<AppFunctionDto> updateFunction(@PathVariable Long id, @RequestBody AppFunctionDto dto) {
        return ResponseEntity.ok(appFunctionService.updateFunction(id, dto));
    }

    @DeleteMapping("/{id}")
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.DELETE)
    public ResponseEntity<Void> deleteFunction(@PathVariable Long id) {
        appFunctionService.deleteFunction(id);
        return ResponseEntity.ok().build();
    }
}
