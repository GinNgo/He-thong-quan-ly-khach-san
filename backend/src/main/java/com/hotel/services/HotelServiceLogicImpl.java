package com.hotel.services;

import com.hotel.dtos.HotelServiceDTO;
import com.hotel.entities.HotelService;
import com.hotel.repositories.HotelServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HotelServiceLogicImpl implements HotelServiceLogic {

    private final HotelServiceRepository serviceRepository;

    @Override
    public List<HotelServiceDTO> getAllServices() {
        return serviceRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public HotelServiceDTO getServiceById(Long id) {
        HotelService service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));
        return mapToDTO(service);
    }

    @Override
    @Transactional
    public HotelServiceDTO createService(HotelServiceDTO dto) {
        HotelService service = new HotelService();
        mapToEntity(dto, service);
        service = serviceRepository.save(service);
        return mapToDTO(service);
    }

    @Override
    @Transactional
    public HotelServiceDTO updateService(Long id, HotelServiceDTO dto) {
        HotelService service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));
        mapToEntity(dto, service);
        service = serviceRepository.save(service);
        return mapToDTO(service);
    }

    @Override
    @Transactional
    public void deleteService(Long id) {
        HotelService service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));
        serviceRepository.delete(service);
    }

    private HotelServiceDTO mapToDTO(HotelService entity) {
        HotelServiceDTO dto = new HotelServiceDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setNameVi(entity.getNameVi());
        dto.setNameEn(entity.getNameEn());
        dto.setPrice(entity.getPrice());
        dto.setDescriptionVi(entity.getDescriptionVi());
        dto.setDescriptionEn(entity.getDescriptionEn());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private void mapToEntity(HotelServiceDTO dto, HotelService entity) {
        entity.setCode(dto.getCode());
        entity.setNameVi(dto.getNameVi());
        entity.setNameEn(dto.getNameEn());
        entity.setPrice(dto.getPrice());
        entity.setDescriptionVi(dto.getDescriptionVi());
        entity.setDescriptionEn(dto.getDescriptionEn());
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
    }
}
