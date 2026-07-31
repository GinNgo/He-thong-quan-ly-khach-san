package com.hotel.paymentprovider.config;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentEnvironmentGuardTest {

    @Test
    void productionIsDisabledByDefaultAndDoesNotFallback() {
        PaymentEnvironmentGuard guard = new PaymentEnvironmentGuard(true, true, false, false, false);
        FinancialException exception = assertThrows(FinancialException.class, () -> guard.validate(
                PaymentEnvironmentGuard.PaymentEnvironment.PRODUCTION, "VNPAY",
                new PaymentEnvironmentGuard.ProviderCredentials("merchant", Map.of("secret", "value"), URI.create("https://pay.example"))));
        assertEquals(FinancialErrorCode.PRODUCTION_NOT_APPROVED, exception.code());
    }

    @Test
    void sandboxRequiresCompleteCredentials() {
        PaymentEnvironmentGuard guard = new PaymentEnvironmentGuard(true, true, false, false, false);
        FinancialException exception = assertThrows(FinancialException.class, () -> guard.validate(
                PaymentEnvironmentGuard.PaymentEnvironment.SANDBOX, "ZALOPAY",
                new PaymentEnvironmentGuard.ProviderCredentials("", Map.of(), URI.create("https://sandbox.example"))));
        assertEquals(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED, exception.code());
    }

    @Test
    void simulatorReadinessIsExplicit() {
        PaymentEnvironmentGuard guard = new PaymentEnvironmentGuard(true, true, false, false, false);
        var readiness = guard.validate(PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR, "DEMO", null);
        assertTrue(readiness.ready());
        assertEquals("SIMULATOR", readiness.mode().name());
    }
}
