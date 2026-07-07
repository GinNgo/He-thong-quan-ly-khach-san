package com.hotel.services;

import com.hotel.dtos.RoomTypeDTO;
import com.hotel.entities.RoomType;
import com.hotel.repositories.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomTypeServiceImpl implements RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final com.hotel.repositories.HotelRepository hotelRepository;

    @Override
    public List<RoomTypeDTO> getAllRoomTypes() {
        return roomTypeRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<RoomTypeDTO> getRoomTypesByHotelId(Long hotelId) {
        return roomTypeRepository.findByHotelId(hotelId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public RoomTypeDTO getRoomTypeById(Long id) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room type not found"));
        return mapToDTO(roomType);
    }

    @Override
    @Transactional
    public RoomTypeDTO createRoomType(RoomTypeDTO dto) {
        RoomType roomType = new RoomType();
        mapToEntity(dto, roomType);
        
        // Temporarily set to default hotel if not provided
        com.hotel.entities.Hotel hotel = hotelRepository.findAll().stream().findFirst().orElseThrow(() -> new RuntimeException("No hotel found"));
        roomType.setHotel(hotel);

        roomType = roomTypeRepository.save(roomType);
        return mapToDTO(roomType);
    }

    @Override
    @Transactional
    public RoomTypeDTO updateRoomType(Long id, RoomTypeDTO dto) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room type not found"));
        mapToEntity(dto, roomType);
        roomType = roomTypeRepository.save(roomType);
        return mapToDTO(roomType);
    }

    @Override
    @Transactional
    public void deleteRoomType(Long id) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room type not found"));
        roomTypeRepository.delete(roomType);
    }

    private RoomTypeDTO mapToDTO(RoomType entity) {
        RoomTypeDTO dto = new RoomTypeDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setNameVi(entity.getNameVi());
        dto.setNameEn(entity.getNameEn());
        dto.setMaxGuest(entity.getMaxGuest());
        dto.setBasePrice(entity.getBasePrice());
        dto.setDescriptionVi(entity.getDescriptionVi());
        dto.setDescriptionEn(entity.getDescriptionEn());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private void mapToEntity(RoomTypeDTO dto, RoomType entity) {
        entity.setCode(dto.getCode());
        entity.setNameVi(dto.getNameVi());
        entity.setNameEn(dto.getNameEn());
        entity.setMaxGuest(dto.getMaxGuest());
        entity.setBasePrice(dto.getBasePrice());
        entity.setDescriptionVi(dto.getDescriptionVi());
        entity.setDescriptionEn(dto.getDescriptionEn());
    }
}
