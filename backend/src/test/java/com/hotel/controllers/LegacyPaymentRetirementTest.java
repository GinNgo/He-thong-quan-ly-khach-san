package com.hotel.controllers;

import com.hotel.dtos.PaymentDTO;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.PaymentService;
import com.hotel.services.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LegacyPaymentRetirementTest {

    @Test
    void tamperedAmountAndMethodAreNeverAcceptedByLegacyMutation() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentController controller = controller(paymentService);
        PaymentDTO tampered = new PaymentDTO();
        tampered.setReservationId(42L);
        tampered.setAmount(java.math.BigDecimal.ONE);
        tampered.setPaymentMethod("CASH");
        tampered.setTransactionId("replayed-or-tampered");

        ResponseEntity<Void> response = controller.processPayment(tampered);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getHeaders().getFirst("Deprecation")).isEqualTo("true");
        assertThat(response.getHeaders().getFirst("Link"))
                .contains("/api/reservations/{reservationId}/payment-attempts");
        verifyNoInteractions(paymentService);
    }

    @Test
    void legacyRouteStillRequiresFinanceCreatePermission() throws Exception {
        Method method = PaymentController.class.getMethod("processPayment", PaymentDTO.class);
        Permission permission = method.getAnnotation(Permission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.function()).isEqualTo(FunctionCode.FINANCE);
        assertThat(permission.action()).isEqualTo(ActionCode.CREATE);
    }

    private PaymentController controller(PaymentService paymentService) {
        try {
            Constructor<?> constructor = Arrays.stream(PaymentController.class.getDeclaredConstructors())
                    .filter(candidate -> candidate.getParameterCount() >= 3)
                    .findFirst()
                    .orElseThrow();
            Object[] arguments = Arrays.stream(constructor.getParameterTypes())
                    .map(parameterType -> {
                        if (parameterType.isAssignableFrom(PaymentService.class)) {
                            return paymentService;
                        }
                        if (parameterType.isAssignableFrom(ReservationService.class)) {
                            return mock(ReservationService.class);
                        }
                        return null;
                    })
                    .toArray();
            constructor.setAccessible(true);
            return (PaymentController) constructor.newInstance(arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to construct PaymentController for retirement test", exception);
        }
    }
}
