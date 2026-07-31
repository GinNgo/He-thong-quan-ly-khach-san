package com.hotel.propertycommerce.invoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.checkout.CheckoutOverride;
import com.hotel.propertycommerce.checkout.CheckoutOverrideRepository;
import com.hotel.propertycommerce.checkout.FolioCalculationService;
import com.hotel.propertycommerce.folio.ReservationChargeLineRepository;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.propertycommerce.payment.PropertyFinancialTransactionRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceFinalizationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private FolioCalculationService folioCalculationService;
    @Mock private CheckoutOverrideRepository checkoutOverrideRepository;
    @Mock private ReservationChargeLineRepository chargeLineRepository;
    @Mock private PropertyFinancialTransactionRepository transactionRepository;
    @Mock private PropertyInvoiceRepository invoiceRepository;
    @Mock private PropertyInvoiceLineRepository invoiceLineRepository;
    @Mock private PropertyInvoicePaymentAllocationRepository allocationRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private FinancialAuditService auditService;

    private InvoiceFinalizationService service;
    private Reservation reservation;
    private User staff;

    @BeforeEach
    void setUp() {
        service = new InvoiceFinalizationService(
                reservationRepository,
                folioCalculationService,
                checkoutOverrideRepository,
                chargeLineRepository,
                transactionRepository,
                invoiceRepository,
                invoiceLineRepository,
                allocationRepository,
                propertyAccessService,
                auditService,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC));
        reservation = reservation();
        staff = user(9L, "staff@example.com");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void finalizesAuthoritativeSnapshotLinesAllocationsAndAudit() {
        authenticateInvoiceCreate();
        PropertyFinancialTransaction payment = payment(31L, 1_000_000);
        arrangeBase(folio(BigDecimal.ZERO, 1_000_000), List.of(payment));

        InvoiceFinalizationService.FinalizedInvoice result = service.finalizeInvoice(
                new InvoiceFinalizationService.FinalizeInvoiceCommand(42L, null));

        PropertyInvoice invoice = result.invoice();
        assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-3-42");
        assertThat(invoice.getCustomerSnapshotJson()).contains("customer@example.com");
        assertThat(invoice.getPropertySnapshotJson()).contains("Luxe Hotel");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1000000");
        assertThat(invoice.getBalanceAmount()).isZero();
        assertThat(result.lines()).hasSize(4);
        BigDecimal lineTotal = result.lines().stream()
                .map(PropertyInvoiceLine::economicEffect)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(lineTotal).isEqualByComparingTo("1000000");
        assertThat(result.allocations()).hasSize(1);
        assertThat(result.allocations().get(0).getAllocatedAmount()).isEqualByComparingTo("1000000");

        ArgumentCaptor<FinancialAuditService.AuditCommand> auditCaptor =
                ArgumentCaptor.forClass(FinancialAuditService.AuditCommand.class);
        verify(auditService).append(auditCaptor.capture());
        assertThat(auditCaptor.getValue().source()).isEqualTo("INVOICE_FINALIZED");
        assertThat(auditCaptor.getValue().metadata().get("lineCount")).isEqualTo(4);
        assertThat(auditCaptor.getValue().metadata().get("allocationCount")).isEqualTo(1);
    }

    @Test
    void outstandingBalanceRequiresAnExactPersistedDebtOverride() {
        authenticateInvoiceCreate();
        PropertyFinancialTransaction payment = payment(31L, 800_000);
        arrangeBase(folio(BigDecimal.valueOf(200_000), 800_000), List.of(payment));

        assertThatThrownBy(() -> service.finalizeInvoice(
                new InvoiceFinalizationService.FinalizeInvoiceCommand(42L, null)))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.OUTSTANDING_BALANCE));

        CheckoutOverride override = CheckoutOverride.approveDebt(
                reservation.getHotel(), reservation, BigDecimal.valueOf(200_000), "Corporate receivable", staff);
        ReflectionTestUtils.setField(override, "id", 77L);
        when(checkoutOverrideRepository.findByIdAndHotelIdAndReservationId(77L, 3L, 42L))
                .thenReturn(Optional.of(override));

        InvoiceFinalizationService.FinalizedInvoice result = service.finalizeInvoice(
                new InvoiceFinalizationService.FinalizeInvoiceCommand(42L, 77L));

        assertThat(result.invoice().getBalanceAmount()).isEqualByComparingTo("200000");
        verify(auditService).append(any());
    }

    @Test
    void staleDebtOverrideAndOverpaymentFailClosed() {
        authenticateInvoiceCreate();
        PropertyFinancialTransaction payment = payment(31L, 800_000);
        arrangeBase(folio(BigDecimal.valueOf(200_000), 800_000), List.of(payment));
        CheckoutOverride stale = CheckoutOverride.approveDebt(
                reservation.getHotel(), reservation, BigDecimal.valueOf(150_000), "Old balance", staff);
        ReflectionTestUtils.setField(stale, "id", 77L);
        when(checkoutOverrideRepository.findByIdAndHotelIdAndReservationId(77L, 3L, 42L))
                .thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> service.finalizeInvoice(
                new InvoiceFinalizationService.FinalizeInvoiceCommand(42L, 77L)))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.CONCURRENT_MODIFICATION));

        when(folioCalculationService.calculate(42L)).thenReturn(folio(BigDecimal.valueOf(-1), 1_000_001));
        assertThatThrownBy(() -> service.finalizeInvoice(
                new InvoiceFinalizationService.FinalizeInvoiceCommand(42L, null)))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(FinancialErrorCode.OVERPAYMENT_REQUIRES_RESOLUTION));
    }

    @Test
    void retryReturnsExistingImmutableInvoiceWithoutRecalculation() {
        authenticateInvoiceCreate();
        PropertyInvoice existing = existingInvoice();
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));
        when(invoiceRepository.findByReservationIdAndStatus(42L, PropertyInvoice.Status.FINALIZED))
                .thenReturn(Optional.of(existing));
        when(invoiceLineRepository.findByInvoiceIdOrderByIdAsc(88L)).thenReturn(List.of());
        when(allocationRepository.findByInvoiceIdOrderByIdAsc(88L)).thenReturn(List.of());

        InvoiceFinalizationService.FinalizedInvoice result = service.finalizeInvoice(
                new InvoiceFinalizationService.FinalizeInvoiceCommand(42L, null));

        assertThat(result.invoice()).isSameAs(existing);
        verify(folioCalculationService, never()).calculate(any());
        verify(invoiceRepository, never()).saveAndFlush(any());
        verify(auditService, never()).append(any());
    }

    @Test
    void legacyPaymentWithoutImmutableLedgerAllocationCannotFinalize() {
        authenticateInvoiceCreate();
        arrangeBase(folio(BigDecimal.ZERO, 1_000_000), List.of());

        assertThatThrownBy(() -> service.finalizeInvoice(
                new InvoiceFinalizationService.FinalizeInvoiceCommand(42L, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ledger allocation");

        verify(allocationRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void invoiceFinalizationRequiresCreatePermissionBeforeLocking() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        authenticate(Map.of(FunctionCode.INVOICE, ActionCode.VIEW));

        assertThatThrownBy(() -> service.finalizeInvoice(
                new InvoiceFinalizationService.FinalizeInvoiceCommand(42L, null)))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.TENANT_ACCESS_DENIED));

        verify(reservationRepository, never()).findByIdForUpdate(any());
    }

    private void arrangeBase(FolioCalculationService.Folio folio, List<PropertyFinancialTransaction> transactions) {
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));
        when(propertyAccessService.currentUser()).thenReturn(staff);
        when(invoiceRepository.findByReservationIdAndStatus(42L, PropertyInvoice.Status.FINALIZED))
                .thenReturn(Optional.empty());
        when(folioCalculationService.calculate(42L)).thenReturn(folio);
        lenient().when(chargeLineRepository.findByReservationIdOrderByCreatedAtAscIdAsc(42L)).thenReturn(List.of());
        lenient().when(transactionRepository.findByReservationIdOrderByOccurredAtAsc(42L)).thenReturn(transactions);
        lenient().when(invoiceRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            PropertyInvoice invoice = invocation.getArgument(0);
            ReflectionTestUtils.setField(invoice, "id", 88L);
            return invoice;
        });
        lenient().when(invoiceLineRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(allocationRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private FolioCalculationService.Folio folio(BigDecimal balance, long successfulPayments) {
        List<FolioCalculationService.FolioLine> lines = List.of(
                line("ROOM", "ROOM", 1_000_000, 1_000_000),
                line("TAX", "TAX", 100_000, 100_000),
                line("FEE", "FEE", 50_000, 50_000),
                new FolioCalculationService.FolioLine(
                        "DISCOUNT_POLICY", 4L, "DISCOUNT", "VIP", "VIP discount", null,
                        BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(150_000),
                        BigDecimal.valueOf(150_000), BigDecimal.valueOf(-150_000), null, null));
        return new FolioCalculationService.Folio(
                42L,
                3L,
                VndMoney.of(1_000_000),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.of(100_000),
                VndMoney.of(50_000),
                VndMoney.of(150_000),
                VndMoney.of(1_000_000),
                VndMoney.of(300_000),
                VndMoney.of(successfulPayments),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.of(successfulPayments),
                balance,
                lines,
                31L,
                LocalDateTime.of(2026, 8, 1, 6, 0));
    }

    private FolioCalculationService.FolioLine line(String category, String code, long amount, long effect) {
        BigDecimal unit = category.equals("TAX") ? BigDecimal.ZERO : BigDecimal.valueOf(amount);
        BigDecimal tax = category.equals("TAX") ? BigDecimal.valueOf(amount) : BigDecimal.ZERO;
        return new FolioCalculationService.FolioLine(
                "SERVER_COMPONENT", Long.valueOf(amount), category, code, category, null,
                BigDecimal.ONE, unit, tax, BigDecimal.ZERO,
                BigDecimal.valueOf(amount), BigDecimal.valueOf(effect), null, null);
    }

    private PropertyFinancialTransaction payment(Long id, long amount) {
        PropertyFinancialTransaction transaction = PropertyFinancialTransaction.record(
                "txn-" + id,
                reservation.getHotel(),
                reservation,
                null,
                null,
                null,
                PropertyFinancialTransaction.TransactionType.ROOM_PAYMENT,
                PropertyFinancialTransaction.Direction.DEBIT,
                VndMoney.of(amount),
                "CASH",
                "INTERNAL",
                null,
                "provider-" + id,
                "effect-" + id,
                "USER",
                staff.getId(),
                "Payment",
                LocalDateTime.of(2026, 8, 1, 5, 0));
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    private PropertyInvoice existingInvoice() {
        PropertyInvoice invoice = PropertyInvoice.finalized(
                reservation.getHotel(), reservation, "INV-3-42", "{}", "{}",
                VndMoney.of(1_000_000), VndMoney.zero(), VndMoney.zero(), VndMoney.zero(),
                VndMoney.of(1_000_000), VndMoney.of(1_000_000), VndMoney.zero(), VndMoney.zero(),
                staff, LocalDateTime.of(2026, 8, 1, 6, 0));
        ReflectionTestUtils.setField(invoice, "id", 88L);
        return invoice;
    }

    private Reservation reservation() {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        hotel.setName("Luxe Hotel");
        hotel.setAddressLine("1 Beach Road");
        hotel.setCity("Da Nang");
        hotel.setCountry("VN");
        User customer = user(8L, "customer@example.com");
        Reservation value = new Reservation();
        value.setId(42L);
        value.setHotel(hotel);
        value.setUser(customer);
        value.setStatus("CHECKED_IN");
        value.setCheckInDate(LocalDate.of(2026, 8, 1));
        value.setCheckOutDate(LocalDate.of(2026, 8, 2));
        return value;
    }

    private User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName(email.startsWith("customer") ? "Customer" : "Staff");
        return user;
    }

    private void authenticateInvoiceCreate() {
        authenticate(Map.of(FunctionCode.INVOICE, ActionCode.CREATE));
    }

    private void authenticate(Map<FunctionCode, Integer> permissions) {
        CustomUserDetails principal = new CustomUserDetails(
                "staff@example.com",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")),
                permissions,
                9L,
                3L,
                Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
