package com.hotel.services;

import com.hotel.dtos.RoomDTO;
import com.hotel.dtos.RoomImageDTO;
import com.hotel.entities.Room;
import com.hotel.entities.RoomImage;
import com.hotel.entities.RoomType;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomImageRepository roomImageRepository;

    @Override
    public List<RoomDTO> getAllRooms() {
        return roomRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public RoomDTO getRoomById(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        return mapToDTO(room);
    }

    @Override
    @Transactional
    public RoomDTO createRoom(RoomDTO dto) {
        Room room = new Room();
        RoomType roomType = roomTypeRepository.findById(dto.getRoomTypeId())
                .orElseThrow(() -> new RuntimeException("RoomType not found"));
        
        mapToEntity(dto, room);
        room.setRoomType(roomType);
        room = roomRepository.save(room);
        
        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            for (RoomImageDTO imgDto : dto.getImages()) {
                RoomImage img = new RoomImage();
                img.setImageUrl(imgDto.getImageUrl());
                img.setIsPrimary(imgDto.getIsPrimary());
                img.setRoom(room);
                roomImageRepository.save(img);
            }
        }
        
        return mapToDTO(room);
    }

    @Override
    @Transactional
    public RoomDTO updateRoom(Long id, RoomDTO dto) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room not found"));
                
        RoomType roomType = roomTypeRepository.findById(dto.getRoomTypeId())
                .orElseThrow(() -> new RuntimeException("RoomType not found"));
                
        mapToEntity(dto, room);
        room.setRoomType(roomType);
        room = roomRepository.save(room);
        
        // Simplicity: we don't handle complex image updates here yet, we assume images are handled in a separate endpoint
        
        return mapToDTO(room);
    }

    @Override
    @Transactional
    public void deleteRoom(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        roomRepository.delete(room);
    }

    private RoomDTO mapToDTO(Room room) {
        RoomDTO dto = new RoomDTO();
        dto.setId(room.getId());
        dto.setRoomNumber(room.getRoomNumber());
        dto.setFloor(room.getFloor());
        dto.setStatus(room.getStatus());
        dto.setDescriptionVi(room.getDescriptionVi());
        dto.setDescriptionEn(room.getDescriptionEn());
        dto.setCreatedAt(room.getCreatedAt());
        dto.setUpdatedAt(room.getUpdatedAt());
        
        if (room.getRoomType() != null) {
            dto.setRoomTypeId(room.getRoomType().getId());
            dto.setRoomTypeCode(room.getRoomType().getCode());
            dto.setRoomTypeNameVi(room.getRoomType().getNameVi());
        }
        
        List<RoomImage> images = roomImageRepository.findByRoomId(room.getId());
        dto.setImages(images.stream().map(img -> {
            RoomImageDTO imgDto = new RoomImageDTO();
            imgDto.setId(img.getId());
            imgDto.setImageUrl(img.getImageUrl());
            imgDto.setIsPrimary(img.getIsPrimary());
            imgDto.setCreatedAt(img.getCreatedAt());
            return imgDto;
        }).collect(Collectors.toList()));
        
        return dto;
    }

    private void mapToEntity(RoomDTO dto, Room room) {
        room.setRoomNumber(dto.getRoomNumber());
        room.setFloor(dto.getFloor());
        room.setStatus(dto.getStatus());
        room.setDescriptionVi(dto.getDescriptionVi());
        room.setDescriptionEn(dto.getDescriptionEn());
    }
}
