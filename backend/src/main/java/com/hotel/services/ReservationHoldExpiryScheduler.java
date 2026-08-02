package com.hotel.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationHoldExpiryScheduler {

    private final ReservationHoldService reservationHoldService;

    @Scheduled(fixedDelayString = "${app.reservation-hold.expiry-scan-ms:60000}")
    public void releaseExpiredHolds() {
        int released = reservationHoldService.expireDueHolds(LocalDateTime.now());
        if (released > 0) {
            log.info("Expired or reconciled {} reservation holds.", released);
        }
    }
}
