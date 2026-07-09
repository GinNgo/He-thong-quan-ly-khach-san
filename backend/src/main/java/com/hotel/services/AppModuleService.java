package com.hotel.services;

import com.hotel.dtos.AppFunctionDto;
import com.hotel.dtos.AppModuleDto;
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
public class AppModuleService {
    @Autowired
    private AppModuleRepository appModuleRepository;

    @Autowired
    private AppFunctionRepository appFunctionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    public List<AppModuleDto> getAllModules() {
        return appModuleRepository.findAll().stream()
                .sorted(Comparator.comparing(AppModule::getId))
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public AppModuleDto getModuleById(Long id) {
        AppModule module = appModuleRepository.findById(id).orElseThrow(() -> new RuntimeException("Module not found"));
        return convertToDto(module);
    }

    public AppModuleDto createModule(AppModuleDto dto) {
        AppModule module = new AppModule();
        module.setCode(dto.getCode());
        module.setName(dto.getName());
        AppModule saved = appModuleRepository.save(module);
        return convertToDto(saved);
    }

    public AppModuleDto updateModule(Long id, AppModuleDto dto) {
        AppModule module = appModuleRepository.findById(id).orElseThrow(() -> new RuntimeException("Module not found"));
        module.setCode(dto.getCode());
        module.setName(dto.getName());
        AppModule saved = appModuleRepository.save(module);
        return convertToDto(saved);
    }

    @Transactional
    public void deleteModule(Long id) {
        List<AppFunction> functions = appFunctionRepository.findByModuleId(id);
        for (AppFunction function : functions) {
            rolePermissionRepository.deleteByFunctionId(function.getId());
            appFunctionRepository.delete(function);
        }
        appModuleRepository.deleteById(id);
    }

    private AppModuleDto convertToDto(AppModule module) {
        AppModuleDto dto = new AppModuleDto();
        dto.setId(module.getId());
        dto.setCode(module.getCode());
        dto.setName(module.getName());
        return dto;
    }
}
