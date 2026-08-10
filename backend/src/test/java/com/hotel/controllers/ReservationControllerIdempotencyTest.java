package com.hotel.controllers;

import com.hotel.dtos.ReservationDTO;
import com.hotel.dtos.ReservationRequest;
import com.hotel.dtos.CustomerCancellationRequest;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyService;
import com.hotel.paymentprovider.idempotency.MutationIdempotencyService;
import com.hotel.services.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationControllerIdempotencyTest {

    @Mock private ReservationService reservationService;
    @Mock private MutationIdempotencyService mutationIdempotencyService;
    @Mock private Authentication authentication;

    private ReservationController controller;

    @BeforeEach
    void setUp() {
        controller = new ReservationController(reservationService, mutationIdempotencyService);
        lenient().when(mutationIdempotencyService.execute(any(), anyInt(), eq(ReservationDTO.class), any()))
                .thenAnswer(invocation -> mutation(invocation.getArgument(3)));
        lenient().when(mutationIdempotencyService.execute(any(), anyInt(), eq(ReservationDTO.class), any(), any()))
                .thenAnswer(invocation -> mutation(invocation.getArgument(3)));
    }

    @Test
    void customerBookingUsesCallerIdentityAndStableRequestKey() {
        ReservationRequest request = bookingRequest();
        ReservationDTO created = new ReservationDTO();
        created.setId(77L);
        when(authentication.getName()).thenReturn("customer@example.test");
        when(reservationService.createReservation(
                "customer@example.test", request, "customer@example.test", "booking-key"))
                .thenReturn(created);

        var response = controller.createCustomerReservation(
                authentication, request, "booking-key", servletRequest("corr-1"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(77L, response.getBody().getId());
        FinancialIdempotencyService.BeginCommand command = capturedCreateCommand();
        assertEquals("RESERVATION_CREATE", command.operation());
        assertEquals("customer@example.test", command.scopeKey());
        assertEquals("booking-key", command.idempotencyKey());
        assertEquals("corr-1", command.correlationId());
        assertNull(command.hotelId());
    }

    @Test
    void bookingRejectsMissingIdempotencyKeyInsteadOfGeneratingAnUnreplayableIdentity() {
        when(authentication.getName()).thenReturn("customer@example.test");

        assertThrows(IllegalArgumentException.class, () -> controller.createCustomerReservation(
                authentication, bookingRequest(), "  ", servletRequest("corr-3")));

        verify(mutationIdempotencyService, never()).execute(any(), anyInt(), eq(ReservationDTO.class), any(), any());
    }

    @Test
    void cancellationScopesTheRetryIdentityToCustomerAndReservation() {
        ReservationDTO cancelled = new ReservationDTO();
        cancelled.setId(77L);
        cancelled.setStatus("CANCELLED");
        when(authentication.getName()).thenReturn("customer@example.test");
        CustomerCancellationRequest cancellation =
                new CustomerCancellationRequest("CHANGE_OF_PLANS", "Thay đổi kế hoạch");
        when(reservationService.cancelMyReservation(77L, "customer@example.test", cancellation)).thenReturn(cancelled);

        var response = controller.cancelMyReservation(
                authentication, 77L, cancellation, "cancel-key", servletRequest("corr-2"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        FinancialIdempotencyService.BeginCommand command = capturedCommand();
        assertEquals("RESERVATION_CANCEL", command.operation());
        assertEquals("customer@example.test:77", command.scopeKey());
        assertEquals("cancel-key", command.idempotencyKey());
    }

    private FinancialIdempotencyService.BeginCommand capturedCommand() {
        ArgumentCaptor<FinancialIdempotencyService.BeginCommand> captor =
                ArgumentCaptor.forClass(FinancialIdempotencyService.BeginCommand.class);
        verify(mutationIdempotencyService).execute(captor.capture(), anyInt(), eq(ReservationDTO.class), any());
        return captor.getValue();
    }

    private FinancialIdempotencyService.BeginCommand capturedCreateCommand() {
        ArgumentCaptor<FinancialIdempotencyService.BeginCommand> captor =
                ArgumentCaptor.forClass(FinancialIdempotencyService.BeginCommand.class);
        verify(mutationIdempotencyService).execute(captor.capture(), anyInt(), eq(ReservationDTO.class), any(), any());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private ReservationDTO mutation(Object supplier) {
        return ((Supplier<ReservationDTO>) supplier).get();
    }

    private MockHttpServletRequest servletRequest(String correlationId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", correlationId);
        return request;
    }

    private ReservationRequest bookingRequest() {
        ReservationRequest request = new ReservationRequest();
        request.setRoomTypeId(7L);
        request.setCheckInDate(LocalDate.of(2026, 8, 10));
        request.setCheckOutDate(LocalDate.of(2026, 8, 12));
        request.setAdults(2);
        request.setChildren(0);
        request.setQuantity(1);
        request.setPaymentMethod("PAY_AT_HOTEL");
        return request;
    }
}
