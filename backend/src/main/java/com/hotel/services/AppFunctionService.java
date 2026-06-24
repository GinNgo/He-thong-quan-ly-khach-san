package com.hotel.services;

import com.hotel.dtos.AppFunctionDto;
import com.hotel.entities.AppFunction;
import com.hotel.entities.AppModule;
import com.hotel.repositories.AppFunctionRepository;
import com.hotel.repositories.AppModuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppFunctionService {
    @Autowired
    private AppFunctionRepository appFunctionRepository;

    @Autowired
    private AppModuleRepository appModuleRepository;

    public List<AppFunctionDto> getAllFunctions() {
        return appFunctionRepository.findAll().stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public List<AppFunctionDto> getFunctionsByModuleId(Long moduleId) {
        AppModule module = appModuleRepository.findById(moduleId).orElseThrow(() -> new RuntimeException("Module not found"));
        // Assuming AppFunctionRepository has a method findByModule, but since it might not be defined, we can just filter all
        return appFunctionRepository.findAll().stream()
                .filter(f -> f.getModule().getId().equals(moduleId))
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public AppFunctionDto getFunctionById(Long id) {
        AppFunction function = appFunctionRepository.findById(id).orElseThrow(() -> new RuntimeException("Function not found"));
        return convertToDto(function);
    }

    public AppFunctionDto createFunction(AppFunctionDto dto) {
        AppFunction function = new AppFunction();
        AppModule module = appModuleRepository.findById(dto.getModuleId()).orElseThrow(() -> new RuntimeException("Module not found"));
        function.setModule(module);
        function.setCode(dto.getCode());
        function.setName(dto.getName());
        function.setUrl(dto.getUrl());
        function.setIcon(dto.getIcon());
        function.setSortOrder(dto.getSortOrder());
        AppFunction saved = appFunctionRepository.save(function);
        return convertToDto(saved);
    }

    public AppFunctionDto updateFunction(Long id, AppFunctionDto dto) {
        AppFunction function = appFunctionRepository.findById(id).orElseThrow(() -> new RuntimeException("Function not found"));
        AppModule module = appModuleRepository.findById(dto.getModuleId()).orElseThrow(() -> new RuntimeException("Module not found"));
        function.setModule(module);
        function.setCode(dto.getCode());
        function.setName(dto.getName());
        function.setUrl(dto.getUrl());
        function.setIcon(dto.getIcon());
        function.setSortOrder(dto.getSortOrder());
        AppFunction saved = appFunctionRepository.save(function);
        return convertToDto(saved);
    }

    public void deleteFunction(Long id) {
        appFunctionRepository.deleteById(id);
    }

    private AppFunctionDto convertToDto(AppFunction function) {
        AppFunctionDto dto = new AppFunctionDto();
        dto.setId(function.getId());
        dto.setModuleId(function.getModule().getId());
        dto.setCode(function.getCode());
        dto.setName(function.getName());
        dto.setUrl(function.getUrl());
        dto.setIcon(function.getIcon());
        dto.setSortOrder(function.getSortOrder());
        return dto;
    }
}
