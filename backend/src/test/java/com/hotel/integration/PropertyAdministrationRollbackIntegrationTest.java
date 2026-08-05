package com.hotel.integration;

import com.hotel.dtos.PropertyClosureRequest;
import com.hotel.entities.Hotel;
import com.hotel.repositories.HotelRepository;
import com.hotel.services.OperationalAuditService;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.PropertyProfileMapper;
import com.hotel.services.impl.HotelManagementServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(HotelManagementServiceImpl.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PropertyAdministrationRollbackIntegrationTest {

    @Autowired private HotelRepository hotelRepository;
    @Autowired private HotelManagementServiceImpl service;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    @MockBean private PropertyAccessService propertyAccessService;
    @MockBean private PropertyProfileMapper propertyProfileMapper;
    @MockBean private OperationalAuditService operationalAuditService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackClosureAndRetainsActiveProperty() {
        Hotel hotel = new Hotel();
        hotel.setName("Rollback property");
        hotel.setNameVi("Rollback property");
        hotel.setCode("ROLLBACK-PROPERTY");
        hotel.setSlug("rollback-property");
        hotel.setAddressLine("1 Rollback Street");
        hotel.setCity("Test City");
        hotel.setCountry("Vietnam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel.setPropertyType("HOTEL");
        hotel.setIsDemo(false);
        Long id = hotelRepository.saveAndFlush(hotel).getId();
        entityManager.clear();

        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(operationalAuditService).append(any());

        assertThrows(IllegalStateException.class,
                () -> service.closeHotel(id, new PropertyClosureRequest("Rollback audit failure")));
        entityManager.clear();

        Hotel retained = hotelRepository.findById(id).orElseThrow();
        assertEquals("ACTIVE", retained.getStatus());
        assertEquals("ACTIVE", retained.getOperationStatus());
    }
}
