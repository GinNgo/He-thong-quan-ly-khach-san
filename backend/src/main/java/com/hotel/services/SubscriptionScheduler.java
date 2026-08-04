package com.hotel.services;

import com.hotel.platformbilling.subscription.SubscriptionEntitlementRepository;
import com.hotel.platformbilling.subscription.SubscriptionLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@Slf4j
public class SubscriptionScheduler {
    private final SubscriptionEntitlementRepository entitlementRepository;
    private final SubscriptionLifecycleService lifecycleService;
    private final Clock clock;

    public SubscriptionScheduler(SubscriptionEntitlementRepository entitlementRepository,
                                 SubscriptionLifecycleService lifecycleService) {
        this(entitlementRepository, lifecycleService, Clock.systemUTC());
    }

    SubscriptionScheduler(SubscriptionEntitlementRepository entitlementRepository,
                          SubscriptionLifecycleService lifecycleService, Clock clock) {
        this.entitlementRepository = entitlementRepository;
        this.lifecycleService = lifecycleService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void checkExpiredSubscriptions() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (Long hotelId : entitlementRepository.findDueHotelIds(cutoff, PageRequest.of(0, 100))) {
            try { lifecycleService.expireIfDue(hotelId); }
            catch (RuntimeException exception) { log.warn("Subscription expiry deferred for hotel {}", hotelId, exception); }
        }
    }
}
