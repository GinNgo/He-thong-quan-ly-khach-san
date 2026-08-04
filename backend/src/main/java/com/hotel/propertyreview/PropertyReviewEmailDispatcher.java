package com.hotel.propertyreview;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PropertyReviewEmailDispatcher {

    private final PropertyReviewEmailOutboxService outboxService;
    private final PropertyReviewMailer mailer;

    public PropertyReviewEmailDispatcher(
            PropertyReviewEmailOutboxService outboxService,
            PropertyReviewMailer mailer) {
        this.outboxService = outboxService;
        this.mailer = mailer;
    }

    @Scheduled(fixedDelayString = "${app.mail.property-review.scan-ms:30000}")
    public void dispatchDue() {
        if (!mailer.isEnabled()) {
            return;
        }
        for (PropertyReviewEmailOutboxService.DispatchClaim claim : outboxService.claimDue()) {
            long startedAt = System.nanoTime();
            boolean delivered;
            try {
                delivered = mailer.send(
                        claim.recipientEmail(), claim.subject(), claim.bodyText());
            } catch (RuntimeException unexpectedFailure) {
                delivered = false;
            }
            long durationMs = java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            outboxService.complete(claim, delivered, durationMs);
        }
    }
}
