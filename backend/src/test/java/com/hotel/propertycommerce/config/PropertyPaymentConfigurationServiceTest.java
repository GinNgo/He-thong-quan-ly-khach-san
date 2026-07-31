package com.hotel.propertycommerce.config;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PropertyPaymentConfigurationServiceTest {
    private PropertyPaymentConfigurationRepository repository;
    private PropertyAccessService accessService;
    private PropertyPaymentConfigurationService service;

    @BeforeEach
    void setUp() {
        repository = mock(PropertyPaymentConfigurationRepository.class);
        accessService = mock(PropertyAccessService.class);
        FinancialAuditService auditService = mock(FinancialAuditService.class);
        Hotel hotel = new Hotel();
        hotel.setId(7L);
        User user = new User();
        user.setId(9L);
        when(accessService.requireManagedHotel(7L)).thenReturn(hotel);
        when(accessService.currentUser()).thenReturn(user);
        when(repository.findByHotelId(7L)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            PropertyPaymentConfiguration configuration = invocation.getArgument(0);
            ReflectionTestUtils.setField(configuration, "id", 11L);
            return configuration;
        });
        service = new PropertyPaymentConfigurationService(repository, accessService,
                new PaymentEnvironmentGuard(true, true, false, false, false), auditService,
                "test-property-payment-encryption-key");
    }

    @Test
    void savesEncryptedAccountAndReturnsOnlyMaskedValue() {
        var response = service.update(7L, validRequest("SIMULATOR", "PERCENTAGE", BigDecimal.valueOf(30)));

        assertEquals("****6789", response.accountNumberMasked());
        assertTrue(response.readiness().ready());
        ArgumentCaptor<PropertyPaymentConfiguration> captor = ArgumentCaptor.forClass(PropertyPaymentConfiguration.class);
        verify(repository).saveAndFlush(captor.capture());
        assertNotEquals("0123456789", captor.getValue().getAccountNumberEncrypted());
        assertFalse(captor.getValue().getAccountNumberEncrypted().contains("0123456789"));
    }

    @Test
    void rejectsFractionalOrOutOfRangeDepositPolicy() {
        FinancialException exception = assertThrows(FinancialException.class,
                () -> service.update(7L, validRequest("SIMULATOR", "PERCENTAGE", BigDecimal.valueOf(101))));
        assertEquals(FinancialErrorCode.INVALID_AMOUNT, exception.code());
    }

    @Test
    void productionRemainsFailClosedEvenWithCompleteBankFields() {
        FinancialException exception = assertThrows(FinancialException.class,
                () -> service.update(7L, validRequest("PRODUCTION", "FIXED", BigDecimal.valueOf(200000))));
        assertEquals(FinancialErrorCode.PRODUCTION_NOT_APPROVED, exception.code());
    }

    @Test
    void sandboxProviderStaysBlockedWithoutPropertyScopedCredentials() {
        var request = new PropertyPaymentConfigurationService.UpdateRequest(
                true, "SANDBOX",
                List.of(new PropertyPaymentConfigurationService.MethodRequest("VNPAY", true, "VNPAY", "merchant-1234")),
                null, null, null, null, "NONE", null, 30,
                "BOOKING {paymentCode}", null, "Huong dan thanh toan", "Payment instructions");

        var readiness = service.validate(7L, request);

        assertFalse(readiness.ready());
        assertTrue(readiness.blockers().contains("vnpay.sandbox_credentials_incomplete"));
    }

    private PropertyPaymentConfigurationService.UpdateRequest validRequest(String environment, String policy, BigDecimal value) {
        return new PropertyPaymentConfigurationService.UpdateRequest(
                true, environment,
                List.of(new PropertyPaymentConfigurationService.MethodRequest("MANUAL_TRANSFER", true, "BANK", null)),
                "Test Bank", "TEST", "LUXESTAY HOTEL", "0123456789", policy, value, 30,
                "BOOKING {paymentCode}", "VIETQR", "Huong dan thanh toan", "Payment instructions");
    }
}
