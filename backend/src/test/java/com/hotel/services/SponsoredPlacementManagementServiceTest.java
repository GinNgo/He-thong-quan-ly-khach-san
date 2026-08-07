package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.SponsoredPlacementDTO;
import com.hotel.dtos.SponsoredPlacementRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.SponsoredPlacement;
import com.hotel.entities.User;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.SponsoredPlacementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SponsoredPlacementManagementServiceTest {

    @Mock
    private SponsoredPlacementRepository placementRepository;
    @Mock
    private HotelRepository hotelRepository;
    @Mock
    private PropertyAccessService propertyAccessService;
    @Mock
    private SubscriptionFeatureService subscriptionFeatureService;

    private SponsoredPlacementManagementService service;

    @BeforeEach
    void setUp() {
        service = new SponsoredPlacementManagementService(
                placementRepository, hotelRepository, propertyAccessService,
                subscriptionFeatureService, new ObjectMapper());
        lenient().when(placementRepository.save(any(SponsoredPlacement.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void tenantCreatesDraftOnlyForItsOperationalPropertyAndWithinQuota() {
        Hotel hotel = hotel(21L);
        when(propertyAccessService.requireManagedHotel(21L)).thenReturn(hotel);
        when(hotelRepository.findById(21L)).thenReturn(Optional.of(hotel));
        when(propertyAccessService.isOperational(hotel)).thenReturn(true);
        when(placementRepository.countByHotelIdAndStatusIn(any(), any())).thenReturn(1L);

        SponsoredPlacementDTO result = service.create(sponsoredRequest(21L, 21L));

        assertThat(result.hotelId()).isEqualTo(21L);
        assertThat(result.status()).isEqualTo(SponsoredPlacementDTO.PlacementStatus.DRAFT);
        verify(subscriptionFeatureService).checkFeatureLimitForProperty(
                21L, SponsoredPlacementManagementService.SUBSCRIPTION_FEATURE, 1L, 1L);
    }

    @Test
    void tenantCannotSponsorAnotherProperty() {
        Hotel owner = hotel(21L);
        Hotel target = hotel(22L);
        when(propertyAccessService.requireManagedHotel(21L)).thenReturn(owner);
        when(hotelRepository.findById(22L)).thenReturn(Optional.of(target));
        when(propertyAccessService.isOperational(target)).thenReturn(true);

        assertThatThrownBy(() -> service.create(sponsoredRequest(21L, 22L)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("only its own property");
    }

    @Test
    void arbitraryExternalCreativeAssetIsRejected() {
        SponsoredPlacementRequest invalid = new SponsoredPlacementRequest(
                21L, SponsoredPlacementRequest.PlacementSurface.HOME_PARTNER_SPOTLIGHT,
                SponsoredPlacementRequest.PlacementKind.SPONSORED, "Tiêu đề", "Title", null, null,
                "https://example.com/unapproved.jpg", "Ảnh", "Image",
                SponsoredPlacementRequest.TargetType.PROPERTY, 21L, null, null, null,
                Instant.now(), Instant.now().plusSeconds(3600), 10, null, 100L, 10L);

        assertThatThrownBy(() -> service.create(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("server-managed");
    }

    @Test
    void editorialPlacementRequiresPlatformAdministrator() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        SponsoredPlacementRequest request = new SponsoredPlacementRequest(
                null, SponsoredPlacementRequest.PlacementSurface.HOME_PARTNER_SPOTLIGHT,
                SponsoredPlacementRequest.PlacementKind.EDITORIAL, "Khám phá", "Discover", null, null,
                "/assets/editorial/discover.webp", "Khám phá", "Discover",
                SponsoredPlacementRequest.TargetType.SEARCH_COLLECTION, null,
                Map.of("provinceId", "10146"), 10146L, null,
                Instant.now(), Instant.now().plusSeconds(3600), 10, null, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("platform administrator");
    }

    @Test
    void platformAdministratorApprovesAValidatedPlacement() {
        Hotel hotel = hotel(21L);
        User admin = new User();
        admin.setId(99L);
        SponsoredPlacement placement = persistedPlacement(hotel);
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(propertyAccessService.currentUser()).thenReturn(admin);
        when(propertyAccessService.isOperational(hotel)).thenReturn(true);
        when(placementRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(placement));

        SponsoredPlacementDTO result = service.approve(7L);

        assertThat(result.status()).isEqualTo(SponsoredPlacementDTO.PlacementStatus.ACTIVE);
        assertThat(result.approvedByUserId()).isEqualTo(99L);
        assertThat(result.approvedAt()).isNotNull();
    }

    @Test
    void crossTenantUpdateIsHiddenAsNotFound() {
        Hotel otherTenant = hotel(22L);
        SponsoredPlacement placement = persistedPlacement(otherTenant);
        when(placementRepository.findById(7L)).thenReturn(Optional.of(placement));
        when(propertyAccessService.requireAssignedHotel(22L))
                .thenThrow(new ResourceNotFoundException("Sponsored placement not found."));

        assertThatThrownBy(() -> service.update(7L, sponsoredRequest(22L, 22L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private SponsoredPlacementRequest sponsoredRequest(Long hotelId, Long targetHotelId) {
        return new SponsoredPlacementRequest(
                hotelId, SponsoredPlacementRequest.PlacementSurface.HOME_PARTNER_SPOTLIGHT,
                SponsoredPlacementRequest.PlacementKind.SPONSORED, "Nghỉ dưỡng biển", "Beach escape",
                "Nội dung đối tác", "Partner content", "/media/placements/beach.webp",
                "Khu nghỉ dưỡng bên biển", "Beach resort",
                SponsoredPlacementRequest.TargetType.PROPERTY, targetHotelId, null, null, null,
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600), 20,
                BigDecimal.valueOf(2_000_000), 1000L, 100L);
    }

    private Hotel hotel(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotel;
    }

    private SponsoredPlacement persistedPlacement(Hotel hotel) {
        SponsoredPlacement placement = new SponsoredPlacement();
        placement.setId(7L);
        placement.setHotel(hotel);
        placement.setPlacementSurface("HOME_PARTNER_SPOTLIGHT");
        placement.setPlacementKind("SPONSORED");
        placement.setStatus("DRAFT");
        placement.setTitleVi("Nghỉ dưỡng biển");
        placement.setTitleEn("Beach escape");
        placement.setImageUrl("/media/placements/beach.webp");
        placement.setImageAltVi("Khu nghỉ dưỡng bên biển");
        placement.setImageAltEn("Beach resort");
        placement.setTargetType("PROPERTY");
        placement.setTargetHotel(hotel);
        placement.setStartsAt(Instant.now().minusSeconds(60));
        placement.setEndsAt(Instant.now().plusSeconds(3600));
        placement.setSortPriority(10);
        placement.setSpentAmount(BigDecimal.ZERO);
        placement.setImpressionCount(0L);
        placement.setClickCount(0L);
        return placement;
    }
}
