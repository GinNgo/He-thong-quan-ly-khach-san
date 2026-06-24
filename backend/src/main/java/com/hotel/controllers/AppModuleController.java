package com.hotel.controllers;

import com.hotel.dtos.AppModuleDto;
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
    public ResponseEntity<List<AppModuleDto>> getAllModules() {
        return ResponseEntity.ok(appModuleService.getAllModules());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppModuleDto> getModuleById(@PathVariable Long id) {
        return ResponseEntity.ok(appModuleService.getModuleById(id));
    }

    @PostMapping
    public ResponseEntity<AppModuleDto> createModule(@RequestBody AppModuleDto dto) {
        return ResponseEntity.ok(appModuleService.createModule(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppModuleDto> updateModule(@PathVariable Long id, @RequestBody AppModuleDto dto) {
        return ResponseEntity.ok(appModuleService.updateModule(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteModule(@PathVariable Long id) {
        appModuleService.deleteModule(id);
        return ResponseEntity.ok().build();
    }
}
