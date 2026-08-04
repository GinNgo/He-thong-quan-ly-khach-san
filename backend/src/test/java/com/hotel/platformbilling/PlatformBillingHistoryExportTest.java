package com.hotel.platformbilling;

import com.hotel.entities.Hotel;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.platformbilling.order.PlatformSubscriptionOrderRepository;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformPaymentAttemptRepository;
import com.hotel.platformbilling.subscription.PlatformSubscriptionHistoryRepository;
import com.hotel.platformbilling.subscription.SoftwareContract;
import com.hotel.platformbilling.subscription.SubscriptionHistory;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformBillingHistoryExportTest {
    @Test void assignedSuspendedHotelCanExportMinimalFormulaSafeHistory() {
        PlatformSubscriptionOrderRepository orders = mock(PlatformSubscriptionOrderRepository.class);
        PlatformPaymentAttemptRepository attempts = mock(PlatformPaymentAttemptRepository.class);
        PlatformSubscriptionHistoryRepository histories = mock(PlatformSubscriptionHistoryRepository.class);
        PropertyAccessService access = mock(PropertyAccessService.class);
        Hotel suspended = new Hotel(); suspended.setId(9L); suspended.setOperationStatus("SUSPENDED");
        when(access.requireAssignedHotel(9L)).thenReturn(suspended);
        SubscriptionHistory history = mock(SubscriptionHistory.class);
        SubscriptionOrder order = mock(SubscriptionOrder.class);
        SoftwareContract contract = mock(SoftwareContract.class);
        when(history.getId()).thenReturn(1L); when(history.getOrder()).thenReturn(order);
        when(order.getPublicId()).thenReturn("order-1"); when(history.getContract()).thenReturn(contract);
        when(contract.getPublicId()).thenReturn("contract-1");
        when(history.getActionType()).thenReturn(SubscriptionHistory.ActionType.REVOKED);
        when(history.getActorType()).thenReturn("USER"); when(history.getActorId()).thenReturn(77L);
        when(history.getTransaction()).thenReturn(mock(com.hotel.platformbilling.payment.PlatformFinancialTransaction.class));
        when(history.getPreviousStateJson()).thenReturn("secret-before");
        when(history.getNewStateJson()).thenReturn("secret-after");
        when(history.getReason()).thenReturn("   =HYPERLINK(\"bad\")");
        when(history.getOccurredAt()).thenReturn(LocalDateTime.of(2026, 8, 4, 12, 0));
        when(histories.findByTargetHotelIdOrderByOccurredAtDesc(9L)).thenReturn(List.of(history));
        PlatformBillingQueryService service = new PlatformBillingQueryService(orders, attempts, histories, access,
                mock(FinancialAuditService.class), Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneOffset.UTC));
        String csv = service.historyCsv(9L);
        assertTrue(csv.contains("'   =HYPERLINK"));
        assertFalse(csv.contains("secret-before")); assertFalse(csv.contains("secret-after"));
        assertFalse(csv.contains("77")); assertFalse(csv.contains("transactionPublicId"));
    }

    @Test void foreignHotelIsHiddenBeforeHistoryRead() {
        PlatformSubscriptionHistoryRepository histories = mock(PlatformSubscriptionHistoryRepository.class);
        PropertyAccessService access = mock(PropertyAccessService.class);
        doThrow(new com.hotel.exceptions.ResourceNotFoundException("not found")).when(access).requireAssignedHotel(8L);
        PlatformBillingQueryService service = new PlatformBillingQueryService(mock(PlatformSubscriptionOrderRepository.class),
                mock(PlatformPaymentAttemptRepository.class), histories, access, mock(FinancialAuditService.class),
                Clock.systemUTC());
        assertThrows(com.hotel.exceptions.ResourceNotFoundException.class, () -> service.historyCsv(8L));
        verifyNoInteractions(histories);
    }
}
