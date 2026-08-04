package com.hotel.services;

import com.hotel.dtos.HotelServiceDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.HotelService;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelServiceRepository;
import com.hotel.repositories.HotelServiceHistoryRepository;
import com.hotel.entities.HotelServiceHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HotelServiceLogicImpl implements HotelServiceLogic {

    private final HotelServiceRepository serviceRepository;
    private final PropertyAccessService propertyAccessService;
    private final HotelServiceHistoryRepository historyRepository;

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
        normalizeAndValidate(dto, null);
        mapToEntity(dto, service, "ACTIVE");
        ensureUniqueCode(property.getId(), service.getCode(), null);
        HotelService saved = serviceRepository.save(service);
        appendHistory(saved, "CREATE", "Service created");
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public HotelServiceDTO updateService(Long id, HotelServiceDTO dto) {
        HotelService service = findLockedService(id);
        requireMutableTenantService(service);
        requireTenantRequest(dto, service.getHotel().getId());
        normalizeAndValidate(dto, service);
        mapToEntity(dto, service, service.getStatus());
        ensureUniqueCode(service.getHotel().getId(), service.getCode(), service.getId());
        HotelService saved = serviceRepository.save(service);
        appendHistory(saved, "UPDATE", "Service catalog details updated");
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public void deleteService(Long id, String reason) {
        HotelService service = findLockedService(id);
        requireMutableTenantService(service);
        String normalizedReason = requireText(reason, "Deactivation reason", 1000);
        if (!"ACTIVE".equals(service.getStatus())) {
            throw new IllegalStateException("Only an active service can be deactivated.");
        }
        service.setStatus("INACTIVE");
        HotelService saved = serviceRepository.save(service);
        appendHistory(saved, "DEACTIVATE", normalizedReason);
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

    private HotelService findLockedService(Long id) {
        if (id == null) throw new ResourceNotFoundException("Service not found.");
        return serviceRepository.findByIdForUpdate(id)
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
        dto.setVersion(entity.getVersion());
        return dto;
    }

    private void mapToEntity(HotelServiceDTO dto, HotelService entity, String status) {
        entity.setCode(normalizeCode(dto.getCode()));
        entity.setNameVi(dto.getNameVi().trim());
        entity.setNameEn(dto.getNameEn().trim());
        entity.setPrice(dto.getPrice().setScale(0, RoundingMode.UNNECESSARY));
        entity.setDescriptionVi(normalizeOptional(dto.getDescriptionVi()));
        entity.setDescriptionEn(normalizeOptional(dto.getDescriptionEn()));
        entity.setStatus(status);
    }

    private void normalizeAndValidate(HotelServiceDTO dto, HotelService existing) {
        if (dto == null) throw new IllegalArgumentException("Service payload is required.");
        String code = normalizeCode(dto.getCode());
        if (code.isBlank() || code.length() > 80 || !code.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("Service code is required and may contain only letters, numbers, underscore or dash.");
        }
        requireText(dto.getNameVi(), "Vietnamese name", 255);
        requireText(dto.getNameEn(), "English name", 255);
        BigDecimal price = dto.getPrice();
        if (price == null || price.signum() <= 0 || price.scale() > 0) {
            throw new IllegalArgumentException("Service price must be a positive integer VND amount.");
        }
        String requestedStatus = dto.getStatus() == null ? (existing == null ? "ACTIVE" : existing.getStatus())
                : dto.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE").contains(requestedStatus)) {
            throw new IllegalArgumentException("Unsupported service status.");
        }
        if (existing == null && !"ACTIVE".equals(requestedStatus)) {
            throw new IllegalArgumentException("New services must be active.");
        }
        if (existing != null && !requestedStatus.equals(existing.getStatus())) {
            throw new IllegalArgumentException("Use the controlled service lifecycle action to change status.");
        }
    }

    private void appendHistory(HotelService service, String action, String reason) {
        HotelServiceHistory history = new HotelServiceHistory();
        history.setService(service);
        history.setHotelId(service.getHotel() == null ? null : service.getHotel().getId());
        history.setAction(action);
        history.setReason(reason);
        history.setCode(service.getCode());
        history.setNameVi(service.getNameVi());
        history.setNameEn(service.getNameEn());
        history.setPrice(service.getPrice());
        history.setStatus(service.getStatus());
        history.setServiceVersion(service.getVersion() == null ? 0L : service.getVersion());
        historyRepository.save(history);
    }

    private String requireText(String value, String label, int max) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(label + " is required.");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + " is too long.");
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
