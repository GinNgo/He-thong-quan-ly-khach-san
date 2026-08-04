package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.observability.OperationalMetrics;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.propertycommerce.payment.PropertyPaymentAttempt;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReceiptEmailServiceTest {

    @Mock private JavaMailSender mailSender;
    private PaymentReceiptEmailService service;

    @BeforeEach
    void setUp() {
        service = new PaymentReceiptEmailService(
                mailSender, new OperationalMetrics(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@luxestay.test");
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    @Test
    void rendersPropertyPaymentReceiptWithEscapedAuthoritativeReferences() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        User customer = org.mockito.Mockito.mock(User.class);
        Hotel hotel = org.mockito.Mockito.mock(Hotel.class);
        Reservation reservation = org.mockito.Mockito.mock(Reservation.class);
        PropertyPaymentAttempt attempt = org.mockito.Mockito.mock(PropertyPaymentAttempt.class);
        PropertyFinancialTransaction transaction = org.mockito.Mockito.mock(PropertyFinancialTransaction.class);
        when(customer.getEmail()).thenReturn("guest@example.com");
        when(customer.getFullName()).thenReturn("Nguyen <Guest>");
        when(hotel.getName()).thenReturn("Luxe <Hotel>");
        when(reservation.getId()).thenReturn(314L);
        when(reservation.getUser()).thenReturn(customer);
        when(attempt.getReservation()).thenReturn(reservation);
        when(attempt.getHotel()).thenReturn(hotel);
        when(attempt.getPurpose()).thenReturn(PropertyPaymentAttempt.Purpose.DEPOSIT);
        when(transaction.getAmount()).thenReturn(new BigDecimal("1500000"));
        when(transaction.getCurrency()).thenReturn("VND");
        when(transaction.getMethod()).thenReturn("QR");
        when(transaction.getProvider()).thenReturn("SIMULATOR");
        when(transaction.getProviderTransactionReference()).thenReturn("BANK-<314>");
        when(transaction.getPublicId()).thenReturn("property-tx-314");

        service.sendPropertyReceiptAfterCommit(attempt, transaction);

        String html = message.getContent().toString();
        assertTrue(message.getSubject().contains("Booking payment received"));
        assertTrue(html.contains("1.500.000 VND"));
        assertTrue(html.contains("Nguyen &lt;Guest&gt;"));
        assertTrue(html.contains("Luxe &lt;Hotel&gt;"));
        assertTrue(html.contains("BANK-&lt;314&gt;"));
        verify(mailSender).send(message);
    }

    @Test
    void rendersPlatformSubscriptionReceiptForTenantOwner() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        User owner = org.mockito.Mockito.mock(User.class);
        Hotel hotel = org.mockito.Mockito.mock(Hotel.class);
        SubscriptionOrder order = org.mockito.Mockito.mock(SubscriptionOrder.class);
        PlatformPaymentAttempt attempt = org.mockito.Mockito.mock(PlatformPaymentAttempt.class);
        PlatformFinancialTransaction transaction = org.mockito.Mockito.mock(PlatformFinancialTransaction.class);
        when(owner.getEmail()).thenReturn("owner@example.com");
        when(owner.getFullName()).thenReturn("Property Owner");
        when(hotel.getName()).thenReturn("LuxeStay Demo");
        when(order.getOwner()).thenReturn(owner);
        when(order.getTargetHotel()).thenReturn(hotel);
        when(order.getPlanName()).thenReturn("Business");
        when(order.getOrderCode()).thenReturn("SUB-2026-001");
        when(order.getOperation()).thenReturn(SubscriptionOrder.Operation.PURCHASE);
        when(attempt.getOrder()).thenReturn(order);
        when(transaction.getAmount()).thenReturn(new BigDecimal("2000000"));
        when(transaction.getCurrency()).thenReturn("VND");
        when(transaction.getMethod()).thenReturn("QR");
        when(transaction.getProvider()).thenReturn("SIMULATOR");
        when(transaction.getProviderTransactionReference()).thenReturn("PLATFORM-001");
        when(transaction.getPublicId()).thenReturn("platform-tx-001");

        service.sendPlatformReceiptAfterCommit(attempt, transaction);

        String html = message.getContent().toString();
        assertTrue(message.getSubject().contains("Subscription payment received"));
        assertTrue(html.contains("2.000.000 VND"));
        assertTrue(html.contains("Business - SUB-2026-001"));
        assertTrue(html.contains("platform-tx-001"));
        verify(mailSender).send(message);
    }

    @Test
    void defersReceiptUntilTransactionCommit() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        User customer = org.mockito.Mockito.mock(User.class);
        Hotel hotel = org.mockito.Mockito.mock(Hotel.class);
        Reservation reservation = org.mockito.Mockito.mock(Reservation.class);
        PropertyPaymentAttempt attempt = org.mockito.Mockito.mock(PropertyPaymentAttempt.class);
        PropertyFinancialTransaction transaction = org.mockito.Mockito.mock(PropertyFinancialTransaction.class);
        when(customer.getEmail()).thenReturn("guest@example.com");
        when(reservation.getUser()).thenReturn(customer);
        when(reservation.getId()).thenReturn(7L);
        when(attempt.getReservation()).thenReturn(reservation);
        when(attempt.getHotel()).thenReturn(hotel);
        when(attempt.getPurpose()).thenReturn(PropertyPaymentAttempt.Purpose.BALANCE);
        when(transaction.getAmount()).thenReturn(BigDecimal.ONE);
        when(transaction.getCurrency()).thenReturn("VND");
        when(transaction.getPublicId()).thenReturn("tx-7");

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.sendPropertyReceiptAfterCommit(attempt, transaction);
            verify(mailSender, never()).send(message);

            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        verify(mailSender).send(message);
    }
}
