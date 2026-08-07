package com.hotel.propertycommerce.invoice;

import com.hotel.config.SecurityConfig;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.JwtAccessDeniedHandler;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtAuthenticationEntryPoint;
import com.hotel.security.JwtTokenProvider;
import com.hotel.security.TenantFilterInterceptor;
import com.hotel.services.EmailService;
import com.hotel.services.PropertyAccessService;
import com.hotel.controllers.GlobalExceptionHandler;
import com.hotel.observability.OperationalMetrics;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PropertyInvoiceController.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class InvoiceAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PropertyInvoiceRepository invoiceRepository;
    @MockBean
    private PropertyInvoiceLineRepository lineRepository;
    @MockBean
    private PropertyInvoicePaymentAllocationRepository allocationRepository;
    @MockBean
    private PropertyCreditNoteRepository creditNoteRepository;
    @MockBean
    private PropertyCreditNoteLineRepository creditNoteLineRepository;
    @MockBean
    private CreditNoteService creditNoteService;
    @MockBean
    private PropertyInvoiceDocumentService documentService;
    @MockBean
    private PropertyAccessService propertyAccessService;
    @MockBean
    private EmailService emailService;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private EntityManagerFactory entityManagerFactory;
    @MockBean
    private TenantFilterInterceptor tenantFilterInterceptor;
    @MockBean
    private OperationalMetrics operationalMetrics;

    private PropertyInvoice invoice;
    private User customer;
    private User staff;

    @BeforeEach
    void setUp() {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        hotel.setName("Luxe Hotel");
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        customer = accountUser(8L, "customer@example.com");
        reservation.setUser(customer);
        staff = accountUser(9L, "staff@example.com");
        invoice = PropertyInvoice.finalized(
                hotel,
                reservation,
                "INV-3-42",
                "{\"email\":\"customer@example.com\"}",
                "{\"name\":\"Luxe Hotel\"}",
                com.hotel.paymentprovider.domain.VndMoney.of(1_000_000),
                com.hotel.paymentprovider.domain.VndMoney.zero(),
                com.hotel.paymentprovider.domain.VndMoney.zero(),
                com.hotel.paymentprovider.domain.VndMoney.zero(),
                com.hotel.paymentprovider.domain.VndMoney.of(1_000_000),
                com.hotel.paymentprovider.domain.VndMoney.of(1_000_000),
                com.hotel.paymentprovider.domain.VndMoney.zero(),
                com.hotel.paymentprovider.domain.VndMoney.zero(),
                staff,
                LocalDateTime.of(2026, 8, 1, 0, 0));
        ReflectionTestUtils.setField(invoice, "id", 88L);
        when(invoiceRepository.findById(88L)).thenReturn(Optional.of(invoice));
        when(lineRepository.findByHotelIdAndInvoiceIdOrderByIdAsc(3L, 88L)).thenReturn(List.of());
        when(allocationRepository.findByHotelIdAndInvoiceIdOrderByIdAsc(3L, 88L)).thenReturn(List.of());
        when(creditNoteRepository.findByHotelIdAndInvoiceIdOrderByIssuedAtAscIdAsc(3L, 88L)).thenReturn(List.of());
        when(creditNoteLineRepository.findByHotelIdAndInvoiceIdOrderByIdAsc(3L, 88L)).thenReturn(List.of());
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of());
        when(propertyAccessService.currentUser()).thenReturn(customer);
        when(tenantFilterInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void customerOwnerCanReadInvoiceSnapshot() throws Exception {
        mockMvc.perform(get("/api/invoices/88").with(user(customerDetails(customer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("INV-3-42"))
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.totalAmount").value(1_000_000));
    }

    @Test
    void customerCanListOnlyTheirFinalizedInvoiceSummaries() throws Exception {
        when(invoiceRepository.findByReservationUserIdAndStatusOrderByFinalizedAtDesc(
                customer.getId(), PropertyInvoice.Status.FINALIZED)).thenReturn(List.of(invoice));

        mockMvc.perform(get("/api/invoices/finalized/my").with(user(customerDetails(customer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(88))
                .andExpect(jsonPath("$[0].reservationId").value(42))
                .andExpect(jsonPath("$[0].invoiceNumber").value("INV-3-42"))
                .andExpect(jsonPath("$[0].status").value("FINALIZED"));
    }

    @Test
    void crossTenantCustomerCannotEnumerateInvoice() throws Exception {
        User other = accountUser(77L, "other@example.com");
        when(propertyAccessService.currentUser()).thenReturn(other);

        mockMvc.perform(get("/api/invoices/88").with(user(customerDetails(other))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void customerCanDownloadFinalizedSnapshotPdf() throws Exception {
        when(documentService.renderPdf(any(), any(), any(), any(), any()))
                .thenReturn("%PDF-1.4\n%%EOF".getBytes());

        mockMvc.perform(get("/api/invoices/88/pdf").with(user(customerDetails(customer))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"INV-3-42.pdf\""))
                .andExpect(header().exists("X-Content-SHA256"))
                .andExpect(header().exists("ETag"));
    }

    @Test
    void invoiceEmailUsesOwnerRecipientAndRejectsUnverifiedAddress() throws Exception {
        when(documentService.renderPdf(any(), any(), any(), any(), any())).thenReturn("%PDF-1.4".getBytes());
        when(emailService.sendInvoiceEmail(any(), any(), any(byte[].class))).thenReturn(true);

        mockMvc.perform(post("/api/invoices/88/email")
                        .contentType(APPLICATION_JSON)
                        .with(user(customerDetails(customer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipient").value("customer@example.com"))
                .andExpect(jsonPath("$.sent").value(true));

        mockMvc.perform(post("/api/invoices/88/email")
                        .contentType(APPLICATION_JSON)
                        .content("{\"recipient\":\"attacker@example.com\"}")
                        .with(user(customerDetails(customer))))
                .andExpect(status().isForbidden());
        verify(emailService, times(1)).sendInvoiceEmail(any(), any(), any(byte[].class));
    }

    @Test
    void authorizedPropertyStaffCanReadInvoiceWithoutBecomingCustomer() throws Exception {
        when(propertyAccessService.currentUser()).thenReturn(staff);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));

        mockMvc.perform(get("/api/invoices/88").with(user(staffDetails(staff, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(42));
        verify(invoiceRepository).findById(88L);
    }

    @Test
    void propertyStaffWithoutInvoicePermissionCannotReadInvoice() throws Exception {
        when(propertyAccessService.currentUser()).thenReturn(staff);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));

        mockMvc.perform(get("/api/invoices/88").with(user(staffDetails(staff, false))))
                .andExpect(status().isNotFound());
    }

    @Test
    void authorizedStaffListsFinalizedInvoicesForAccessibleProperties() throws Exception {
        when(propertyAccessService.currentUser()).thenReturn(staff);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));
        when(invoiceRepository.findByHotelIdInAndStatusOrderByFinalizedAtDesc(
                List.of(3L), PropertyInvoice.Status.FINALIZED)).thenReturn(List.of(invoice));

        mockMvc.perform(get("/api/management/invoices/finalized").with(user(staffDetails(staff, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invoiceNumber").value("INV-3-42"))
                .andExpect(jsonPath("$[0].customerSnapshotJson").value("{\"email\":\"customer@example.com\"}"));
    }

    private CustomUserDetails customerDetails(User user) {
        return new CustomUserDetails(
                user.getUsername(),
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")),
                new HashMap<>(),
                user.getId(),
                null,
                new HashMap<>());
    }

    private CustomUserDetails staffDetails(User user, boolean invoiceView) {
        HashMap<FunctionCode, Integer> permissions = new HashMap<>();
        if (invoiceView) {
            permissions.put(FunctionCode.INVOICE, ActionCode.VIEW);
        }
        return new CustomUserDetails(
                user.getUsername(),
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")),
                permissions,
                user.getId(),
                3L,
                new HashMap<>());
    }

    private User accountUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName(email);
        user.setRoles(new HashSet<>());
        return user;
    }
}
