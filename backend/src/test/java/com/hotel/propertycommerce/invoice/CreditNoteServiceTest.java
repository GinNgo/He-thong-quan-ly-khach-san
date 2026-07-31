package com.hotel.propertycommerce.invoice;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditNoteServiceTest {

    @Mock private PropertyInvoiceRepository invoiceRepository;
    @Mock private PropertyInvoiceLineRepository invoiceLineRepository;
    @Mock private PropertyCreditNoteRepository creditNoteRepository;
    @Mock private PropertyCreditNoteLineRepository creditNoteLineRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private FinancialAuditService auditService;

    private CreditNoteService service;
    private PropertyInvoice invoice;
    private PropertyInvoiceLine roomLine;
    private User staff;

    @BeforeEach
    void setUp() {
        service = new CreditNoteService(
                invoiceRepository,
                invoiceLineRepository,
                creditNoteRepository,
                creditNoteLineRepository,
                propertyAccessService,
                auditService,
                Clock.fixed(Instant.parse("2026-08-01T01:00:00Z"), ZoneOffset.UTC));
        staff = user(9L);
        invoice = invoice(88L);
        roomLine = invoiceLine(invoice, 201L, "ROOM", 700_000);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void appendsApprovedCreditNoteWithoutRewritingFinalizedInvoice() {
        authenticate(ActionCode.APPROVE);
        arrangeBase(List.of(), List.of());
        when(creditNoteRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            PropertyCreditNote note = invocation.getArgument(0);
            ReflectionTestUtils.setField(note, "id", 301L);
            return note;
        });
        when(creditNoteLineRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreditNoteService.IssuedCreditNote result = service.issue(new CreditNoteService.IssueCreditNoteCommand(
                88L,
                "Service recovery after checkout",
                List.of(
                        new CreditNoteService.CreditLineCommand(201L, "Room correction", BigDecimal.valueOf(100_000)),
                        new CreditNoteService.CreditLineCommand(null, "Goodwill credit", BigDecimal.valueOf(50_000))),
                "corr-credit-1"));

        assertThat(result.creditNote().getCreditNoteNumber()).isEqualTo("CN-3-88-1");
        assertThat(result.creditNote().getAmount()).isEqualByComparingTo("150000");
        assertThat(result.creditNote().getApprovedBy()).isSameAs(staff);
        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines().get(0).getInvoiceLine()).isSameAs(roomLine);
        assertThat(invoice.getStatus()).isEqualTo(PropertyInvoice.Status.FINALIZED);
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1000000");
        verify(invoiceRepository, never()).save(any());

        ArgumentCaptor<FinancialAuditService.AuditCommand> auditCaptor =
                ArgumentCaptor.forClass(FinancialAuditService.AuditCommand.class);
        verify(auditService).append(auditCaptor.capture());
        assertThat(auditCaptor.getValue().source()).isEqualTo("CREDIT_NOTE_ISSUED");
        assertThat(auditCaptor.getValue().reason()).isEqualTo("Service recovery after checkout");
        assertThat(auditCaptor.getValue().metadata().get("amount")).isEqualTo(BigDecimal.valueOf(150_000));
        assertThat(auditCaptor.getValue().metadata().get("referencedLineCount")).isEqualTo(1L);
    }

    @Test
    void requiresInvoiceAdjustApproveBeforeLocking() {
        authenticate(ActionCode.VIEW);
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);

        assertThatThrownBy(() -> service.issue(command(201L, 10_000)))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.TENANT_ACCESS_DENIED));

        verify(invoiceRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void hidesLockedInvoiceFromCrossPropertyActor() {
        authenticate(ActionCode.APPROVE);
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.currentUser()).thenReturn(staff);
        when(invoiceRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(invoice));
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(99L));

        assertThatThrownBy(() -> service.issue(command(201L, 10_000)))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.RESOURCE_NOT_FOUND));

        verify(creditNoteRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsCumulativeCreditAboveInvoiceTotal() {
        authenticate(ActionCode.APPROVE);
        PropertyCreditNote existing = note(invoice, 302L, 950_000);
        arrangeBase(List.of(existing), List.of());

        assertThatThrownBy(() -> service.issue(command(null, 100_000)))
                .isInstanceOfSatisfying(FinancialException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(FinancialErrorCode.INVALID_AMOUNT);
                    assertThat(exception.getMessage()).contains("invoice total");
                });

        verify(creditNoteRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsCumulativeCreditAboveReferencedLineValue() {
        authenticate(ActionCode.APPROVE);
        PropertyCreditNote existing = note(invoice, 302L, 500_000);
        PropertyCreditNoteLine existingLine = PropertyCreditNoteLine.snapshot(
                existing, roomLine, "Earlier room correction", VndMoney.of(500_000));
        arrangeBase(List.of(existing), List.of(existingLine));

        assertThatThrownBy(() -> service.issue(command(201L, 250_000)))
                .isInstanceOfSatisfying(FinancialException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(FinancialErrorCode.INVALID_AMOUNT);
                    assertThat(exception.getMessage()).contains("invoice line");
                });

        verify(creditNoteRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUnknownLineAndFractionalVndWithoutPersistence() {
        authenticate(ActionCode.APPROVE);
        arrangeBase(List.of(), List.of());

        assertThatThrownBy(() -> service.issue(command(999L, 10_000)))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.RESOURCE_NOT_FOUND));

        CreditNoteService.IssueCreditNoteCommand fractional = new CreditNoteService.IssueCreditNoteCommand(
                88L,
                "Fractional correction",
                List.of(new CreditNoteService.CreditLineCommand(null, "Invalid amount", new BigDecimal("1.5"))),
                null);
        assertThatThrownBy(() -> service.issue(fractional))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.INVALID_AMOUNT));

        verify(creditNoteRepository, never()).saveAndFlush(any());
    }

    private void arrangeBase(
            List<PropertyCreditNote> existingNotes,
            List<PropertyCreditNoteLine> existingLines) {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.currentUser()).thenReturn(staff);
        when(invoiceRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(invoice));
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));
        when(invoiceLineRepository.findByHotelIdAndInvoiceIdOrderByIdAsc(3L, 88L))
                .thenReturn(List.of(roomLine));
        when(creditNoteRepository.findByHotelIdAndInvoiceIdOrderByIssuedAtAscIdAsc(3L, 88L))
                .thenReturn(existingNotes);
        when(creditNoteLineRepository.findByHotelIdAndInvoiceIdOrderByIdAsc(3L, 88L))
                .thenReturn(existingLines);
    }

    private CreditNoteService.IssueCreditNoteCommand command(Long invoiceLineId, long amount) {
        return new CreditNoteService.IssueCreditNoteCommand(
                88L,
                "Approved correction",
                List.of(new CreditNoteService.CreditLineCommand(
                        invoiceLineId,
                        "Correction line",
                        BigDecimal.valueOf(amount))),
                "corr-test");
    }

    private PropertyCreditNote note(PropertyInvoice target, Long id, long amount) {
        PropertyCreditNote note = PropertyCreditNote.issue(
                target,
                "CN-3-88-EXISTING-" + id,
                "Earlier correction",
                VndMoney.of(amount),
                staff,
                staff,
                LocalDateTime.of(2026, 8, 1, 0, 30));
        ReflectionTestUtils.setField(note, "id", id);
        return note;
    }

    private PropertyInvoice invoice(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        PropertyInvoice value = PropertyInvoice.finalized(
                hotel,
                reservation,
                "INV-3-42",
                "{}",
                "{}",
                VndMoney.of(1_000_000),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.of(1_000_000),
                VndMoney.of(1_000_000),
                VndMoney.zero(),
                VndMoney.zero(),
                staff,
                LocalDateTime.of(2026, 8, 1, 0, 0));
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }

    private PropertyInvoiceLine invoiceLine(PropertyInvoice target, Long id, String code, long amount) {
        PropertyInvoiceLine line = PropertyInvoiceLine.snapshot(
                target,
                PropertyInvoiceLine.LineType.ROOM,
                null,
                code,
                "Room charge",
                null,
                BigDecimal.ONE,
                VndMoney.of(amount),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.of(amount),
                null,
                null);
        ReflectionTestUtils.setField(line, "id", id);
        return line;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("staff@example.com");
        return user;
    }

    private void authenticate(int invoiceAdjustMask) {
        CustomUserDetails principal = new CustomUserDetails(
                "staff@example.com",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")),
                Map.of(FunctionCode.INVOICE_ADJUST, invoiceAdjustMask),
                9L,
                3L,
                Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
