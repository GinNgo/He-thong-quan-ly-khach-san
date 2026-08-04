package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.RoomType;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicInventoryEligibilityPolicyTest {

    @Mock private HotelRepository hotelRepository;

    @Test
    void publicPropertyRequiresApprovedAndOperationallyActiveState() {
        PublicInventoryEligibilityPolicy policy = policy(false, "test");
        Hotel hotel = hotel(false);
        when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));

        assertDoesNotThrow(() -> policy.requirePublicProperty(10L));

        hotel.setOperationStatus("SUSPENDED");
        assertThrows(ResourceNotFoundException.class, () -> policy.requirePublicProperty(10L));

        hotel.setStatus("CLOSED");
        hotel.setOperationStatus("CLOSED");
        assertThrows(ResourceNotFoundException.class, () -> policy.requirePublicProperty(10L));
    }

    @Test
    void inconsistentLegacyStatusFailsClosedEvenWhenApprovalAndOperationLookActive() {
        PublicInventoryEligibilityPolicy policy = policy(false, "test");
        Hotel hotel = hotel(false);
        hotel.setStatus("PENDING_APPROVAL");
        when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));

        assertThrows(ResourceNotFoundException.class, () -> policy.requirePublicProperty(10L));
    }

    @Test
    void productionHidesDemoInventoryUnlessExplicitlyAllowed() {
        Hotel hotel = hotel(true);
        when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));

        assertThrows(ResourceNotFoundException.class,
                () -> policy(false, "production").requirePublicProperty(10L));
        assertDoesNotThrow(() -> policy(true, "production").requirePublicProperty(10L));
    }

    @Test
    void bookingRequiresAnActiveRoomTypeAtTheLockedRead() {
        PublicInventoryEligibilityPolicy policy = policy(false, "test");
        RoomType roomType = new RoomType();
        roomType.setHotel(hotel(false));
        roomType.setStatus("INACTIVE");

        assertThrows(IllegalStateException.class,
                () -> policy.requireSellableRoomTypeForBooking(roomType));

        roomType.setStatus("ACTIVE");
        assertDoesNotThrow(() -> policy.requireSellableRoomTypeForBooking(roomType));
    }

    private PublicInventoryEligibilityPolicy policy(boolean allowDemo, String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new PublicInventoryEligibilityPolicy(hotelRepository, environment, allowDemo);
    }

    private Hotel hotel(boolean demo) {
        Hotel hotel = new Hotel();
        hotel.setId(10L);
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel.setIsDemo(demo);
        return hotel;
    }
}
