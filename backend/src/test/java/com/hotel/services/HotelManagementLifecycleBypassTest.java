package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.repositories.HotelRepository;
import com.hotel.services.impl.HotelManagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelManagementLifecycleBypassTest {

    @Mock private HotelRepository hotelRepository;
    @Mock private PropertyAccessService propertyAccessService;

    private HotelManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HotelManagementServiceImpl();
        ReflectionTestUtils.setField(service, "hotelRepository", hotelRepository);
        ReflectionTestUtils.setField(service, "propertyAccessService", propertyAccessService);
    }

    @Test
    void legacyCreateCannotBypassDraftApprovalBoundary() {
        Hotel request = new Hotel();
        request.setStatus("ACTIVE");
        request.setApprovalStatus("APPROVED");
        request.setOperationStatus("ACTIVE");
        request.setLifecycleAction("REACTIVATE");
        request.setLifecycleReason("caller supplied");
        when(hotelRepository.save(request)).thenReturn(request);

        Hotel saved = service.createHotel(request);

        assertEquals("DRAFT", saved.getStatus());
        assertEquals("DRAFT", saved.getApprovalStatus());
        assertEquals("INACTIVE", saved.getOperationStatus());
        assertNull(saved.getLifecycleAction());
        assertNull(saved.getLifecycleReason());
    }

    @Test
    void legacyProfileUpdateCannotMutateLifecycleState() {
        Hotel existing = new Hotel();
        existing.setId(7L);
        existing.setStatus("SUSPENDED");
        existing.setApprovalStatus("APPROVED");
        existing.setOperationStatus("SUSPENDED");
        existing.setLifecycleAction("SUSPEND");
        existing.setLifecycleReason("Safety inspection is required.");
        Hotel request = new Hotel();
        request.setName("Updated name");
        request.setStatus("ACTIVE");
        request.setApprovalStatus("REJECTED");
        request.setOperationStatus("ACTIVE");
        when(hotelRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(hotelRepository.save(existing)).thenReturn(existing);

        Hotel saved = service.updateHotel(7L, request);

        assertEquals("Updated name", saved.getName());
        assertEquals("SUSPENDED", saved.getStatus());
        assertEquals("APPROVED", saved.getApprovalStatus());
        assertEquals("SUSPENDED", saved.getOperationStatus());
        assertEquals("SUSPEND", saved.getLifecycleAction());
        verify(propertyAccessService).requireAccessibleOrNotFound(eq(7L), anyString());
    }

    @Test
    void legacyDeleteFailsClosedWithoutRemovingHistoricalData() {
        when(hotelRepository.existsById(7L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.deleteHotel(7L));

        verify(hotelRepository, never()).deleteById(7L);
    }
}
