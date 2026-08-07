package com.hotel.services;

import com.hotel.entities.RoomType;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.RoomRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RoomAvailabilityServiceTest {

    private final RoomAvailabilityService service = new RoomAvailabilityService(
            mock(RoomRepository.class),
            mock(ReservationDetailRepository.class));

    @Test
    void canHostChecksAdultAndTotalCapacityBeforeQuoting() {
        RoomType roomType = new RoomType();
        roomType.setMaxAdults(1);
        roomType.setMaxChildren(1);
        roomType.setMaxGuests(2);

        assertFalse(service.canHost(roomType, 1, 2, 0));
        assertTrue(service.canHost(roomType, 1, 1, 1));
    }
}
