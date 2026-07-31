package com.hotel.propertycommerce.payment;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.adapters.PaymentProviderAdapterRegistry;
import com.hotel.paymentprovider.adapters.SimulatorPaymentProviderAdapter;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyPaymentCallbackCredentialsResolverTest {

    @Mock
    private PropertyPaymentAttemptRepository attemptRepository;

    @Test
    void resolvesSimulatorContextFromServerConfigurationAndStoredAttempt() {
        MockEnvironment properties = new MockEnvironment()
                .withProperty("payment.demo.signing-secret", "server-side-signing-secret")
                .withProperty("payment.property.simulator.merchant-id", "PROPERTY-SIMULATOR");
        PropertyPaymentCallbackCredentialsResolver resolver = resolver(properties, true);
        PropertyPaymentAttempt attempt = attempt(PaymentEnvironment.SIMULATOR);
        when(attemptRepository.findByProviderAndReference("SIMULATOR", "attempt-callback"))
                .thenReturn(Optional.of(attempt));

        PropertyPaymentCallbackCredentialsResolver.CallbackContext context = resolver.resolve(
                "simulator",
                Map.of("reference", "attempt-callback", "status", "SUCCESS"));

        assertEquals(PaymentEnvironment.SIMULATOR, context.environment());
        assertEquals("PROPERTY-SIMULATOR", context.merchantId());
        assertEquals("server-side-signing-secret", context.credentials().get("signingSecret"));
    }

    @Test
    void missingServerSecretFailsClosedEvenWhenPayloadSuppliesCredentials() {
        PropertyPaymentCallbackCredentialsResolver resolver = resolver(new MockEnvironment(), true);
        when(attemptRepository.findByProviderAndReference("SIMULATOR", "attempt-callback"))
                .thenReturn(Optional.of(attempt(PaymentEnvironment.SIMULATOR)));

        FinancialException exception = assertThrows(FinancialException.class, () -> resolver.resolve(
                "SIMULATOR",
                Map.of(
                        "reference", "attempt-callback",
                        "status", "SUCCESS",
                        "credentials", Map.of("signingSecret", "attacker-secret"))));

        assertEquals(FinancialErrorCode.PROVIDER_UNAVAILABLE, exception.code());
    }

    @Test
    void disabledSimulatorEnvironmentFailsBeforeCallbackProcessing() {
        MockEnvironment properties = new MockEnvironment()
                .withProperty("payment.demo.signing-secret", "server-side-signing-secret");
        PropertyPaymentCallbackCredentialsResolver resolver = resolver(properties, false);
        when(attemptRepository.findByProviderAndReference("SIMULATOR", "attempt-callback"))
                .thenReturn(Optional.of(attempt(PaymentEnvironment.SIMULATOR)));

        FinancialException exception = assertThrows(FinancialException.class, () -> resolver.resolve(
                "SIMULATOR",
                Map.of("reference", "attempt-callback", "status", "SUCCESS")));

        assertEquals(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED, exception.code());
    }

    private PropertyPaymentCallbackCredentialsResolver resolver(
            MockEnvironment properties,
            boolean simulatorEnabled) {
        return new PropertyPaymentCallbackCredentialsResolver(
                attemptRepository,
                new PaymentProviderAdapterRegistry(List.of(new SimulatorPaymentProviderAdapter())),
                new PaymentEnvironmentGuard(simulatorEnabled, true, false, false, false),
                properties);
    }

    private PropertyPaymentAttempt attempt(PaymentEnvironment environment) {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        User owner = new User();
        owner.setId(7L);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        reservation.setUser(owner);
        PropertyPaymentAttempt attempt = PropertyPaymentAttempt.create(
                "attempt-callback",
                hotel,
                reservation,
                null,
                owner,
                PropertyPaymentAttempt.Purpose.DEPOSIT,
                "MOMO",
                "SIMULATOR",
                environment,
                VndMoney.of(360_000),
                null,
                "{}",
                "attempt-key",
                "attempt-hash",
                LocalDateTime.now().plusMinutes(15));
        attempt.bindProviderOrderReference("attempt-callback");
        attempt.transitionTo(PaymentState.PENDING, LocalDateTime.now(), null, null);
        ReflectionTestUtils.setField(attempt, "id", 71L);
        return attempt;
    }
}
