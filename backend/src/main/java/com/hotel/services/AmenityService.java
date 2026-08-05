package com.hotel.services;

import com.hotel.dtos.AmenityAssignmentRequest;
import com.hotel.dtos.AmenityDTO;
import com.hotel.dtos.AmenityUpsertRequest;
import com.hotel.entities.Amenity;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyAmenity;
import com.hotel.entities.RoomType;
import com.hotel.entities.RoomTypeAmenity;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.AmenityRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyAmenityRepository;
import com.hotel.repositories.RoomTypeAmenityRepository;
import com.hotel.repositories.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AmenityService {

    private static final Set<String> CATEGORIES = Set.of(
            "GENERAL", "INTERNET", "PARKING", "FOOD", "WELLNESS", "ROOM", "ACCESSIBILITY");

    private final AmenityRepository amenityRepository;
    private final PropertyAmenityRepository propertyAmenityRepository;
    private final RoomTypeAmenityRepository roomTypeAmenityRepository;
    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final PropertyAccessService propertyAccessService;

    @Transactional(readOnly = true)
    public List<AmenityDTO> activeCatalog() {
        return amenityRepository.findByStatusOrderByCategoryAscSortOrderAscNameViAsc("ACTIVE")
                .stream().map(AmenityDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AmenityDTO> managementCatalog() {
        requireSystemAdministrator();
        return amenityRepository.findAllByOrderByCategoryAscSortOrderAscNameViAsc()
                .stream().map(AmenityDTO::from).toList();
    }

    @Transactional
    public AmenityDTO create(AmenityUpsertRequest request) {
        requireSystemAdministrator();
        Amenity amenity = new Amenity();
        apply(amenity, request);
        amenityRepository.findByCode(amenity.getCode()).ifPresent(existing -> {
            throw new IllegalArgumentException("Amenity code already exists.");
        });
        amenity.setStatus("ACTIVE");
        return AmenityDTO.from(amenityRepository.save(amenity));
    }

    @Transactional
    public AmenityDTO update(Long amenityId, AmenityUpsertRequest request) {
        requireSystemAdministrator();
        Amenity amenity = amenityRepository.findById(amenityId)
                .orElseThrow(() -> new ResourceNotFoundException("Amenity not found."));
        String previousCode = amenity.getCode();
        apply(amenity, request);
        if (!amenity.getCode().equals(previousCode)) {
            amenityRepository.findByCode(amenity.getCode()).ifPresent(existing -> {
                if (!existing.getId().equals(amenityId)) {
                    throw new IllegalArgumentException("Amenity code already exists.");
                }
            });
        }
        return AmenityDTO.from(amenityRepository.save(amenity));
    }

    @Transactional
    public AmenityDTO deactivate(Long amenityId) {
        requireSystemAdministrator();
        Amenity amenity = amenityRepository.findById(amenityId)
                .orElseThrow(() -> new ResourceNotFoundException("Amenity not found."));
        amenity.setStatus("INACTIVE");
        return AmenityDTO.from(amenityRepository.save(amenity));
    }

    @Transactional(readOnly = true)
    public List<AmenityDTO> propertyAmenities(Long propertyId) {
        propertyAccessService.requireAccessibleOrNotFound(propertyId, "cơ sở");
        return propertyAmenityRepository.findActiveAmenities(propertyId).stream()
                .map(AmenityDTO::from).toList();
    }

    @Transactional
    public List<AmenityDTO> replacePropertyAmenities(Long propertyId, AmenityAssignmentRequest request) {
        propertyAccessService.requireManagedHotel(propertyId);
        Hotel hotel = hotelRepository.findByIdForUpdate(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cơ sở."));
        List<Amenity> amenities = requireActiveAmenities(request.amenityIds());
        propertyAmenityRepository.deleteByHotelId(propertyId);
        List<PropertyAmenity> assignments = amenities.stream().map(amenity -> {
            PropertyAmenity assignment = new PropertyAmenity();
            assignment.setHotel(hotel);
            assignment.setAmenity(amenity);
            return assignment;
        }).toList();
        propertyAmenityRepository.saveAll(assignments);
        return amenities.stream().map(AmenityDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AmenityDTO> roomTypeAmenities(Long roomTypeId) {
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại phòng."));
        propertyAccessService.requireAccessibleOrNotFound(roomType.getHotel().getId(), "loại phòng");
        return roomTypeAmenityRepository.findActiveAmenitiesByRoomTypeId(roomTypeId).stream()
                .map(AmenityDTO::from).toList();
    }

    @Transactional
    public List<AmenityDTO> replaceRoomTypeAmenities(Long roomTypeId, AmenityAssignmentRequest request) {
        RoomType roomType = roomTypeRepository.findByIdForUpdate(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại phòng."));
        propertyAccessService.requireAccessibleOrNotFound(roomType.getHotel().getId(), "loại phòng");
        List<Amenity> amenities = requireActiveAmenities(request.amenityIds());
        roomTypeAmenityRepository.deleteByRoomTypeId(roomTypeId);
        List<RoomTypeAmenity> assignments = amenities.stream().map(amenity -> {
            RoomTypeAmenity assignment = new RoomTypeAmenity();
            assignment.setHotel(roomType.getHotel());
            assignment.setRoomType(roomType);
            assignment.setAmenity(amenity);
            return assignment;
        }).toList();
        roomTypeAmenityRepository.saveAll(assignments);
        return amenities.stream().map(AmenityDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<String> publicDisplayNames(Long hotelId) {
        Map<Long, Amenity> amenities = new LinkedHashMap<>();
        propertyAmenityRepository.findActiveAmenities(hotelId)
                .forEach(amenity -> amenities.put(amenity.getId(), amenity));
        roomTypeAmenityRepository.findActiveAmenitiesByHotelId(hotelId)
                .forEach(amenity -> amenities.putIfAbsent(amenity.getId(), amenity));
        return amenities.values().stream().map(Amenity::getNameVi).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<String>> publicDisplayNames(Set<Long> hotelIds) {
        if (hotelIds == null || hotelIds.isEmpty()) return Map.of();

        Map<Long, LinkedHashMap<Long, String>> namesByHotel = new LinkedHashMap<>();
        hotelIds.forEach(hotelId -> namesByHotel.put(hotelId, new LinkedHashMap<>()));
        mergeAmenityNames(namesByHotel, propertyAmenityRepository.findPublicAmenityNamesByHotelIds(hotelIds));

        Map<Long, List<String>> result = new LinkedHashMap<>();
        namesByHotel.forEach((hotelId, amenities) -> result.put(hotelId, List.copyOf(amenities.values())));
        return result;
    }

    private void mergeAmenityNames(Map<Long, LinkedHashMap<Long, String>> namesByHotel, List<Object[]> rows) {
        for (Object[] row : rows) {
            Long hotelId = ((Number) row[0]).longValue();
            Long amenityId = ((Number) row[1]).longValue();
            namesByHotel.computeIfAbsent(hotelId, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(amenityId, (String) row[2]);
        }
    }

    private List<Amenity> requireActiveAmenities(List<Long> requestedIds) {
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw new IllegalArgumentException("Amenity ids must be unique.");
        }
        if (uniqueIds.isEmpty()) return List.of();
        List<Amenity> found = amenityRepository.findByIdInAndStatus(uniqueIds, "ACTIVE");
        if (found.size() != uniqueIds.size()) {
            throw new ResourceNotFoundException("Amenity not found.");
        }
        Map<Long, Amenity> byId = new LinkedHashMap<>();
        found.forEach(amenity -> byId.put(amenity.getId(), amenity));
        List<Amenity> ordered = new ArrayList<>(uniqueIds.size());
        uniqueIds.forEach(id -> ordered.add(byId.get(id)));
        return ordered;
    }

    private void apply(Amenity amenity, AmenityUpsertRequest request) {
        String code = normalize(request.code()).replace('-', '_').replace(' ', '_');
        String category = normalize(request.category());
        if (!code.matches("[A-Z0-9_]{2,50}")) {
            throw new IllegalArgumentException("Amenity code must contain only A-Z, 0-9 and underscore.");
        }
        if (!CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Amenity category is not supported.");
        }
        amenity.setCode(code);
        amenity.setNameVi(required(request.nameVi(), "Vietnamese amenity name"));
        amenity.setNameEn(optional(request.nameEn()));
        amenity.setCategory(category);
        amenity.setIcon(optional(request.icon()));
        amenity.setSortOrder(request.sortOrder());
    }

    private void requireSystemAdministrator() {
        if (!propertyAccessService.isSystemAdministrator()) {
            throw new SecurityException("Only system administrators can manage the amenity catalog.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String required(String value, String field) {
        String normalized = optional(value);
        if (normalized == null) throw new IllegalArgumentException(field + " is required.");
        return normalized;
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
