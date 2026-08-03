package com.hotel.services;

import com.hotel.dtos.PartnerRegistrationRequest;
import com.hotel.entities.Location;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.LocationRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(PropertyRegistrationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:partner-registration-rollback;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class PropertyRegistrationRollbackIntegrationTest {

    @Autowired private PropertyRegistrationService registrationService;
    @Autowired private UserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private PasswordEncoder passwordEncoder;
    @MockBean private PropertyOwnershipLifecycleService ownershipLifecycleService;

    private Long provinceId;
    private Long wardId;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            Location province = location("T230-P", "PROVINCE", "Da Nang", null);
            province = locationRepository.saveAndFlush(province);
            Location ward = location("T230-W", "WARD", "Hai Chau", province);
            ward = locationRepository.saveAndFlush(ward);
            provinceId = province.getId();
            wardId = ward.getId();
        });
    }

    @Test
    void pendingOwnerFailureRollsBackUserAndPropertyTogether() {
        when(passwordEncoder.encode("secret123")).thenReturn("hash");
        doThrow(new ForcedPendingOwnerFailure())
                .when(ownershipLifecycleService).createPendingOwner(any(), any());

        assertThatThrownBy(() -> registrationService.registerAnonymousPartner(request()))
                .isInstanceOf(ForcedPendingOwnerFailure.class);

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(userRepository.findByEmailIgnoreCase("rollback@example.com")).isEmpty();
            assertThat(hotelRepository.findAll()).isEmpty();
        });
    }

    private PartnerRegistrationRequest request() {
        PartnerRegistrationRequest request = new PartnerRegistrationRequest();
        request.setEmail("rollback@example.com");
        request.setPassword("secret123");
        request.setFullName("Rollback Owner");
        request.setPhone("0900000000");
        request.setPropertyName("Rollback Hotel");
        request.setProvinceId(provinceId);
        request.setWardId(wardId);
        request.setAddress("12 Bach Dang");
        return request;
    }

    private Location location(String code, String type, String name, Location parent) {
        Location location = new Location();
        location.setCode(code);
        location.setSourceCode(code);
        location.setNameVi(name);
        location.setLocationType(type);
        location.setParent(parent);
        location.setStatus("ACTIVE");
        return location;
    }

    private static final class ForcedPendingOwnerFailure extends RuntimeException {
    }
}
