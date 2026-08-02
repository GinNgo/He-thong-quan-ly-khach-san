package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyClaimRequest;
import com.hotel.entities.User;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyClaimRequestRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyClaimServiceTest {
    @Mock private PropertyClaimRequestRepository claimRepository;
    @Mock private HotelRepository hotelRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private SubscriptionFeatureService subscriptionFeatureService;

    @InjectMocks
    private PropertyClaimService claimService;

    @Test
    void approveClaim_WhenRequesterExceedsPropertyQuota_DoesNotMutateClaimOrProperty() {
        User requester = new User();
        requester.setId(7L);
        User admin = new User();
        admin.setId(1L);
        Hotel property = new Hotel();
        property.setId(10L);
        PropertyClaimRequest claim = new PropertyClaimRequest();
        claim.setId(20L);
        claim.setStatus("PENDING");
        claim.setRequesterUser(requester);
        claim.setProperty(property);

        when(claimRepository.findById(20L)).thenReturn(Optional.of(claim));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userPropertyRepository.countActiveOwnedPropertiesByUserId(7L)).thenReturn(1L);
        doThrow(new RuntimeException("quota exceeded"))
                .when(subscriptionFeatureService).checkFeatureLimit(7L, "MAX_PROPERTIES", 1L, 1L);

        assertThrows(RuntimeException.class, () -> claimService.approveClaim(20L, 1L));

        verify(claimRepository, never()).save(any());
        verify(hotelRepository, never()).save(any());
        verify(userPropertyRepository, never()).save(any());
    }
}
