package com.hotel.services;

import com.hotel.dtos.HotelServiceDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.HotelService;
import com.hotel.exceptions.ResourceNotFoundException;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelServiceLogicImplTest {

    @Mock
    private HotelServiceRepository serviceRepository;

    @Mock
    private PropertyAccessService propertyAccessService;

    @InjectMocks
    private HotelServiceLogicImpl hotelServiceLogic;

    private Hotel firstProperty;
    private Hotel secondProperty;
    private HotelService firstService;
    private HotelServiceDTO serviceDto;

    @BeforeEach
    void setUp() {
        firstProperty = property(10L);
        secondProperty = property(20L);

        firstService = new HotelService();
        firstService.setId(1L);
        firstService.setHotel(firstProperty);
        firstService.setSystemService(false);
        firstService.setCode("BREAKFAST");
        firstService.setNameVi("Breakfast");
        firstService.setNameEn("Breakfast");
        firstService.setPrice(new BigDecimal("100000"));
        firstService.setStatus("ACTIVE");

        serviceDto = new HotelServiceDTO();
        serviceDto.setCode(" breakfast ");
        serviceDto.setNameVi("Breakfast");
        serviceDto.setNameEn("Breakfast");
        serviceDto.setPrice(new BigDecimal("100000"));
        serviceDto.setStatus("ACTIVE");
    }

    @Test
    void listUsesTheAuthorizedPropertyAndIncludesOnlyItsServicesAndSystemTemplates() {
        HotelService systemTemplate = new HotelService();
        systemTemplate.setId(2L);
        systemTemplate.setSystemService(true);
        systemTemplate.setCode("LATE_CHECKOUT");
        when(propertyAccessService.requireManagedHotel(10L)).thenReturn(firstProperty);
        when(serviceRepository.findVisibleByHotelId(10L)).thenReturn(List.of(firstService, systemTemplate));

        List<HotelServiceDTO> result = hotelServiceLogic.getAllServices(10L);

        assertEquals(List.of(1L, 2L), result.stream().map(HotelServiceDTO::getId).toList());
        assertEquals(10L, result.get(0).getHotelId());
        assertEquals(Boolean.TRUE, result.get(1).getSystemService());
        verify(serviceRepository).findVisibleByHotelId(10L);
    }

    @Test
    void missingPropertyCanOnlyBeInferredWhenExactlyOnePropertyIsAccessible() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(10L));
        when(propertyAccessService.requireManagedHotel(10L)).thenReturn(firstProperty);
        when(serviceRepository.findVisibleByHotelId(10L)).thenReturn(List.of(firstService));

        assertEquals(1, hotelServiceLogic.getAllServices(null).size());
    }

    @Test
    void createBindsTheServerAuthorizedPropertyAndRejectsClientPropertyOverride() {
        when(propertyAccessService.requireManagedHotel(10L)).thenReturn(firstProperty);
        when(serviceRepository.countByHotelIdAndCodeIgnoreCase(10L, "BREAKFAST")).thenReturn(0L);
        when(serviceRepository.save(any(HotelService.class))).thenAnswer(invocation -> {
            HotelService saved = invocation.getArgument(0);
            saved.setId(3L);
            return saved;
        });

        HotelServiceDTO result = hotelServiceLogic.createService(10L, serviceDto);

        assertEquals(3L, result.getId());
        assertEquals(10L, result.getHotelId());
        assertEquals("BREAKFAST", result.getCode());

        serviceDto.setHotelId(20L);
        assertThrows(IllegalArgumentException.class, () -> hotelServiceLogic.createService(10L, serviceDto));
        verify(serviceRepository, never()).delete(any());
    }

    @Test
    void crossPropertyReadIsHiddenAsNotFound() {
        firstService.setHotel(secondProperty);
        when(serviceRepository.findUnfilteredById(1L)).thenReturn(Optional.of(firstService));
        doThrow(new ResourceNotFoundException("Service not found."))
                .when(propertyAccessService).requireAccessibleOrNotFound(20L, "service");

        assertThrows(ResourceNotFoundException.class, () -> hotelServiceLogic.getServiceById(1L));
    }

    @Test
    void crossPropertyUpdateAndDeleteAreRejectedBeforeMutation() {
        firstService.setHotel(secondProperty);
        when(serviceRepository.findUnfilteredById(1L)).thenReturn(Optional.of(firstService));
        doThrow(new ResourceNotFoundException("Service not found."))
                .when(propertyAccessService).requireAccessibleOrNotFound(20L, "service");

        assertThrows(ResourceNotFoundException.class, () -> hotelServiceLogic.updateService(1L, serviceDto));
        assertThrows(ResourceNotFoundException.class, () -> hotelServiceLogic.deleteService(1L));
        verify(serviceRepository, never()).save(any());
        verify(serviceRepository, never()).delete(any());
    }

    @Test
    void systemTemplatesAreReadableButImmutable() {
        HotelService systemTemplate = new HotelService();
        systemTemplate.setId(2L);
        systemTemplate.setCode("LATE_CHECKOUT");
        systemTemplate.setSystemService(true);
        when(serviceRepository.findUnfilteredById(2L)).thenReturn(Optional.of(systemTemplate));

        assertNotNull(hotelServiceLogic.getServiceById(2L));
        assertThrows(IllegalStateException.class, () -> hotelServiceLogic.updateService(2L, serviceDto));
        assertThrows(IllegalStateException.class, () -> hotelServiceLogic.deleteService(2L));
        verify(serviceRepository, never()).delete(systemTemplate);
    }

    private Hotel property(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotel;
    }
}
