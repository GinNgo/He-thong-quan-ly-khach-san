package com.hotel.services;

import com.hotel.dtos.AppFunctionDto;
import com.hotel.entities.AppFunction;
import com.hotel.entities.AppModule;
import com.hotel.repositories.AppFunctionRepository;
import com.hotel.repositories.AppModuleRepository;
import com.hotel.repositories.RolePermissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppFunctionService {
    @Autowired
    private AppFunctionRepository appFunctionRepository;

    @Autowired
    private AppModuleRepository appModuleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    public List<AppFunctionDto> getAllFunctions() {
        return appFunctionRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((AppFunction f) -> f.getModule().getId())
                        .thenComparing(f -> f.getSortOrder() != null ? f.getSortOrder() : 999)
                        .thenComparing(AppFunction::getId))
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<AppFunctionDto> getFunctionsByModuleId(Long moduleId) {
        appModuleRepository.findById(moduleId).orElseThrow(() -> new RuntimeException("Module not found"));
        return appFunctionRepository.findByModuleIdOrderBySortOrderAsc(moduleId).stream()
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

    @Transactional
    public void deleteFunction(Long id) {
        rolePermissionRepository.deleteByFunctionId(id);
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
