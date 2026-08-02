package com.hotel.services;

import com.hotel.dtos.HotelServiceDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.HotelService;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class HotelServiceLogicImpl implements HotelServiceLogic {

    private final HotelServiceRepository serviceRepository;
    private final PropertyAccessService propertyAccessService;

    @Override
    @Transactional(readOnly = true)
    public List<HotelServiceDTO> getAllServices(Long hotelId) {
        Hotel property = resolveTargetProperty(hotelId);
        return serviceRepository.findVisibleByHotelId(property.getId()).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HotelServiceDTO getServiceById(Long id) {
        HotelService service = findService(id);
        requireReadable(service);
        return mapToDTO(service);
    }

    @Override
    @Transactional
    public HotelServiceDTO createService(Long hotelId, HotelServiceDTO dto) {
        Hotel property = resolveTargetProperty(hotelId);
        requireTenantRequest(dto, property.getId());

        HotelService service = new HotelService();
        service.setHotel(property);
        service.setSystemService(false);
        mapToEntity(dto, service);
        ensureUniqueCode(property.getId(), service.getCode(), null);
        return mapToDTO(serviceRepository.save(service));
    }

    @Override
    @Transactional
    public HotelServiceDTO updateService(Long id, HotelServiceDTO dto) {
        HotelService service = findService(id);
        requireMutableTenantService(service);
        requireTenantRequest(dto, service.getHotel().getId());
        mapToEntity(dto, service);
        ensureUniqueCode(service.getHotel().getId(), service.getCode(), service.getId());
        return mapToDTO(serviceRepository.save(service));
    }

    @Override
    @Transactional
    public void deleteService(Long id) {
        HotelService service = findService(id);
        requireMutableTenantService(service);
        serviceRepository.delete(service);
    }

    private Hotel resolveTargetProperty(Long requestedHotelId) {
        if (requestedHotelId != null) {
            return propertyAccessService.requireManagedHotel(requestedHotelId);
        }
        if (propertyAccessService.isSystemAdministrator()) {
            throw new IllegalArgumentException("A property must be selected before managing services.");
        }
        var accessible = propertyAccessService.accessibleHotelIds();
        if (accessible.size() != 1) {
            throw new IllegalArgumentException("A property must be selected before managing services.");
        }
        return propertyAccessService.requireManagedHotel(accessible.iterator().next());
    }

    private HotelService findService(Long id) {
        if (id == null) {
            throw new ResourceNotFoundException("Service not found.");
        }
        return serviceRepository.findUnfilteredById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found."));
    }

    private void requireReadable(HotelService service) {
        if (Boolean.TRUE.equals(service.getSystemService())) {
            if (service.getHotel() != null) {
                throw new ResourceNotFoundException("Service not found.");
            }
            return;
        }
        if (service.getHotel() == null) {
            throw new ResourceNotFoundException("Service not found.");
        }
        propertyAccessService.requireAccessibleOrNotFound(service.getHotel().getId(), "service");
    }

    private void requireMutableTenantService(HotelService service) {
        requireReadable(service);
        if (Boolean.TRUE.equals(service.getSystemService())) {
            throw new IllegalStateException("System service templates are immutable.");
        }
    }

    private void requireTenantRequest(HotelServiceDTO dto, Long hotelId) {
        if (dto == null) {
            throw new IllegalArgumentException("Service payload is required.");
        }
        if (dto.getHotelId() != null && !dto.getHotelId().equals(hotelId)) {
            throw new IllegalArgumentException("The service property is server-owned and cannot be changed.");
        }
        if (Boolean.TRUE.equals(dto.getSystemService())) {
            throw new IllegalArgumentException("System service templates cannot be created or assigned to a property.");
        }
    }

    private void ensureUniqueCode(Long hotelId, String code, Long currentId) {
        String normalized = normalizeCode(code);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Service code is required.");
        }
        long matches = currentId == null
                ? serviceRepository.countByHotelIdAndCodeIgnoreCase(hotelId, normalized)
                : serviceRepository.countByHotelIdAndCodeIgnoreCaseExcludingId(hotelId, normalized, currentId);
        if (matches > 0) {
            throw new IllegalArgumentException("Service code already exists for this property.");
        }
    }

    private HotelServiceDTO mapToDTO(HotelService entity) {
        HotelServiceDTO dto = new HotelServiceDTO();
        dto.setId(entity.getId());
        dto.setHotelId(entity.getHotel() == null ? null : entity.getHotel().getId());
        dto.setCode(entity.getCode());
        dto.setNameVi(entity.getNameVi());
        dto.setNameEn(entity.getNameEn());
        dto.setPrice(entity.getPrice());
        dto.setDescriptionVi(entity.getDescriptionVi());
        dto.setDescriptionEn(entity.getDescriptionEn());
        dto.setStatus(entity.getStatus());
        dto.setSystemService(entity.getSystemService());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private void mapToEntity(HotelServiceDTO dto, HotelService entity) {
        entity.setCode(normalizeCode(dto.getCode()));
        entity.setNameVi(dto.getNameVi());
        entity.setNameEn(dto.getNameEn());
        entity.setPrice(dto.getPrice());
        entity.setDescriptionVi(dto.getDescriptionVi());
        entity.setDescriptionEn(dto.getDescriptionEn());
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
