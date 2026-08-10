package com.hotel.propertycommerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.security.PermissionInterceptor;
import com.hotel.services.PropertyAccessService;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.method.HandlerMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ContextConfiguration(classes = PropertyPaymentConfigurationIntegrationTest.TestApplication.class)
@Import({PropertyPaymentConfigurationService.class, PropertyAccessService.class,
        PropertyPaymentConfigurationIntegrationTest.TestConfiguration.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:property-payment-config;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "payment.property.encryption-key=integration-test-encryption-key"
})
class PropertyPaymentConfigurationIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.hotel")
    @EnableJpaRepositories(basePackages = "com.hotel")
    static class TestApplication { }

    static class TestConfiguration {
        @Bean PaymentEnvironmentGuard paymentEnvironmentGuard() {
            return new PaymentEnvironmentGuard(true, true, false, false, false);
        }
    }

    @Autowired private PropertyPaymentConfigurationService service;
    @Autowired private PropertyPaymentConfigurationRepository repository;
    @Autowired private UserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private UserPropertyRepository userPropertyRepository;
    @Autowired private EntityManager entityManager;
    @MockBean private FinancialAuditService auditService;

    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test
    void tenantFilterAndPropertyAccessHideOtherPropertyConfiguration() {
        User owner = user("owner"); User admin = user("admin");
        Hotel first = hotel("first"); Hotel second = hotel("second");
        assign(owner, first);
        authenticate(admin, "SUPER_ADMIN"); service.update(second.getId(), request());
        authenticate(owner, "PROPERTY_OWNER"); service.update(first.getId(), request());
        assertThrows(SecurityException.class, () -> service.get(second.getId()));
        entityManager.flush(); entityManager.clear();
        entityManager.unwrap(Session.class).enableFilter("propertyPaymentConfigurationTenantFilter").setParameter("hotelId", first.getId());
        assertEquals(1, repository.findAll().size());
        assertEquals(first.getId(), repository.findAll().getFirst().getHotel().getId());
    }

    @Test
    void updateEndpointRequiresDedicatedConfigurationPermission() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor(new ObjectMapper());
        HandlerMethod handler = new HandlerMethod(new PropertyPaymentConfigurationController(service),
                PropertyPaymentConfigurationController.class.getMethod("update", Long.class,
                        PropertyPaymentConfigurationService.UpdateRequest.class));
        CustomUserDetails owner = new CustomUserDetails("owner", "password",
                List.of(new SimpleGrantedAuthority("PROPERTY_OWNER")), Map.of(FunctionCode.HOTEL, ActionCode.UPDATE),
                1L, 1L, Map.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("PUT", "/api/management/properties/1/payment-configuration"),
                new MockHttpServletResponse(), handler));

        CustomUserDetails authorized = new CustomUserDetails("owner", "password",
                List.of(new SimpleGrantedAuthority("PROPERTY_OWNER")), Map.of(FunctionCode.PROPERTY_PAYMENT_CONFIG, ActionCode.UPDATE),
                1L, 1L, Map.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(authorized, null, authorized.getAuthorities()));
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("PUT", "/api/management/properties/1/payment-configuration"),
                new MockHttpServletResponse(), handler));
    }

    private PropertyPaymentConfigurationService.UpdateRequest request() {
        return new PropertyPaymentConfigurationService.UpdateRequest(true, "SIMULATOR",
                List.of(new PropertyPaymentConfigurationService.MethodRequest("MANUAL_TRANSFER", true, "BANK", null)),
                "Test Bank", "TEST", "LUXESTAY", "0123456789", "FIXED", BigDecimal.valueOf(200000), 30,
                "BOOKING {paymentCode}", "VIETQR", "Huong dan", "Instructions");
    }

    private User user(String prefix) {
        User user = new User(); String id = prefix + "-" + UUID.randomUUID();
        user.setUsername(id); user.setEmail(id + "@example.com"); user.setPasswordHash("hash"); user.setStatus("ACTIVE");
        return userRepository.saveAndFlush(user);
    }

    private Hotel hotel(String prefix) {
        Hotel hotel = new Hotel(); hotel.setName(prefix + "-" + UUID.randomUUID()); hotel.setAddressLine("Address");
        hotel.setCity("City"); hotel.setCountry("VN"); hotel.setStatus("ACTIVE"); hotel.setOperationStatus("ACTIVE"); hotel.setApprovalStatus("APPROVED");
        return hotelRepository.saveAndFlush(hotel);
    }

    private void assign(User user, Hotel hotel) {
        UserProperty mapping = new UserProperty(); mapping.setUser(user); mapping.setHotel(hotel); mapping.setRelationshipType("OWNER"); mapping.setStatus("ACTIVE");
        userPropertyRepository.saveAndFlush(mapping);
    }

    private void authenticate(User user, String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, Set.of(new SimpleGrantedAuthority(authority))));
    }
}
