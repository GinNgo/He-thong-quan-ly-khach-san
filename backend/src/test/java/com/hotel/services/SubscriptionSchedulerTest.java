package com.hotel.services;

import com.hotel.platformbilling.subscription.SubscriptionEntitlementRepository;
import com.hotel.platformbilling.subscription.SubscriptionLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionSchedulerTest {
    @Test void isolatesCandidateFailuresAndContinuesBatch() {
        SubscriptionEntitlementRepository repository = mock(SubscriptionEntitlementRepository.class);
        SubscriptionLifecycleService lifecycle = mock(SubscriptionLifecycleService.class);
        when(repository.findDueHotelIds(any(), any(Pageable.class))).thenReturn(List.of(1L, 2L, 3L));
        when(lifecycle.expireIfDue(1L)).thenThrow(new RuntimeException("locked"));
        new SubscriptionScheduler(repository, lifecycle, Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneOffset.UTC))
                .checkExpiredSubscriptions();
        verify(lifecycle).expireIfDue(1L);
        verify(lifecycle).expireIfDue(2L);
        verify(lifecycle).expireIfDue(3L);
    }
}
