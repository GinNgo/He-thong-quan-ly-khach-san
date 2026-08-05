package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotel.dtos.OperationalPolicyRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.OperationalPolicyVersion;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.OperationalPolicyRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalPolicyServiceTest {

    @Mock OperationalPolicyRepository repository;
    @Mock HotelRepository hotelRepository;
    @Mock PropertyAccessService propertyAccessService;
    OperationalPolicyService service;
    Hotel hotel;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new OperationalPolicyService(repository, hotelRepository, propertyAccessService, mapper);
        hotel = hotel(7L);
    }

    @Test
    void createsNextTenantOwnedDraft() {
        when(propertyAccessService.requireAssignedHotel(7L)).thenReturn(hotel);
        when(hotelRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(hotel));
        OperationalPolicyVersion previous = policy(1L, 3L, "PUBLISHED", LocalDateTime.now().minusDays(1));
        when(repository.findFirstByHotelIdOrderByVersionNumberDesc(7L)).thenReturn(Optional.of(previous));
        when(repository.save(any())).thenAnswer(invocation -> {
            OperationalPolicyVersion saved = invocation.getArgument(0);
            saved.setId(22L);
            return saved;
        });

        var result = service.createDraft(7L, request(LocalDateTime.now().plusDays(1)));

        assertEquals(4L, result.version());
        assertEquals("DRAFT", result.status());
        assertEquals(7L, result.hotelId());
        verify(propertyAccessService).requireAssignedHotel(7L);
    }

    @Test
    void publishingClosesThePreviousEffectiveVersion() {
        when(propertyAccessService.requireAssignedHotel(7L)).thenReturn(hotel);
        LocalDateTime cutover = LocalDateTime.now().plusDays(2);
        OperationalPolicyVersion candidate = policy(12L, 2L, "DRAFT", cutover);
        OperationalPolicyVersion current = policy(11L, 1L, "PUBLISHED", LocalDateTime.now().minusDays(2));
        when(repository.findByIdForUpdate(12L)).thenReturn(Optional.of(candidate));
        when(repository.findPublishedForUpdate(7L)).thenReturn(List.of(current));
        when(repository.save(candidate)).thenReturn(candidate);

        var result = service.publish(7L, 12L);

        assertEquals("PUBLISHED", result.status());
        assertEquals(cutover, current.getEffectiveUntil());
        verify(repository).saveAll(List.of(current));
    }

    @Test
    void publicReadLocalizesAndSnapshotKeepsThePublishedVersion() {
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel.setStatus("ACTIVE");
        OperationalPolicyVersion policy = policy(21L, 5L, "PUBLISHED", LocalDateTime.now().minusDays(1));
        policy.setCheckInEn("Check in after 14:00");
        when(hotelRepository.findById(7L)).thenReturn(Optional.of(hotel));
        when(repository.findByHotelIdAndStatusOrderByEffectiveFromDesc(7L, "PUBLISHED")).thenReturn(List.of(policy));

        var publicPolicy = service.currentPublic(7L, "en-US", LocalDate.now());
        var snapshot = service.capture(7L, LocalDate.now()).orElseThrow();

        assertEquals("Check in after 14:00", publicPolicy.checkIn());
        assertEquals(5L, snapshot.version());
        assertTrue(snapshot.json().contains("\"version\":5"));
    }

    @Test
    void crossPropertyDraftMutationIsNotDiscoverable() {
        when(propertyAccessService.requireAssignedHotel(8L)).thenReturn(hotel(8L));
        when(repository.findByIdForUpdate(12L)).thenReturn(Optional.of(policy(12L, 2L, "DRAFT", LocalDateTime.now().plusDays(1))));

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateDraft(8L, 12L, request(LocalDateTime.now().plusDays(1))));
    }

    private OperationalPolicyRequest request(LocalDateTime effectiveFrom) {
        return new OperationalPolicyRequest(effectiveFrom, "Nhận phòng sau 14:00", "Check in after 14:00",
                "Trả phòng trước 12:00", "Check out before 12:00", "Liên hệ cơ sở để biết điều kiện hủy.",
                "Contact the property for cancellation terms.", "Trẻ em theo sức chứa đã công bố.", null,
                "Không nhận thú cưng.", null, "Không hút thuốc trong phòng.", null,
                "Giữ yên lặng sau 22:00.", null);
    }

    private OperationalPolicyVersion policy(Long id, Long version, String status, LocalDateTime effectiveFrom) {
        OperationalPolicyVersion policy = new OperationalPolicyVersion();
        policy.setId(id);
        policy.setHotel(hotel);
        policy.setVersionNumber(version);
        policy.setStatus(status);
        policy.setEffectiveFrom(effectiveFrom);
        policy.setCheckInVi("Nhận phòng sau 14:00");
        policy.setCheckOutVi("Trả phòng trước 12:00");
        policy.setCancellationVi("Liên hệ cơ sở để biết điều kiện hủy.");
        policy.setChildPolicyVi("Trẻ em theo sức chứa đã công bố.");
        policy.setPetPolicyVi("Không nhận thú cưng.");
        policy.setSmokingPolicyVi("Không hút thuốc trong phòng.");
        policy.setHouseRulesVi("Giữ yên lặng sau 22:00.");
        return policy;
    }

    private Hotel hotel(Long id) {
        Hotel value = new Hotel();
        value.setId(id);
        return value;
    }
}
