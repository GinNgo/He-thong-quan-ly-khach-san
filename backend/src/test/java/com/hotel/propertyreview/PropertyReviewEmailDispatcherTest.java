package com.hotel.propertyreview;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PropertyReviewEmailDispatcherTest {

    @Test
    void disabledMailerLeavesQueuedRowsPendingWithoutClaiming() {
        PropertyReviewEmailOutboxService outboxService = mock(PropertyReviewEmailOutboxService.class);
        PropertyReviewMailer mailer = mock(PropertyReviewMailer.class);
        when(mailer.isEnabled()).thenReturn(false);

        new PropertyReviewEmailDispatcher(outboxService, mailer).dispatchDue();

        verify(outboxService, never()).claimDue();
    }

    @Test
    void enabledDispatcherCompletesClaimWithDeliveryOutcome() {
        PropertyReviewEmailOutboxService outboxService = mock(PropertyReviewEmailOutboxService.class);
        PropertyReviewMailer mailer = mock(PropertyReviewMailer.class);
        var claim = new PropertyReviewEmailOutboxService.DispatchClaim(
                1L, "token", "owner@example.test", "Approved", "Property approved.");
        when(mailer.isEnabled()).thenReturn(true);
        when(outboxService.claimDue()).thenReturn(List.of(claim));
        when(mailer.send("owner@example.test", "Approved", "Property approved.")).thenReturn(true);

        new PropertyReviewEmailDispatcher(outboxService, mailer).dispatchDue();

        verify(outboxService).complete(org.mockito.ArgumentMatchers.eq(claim),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.anyLong());
    }
}
