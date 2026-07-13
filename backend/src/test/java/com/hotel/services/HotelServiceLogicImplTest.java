package com.hotel.services;

import com.hotel.dtos.HotelServiceDTO;
import com.hotel.entities.HotelService;
import com.hotel.repositories.HotelServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HotelServiceLogicImplTest {

    @Mock
    private HotelServiceRepository serviceRepository;

    @InjectMocks
    private HotelServiceLogicImpl hotelServiceLogic;

    private HotelService mockService;
    private HotelServiceDTO mockDto;

    @BeforeEach
    void setUp() {
        mockService = new HotelService();
        mockService.setId(1L);
        mockService.setCode("SVC_TEST");
        mockService.setNameVi("Dịch vụ Test");
        mockService.setPrice(new BigDecimal("100000"));
        mockService.setStatus("ACTIVE");

        mockDto = new HotelServiceDTO();
        mockDto.setCode("SVC_TEST");
        mockDto.setNameVi("Dịch vụ Test");
        mockDto.setPrice(new BigDecimal("100000"));
        mockDto.setStatus("ACTIVE");
    }

    @Test
    void getServiceById_WhenFound_ReturnsDto() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(mockService));

        HotelServiceDTO result = hotelServiceLogic.getServiceById(1L);

        assertNotNull(result);
        assertEquals(mockService.getCode(), result.getCode());
        assertEquals(mockService.getNameVi(), result.getNameVi());
    }

    @Test
    void getServiceById_WhenNotFound_ThrowsException() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> hotelServiceLogic.getServiceById(1L));
    }

    @Test
    void createService_SavesAndReturnsDto() {
        when(serviceRepository.save(any(HotelService.class))).thenReturn(mockService);

        HotelServiceDTO result = hotelServiceLogic.createService(mockDto);

        assertNotNull(result);
        assertEquals(mockDto.getCode(), result.getCode());
        verify(serviceRepository, times(1)).save(any(HotelService.class));
    }

    @Test
    void updateService_WhenFound_UpdatesAndReturnsDto() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(mockService));
        when(serviceRepository.save(any(HotelService.class))).thenReturn(mockService);

        HotelServiceDTO result = hotelServiceLogic.updateService(1L, mockDto);

        assertNotNull(result);
        verify(serviceRepository, times(1)).save(mockService);
    }

    @Test
    void updateService_WhenNotFound_ThrowsException() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> hotelServiceLogic.updateService(1L, mockDto));
        verify(serviceRepository, never()).save(any(HotelService.class));
    }

    @Test
    void deleteService_WhenFound_DeletesService() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(mockService));

        hotelServiceLogic.deleteService(1L);

        verify(serviceRepository, times(1)).delete(mockService);
    }
}
