package com.hotel.controllers;

import com.hotel.dtos.AddServiceRequest;
import com.hotel.paymentprovider.idempotency.MutationIdempotencyService;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LegacyServiceChargeRetirementTest {

    @Test
    void legacyMutationReturnsGoneWithoutCallingReservationService() {
        ReservationService reservationService = mock(ReservationService.class);
        ReservationController controller = new ReservationController(
                reservationService,
                mock(MutationIdempotencyService.class));

        ResponseEntity<Void> response = controller.addExtraService(42L, new AddServiceRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getHeaders().getFirst("Deprecation")).isEqualTo("true");
        assertThat(response.getHeaders().getFirst("Link"))
                .contains("/api/management/reservations/42/charges/services");
        verifyNoInteractions(reservationService);
    }

    @Test
    void legacyRouteRequiresTheAuthoritativeServiceChargePermission() throws Exception {
        Method method = ReservationController.class.getMethod(
                "addExtraService", Long.class, AddServiceRequest.class);
        Permission permission = method.getAnnotation(Permission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.function()).isEqualTo(FunctionCode.RESERVATION_SERVICE);
        assertThat(permission.action()).isEqualTo(ActionCode.CREATE);
    }
}
