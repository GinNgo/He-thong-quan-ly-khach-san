package com.hotel.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationHoldExpirySchedulerTest {

    @Mock
    private ReservationHoldService reservationHoldService;

    @InjectMocks
    private ReservationHoldExpiryScheduler scheduler;

    @Test
    void scheduledScanDelegatesToThePersistedHoldService() {
        when(reservationHoldService.expireDueHolds(any(LocalDateTime.class))).thenReturn(1);

        scheduler.releaseExpiredHolds();

        verify(reservationHoldService).expireDueHolds(any(LocalDateTime.class));
    }
}
