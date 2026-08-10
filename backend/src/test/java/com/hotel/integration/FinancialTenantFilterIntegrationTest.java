package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.paymentprovider.audit.FinancialAuditEventRepository;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ContextConfiguration(classes = FinancialTenantFilterIntegrationTest.TestApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:financial-tenant;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class FinancialTenantFilterIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.hotel")
    @EnableJpaRepositories(basePackages = "com.hotel")
    static class TestApplication {
    }

    @Autowired private EntityManager entityManager;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private FinancialAuditEventRepository auditRepository;

    @Test
    void hibernateFiltersHideFinancialAndOperationalRowsFromOtherProperties() {
        Hotel first = hotel("first");
        Hotel second = hotel("second");
        room(first, "101");
        room(second, "201");
        FinancialAuditService auditService = new FinancialAuditService(auditRepository, new ObjectMapper());
        auditService.append(audit(first.getId(), "attempt-a"));
        auditService.append(audit(second.getId(), "attempt-b"));
        entityManager.flush();
        entityManager.clear();

        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("roomTenantFilter").setParameter("hotelId", first.getId());
        session.enableFilter("financialAuditTenantFilter").setParameter("hotelId", first.getId());

        assertEquals(1, roomRepository.findAll().size());
        assertEquals("101", roomRepository.findAll().getFirst().getRoomNumber());
        assertEquals(1, auditRepository.findAll().size());
        assertEquals(first.getId(), auditRepository.findAll().getFirst().getHotelId());
    }

    private Hotel hotel(String prefix) {
        Hotel hotel = new Hotel();
        hotel.setName(prefix + "-" + UUID.randomUUID());
        hotel.setAddressLine("Address");
        hotel.setCity("City");
        hotel.setCountry("VN");
        hotel.setStatus("ACTIVE");
        hotel.setOperationStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        return hotelRepository.saveAndFlush(hotel);
    }

    private void room(Hotel hotel, String number) {
        RoomType type = new RoomType();
        type.setHotel(hotel);
        type.setCode("TYPE-" + number);
        type.setNameVi("Room type " + number);
        type.setNameEn("Room type " + number);
        type.setBasePrice(BigDecimal.valueOf(500000));
        type.setStatus("ACTIVE");
        type = roomTypeRepository.saveAndFlush(type);
        Room room = new Room();
        room.setHotel(hotel);
        room.setRoomType(type);
        room.setRoomNumber(number);
        room.setFloor(1);
        room.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(room);
    }

    private FinancialAuditService.AuditCommand audit(Long hotelId, String aggregateId) {
        return new FinancialAuditService.AuditCommand(
                "PROPERTY_COMMERCE", hotelId, "PAYMENT_ATTEMPT", aggregateId, "SYSTEM", null,
                "TEST", null, "CREATED", null, aggregateId, null, "corr-" + aggregateId, Map.of());
    }
}
