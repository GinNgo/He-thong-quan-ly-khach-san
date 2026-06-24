package com.hotel.controllers;

import com.hotel.dtos.AppFunctionDto;
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
    public ResponseEntity<List<AppFunctionDto>> getAllFunctions() {
        return ResponseEntity.ok(appFunctionService.getAllFunctions());
    }

    @GetMapping("/module/{moduleId}")
    public ResponseEntity<List<AppFunctionDto>> getFunctionsByModuleId(@PathVariable Long moduleId) {
        return ResponseEntity.ok(appFunctionService.getFunctionsByModuleId(moduleId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppFunctionDto> getFunctionById(@PathVariable Long id) {
        return ResponseEntity.ok(appFunctionService.getFunctionById(id));
    }

    @PostMapping
    public ResponseEntity<AppFunctionDto> createFunction(@RequestBody AppFunctionDto dto) {
        return ResponseEntity.ok(appFunctionService.createFunction(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppFunctionDto> updateFunction(@PathVariable Long id, @RequestBody AppFunctionDto dto) {
        return ResponseEntity.ok(appFunctionService.updateFunction(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFunction(@PathVariable Long id) {
        appFunctionService.deleteFunction(id);
        return ResponseEntity.ok().build();
    }
}
