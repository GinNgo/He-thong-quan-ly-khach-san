package com.hotel.services;

import com.hotel.entities.AccountSubscription;
import com.hotel.repositories.AccountSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {

    private final AccountSubscriptionRepository accountSubscriptionRepository;

    @Scheduled(cron = "0 0 0 * * ?") // Chạy vào lúc 00:00 mỗi ngày
    @Transactional
    public void checkExpiredSubscriptions() {
        log.info("Checking for expired subscriptions...");
        LocalDateTime now = LocalDateTime.now();
        List<AccountSubscription> activeSubscriptions = accountSubscriptionRepository.findByUserIdAndStatus(null, "ACTIVE"); // We need a custom query to get ALL active subscriptions, findByStatus would be better. Let's assume we can fetch them. Actually findByStatus doesn't exist yet, we'll write a simple loop over findAll for this mock or create the repository method.
        // Wait, AccountSubscriptionRepository doesn't have findByStatus. I'll use findAll and filter for now to avoid compilation issues, or just add findByStatus to repo.
        
        for (AccountSubscription sub : accountSubscriptionRepository.findAll()) {
            if ("ACTIVE".equals(sub.getStatus()) && !sub.getIsLifetime() && sub.getEndAt() != null) {
                if (sub.getEndAt().isBefore(now)) {
                    sub.setStatus("EXPIRED");
                    accountSubscriptionRepository.save(sub);
                    log.info("Subscription {} for user {} has expired.", sub.getId(), sub.getUser().getUsername());
                }
            }
        }
    }
}
