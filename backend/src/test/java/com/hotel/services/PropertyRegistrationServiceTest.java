package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.repositories.AccountSubscriptionRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.SubscriptionPlanRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class PropertyRegistrationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private HotelRepository hotelRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private AccountSubscriptionRepository accountSubscriptionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PropertyOwnershipLifecycleService ownershipLifecycleService;

    @InjectMocks
    private PropertyRegistrationService registrationService;

    @Test
    void registerPropertyOwner_LeavesRoleAndMappingPendingUntilApproval() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userPropertyRepository.findByUserId(any())).thenReturn(List.of());
        when(hotelRepository.save(any(Hotel.class))).thenAnswer(invocation -> {
            Hotel hotel = invocation.getArgument(0);
            hotel.setId(55L);
            return hotel;
        });
        when(planRepository.findByCode("BASIC")).thenReturn(Optional.empty());

        var user = registrationService.registerPropertyOwner(
                "owner@example.com", "secret123", "Owner", "0900000000",
                "Pending Hotel", "1 Test Street", null);

        assertTrue(user.getRoles() == null || user.getRoles().isEmpty());
        ArgumentCaptor<Hotel> property = ArgumentCaptor.forClass(Hotel.class);
        verify(ownershipLifecycleService).createPendingOwner(any(), property.capture());
        assertTrue("PENDING_APPROVAL".equals(property.getValue().getApprovalStatus()));
        assertTrue("INACTIVE".equals(property.getValue().getOperationStatus()));
    }
}
