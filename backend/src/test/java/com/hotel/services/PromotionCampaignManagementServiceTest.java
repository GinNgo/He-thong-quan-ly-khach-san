package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.PromotionCampaignDTO;
import com.hotel.dtos.PromotionCampaignRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.PromotionCampaign;
import com.hotel.repositories.PromotionCampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PromotionCampaignManagementServiceTest {

    @Mock
    private PromotionCampaignRepository campaignRepository;
    @Mock
    private PropertyAccessService propertyAccessService;
    @Mock
    private SubscriptionFeatureService subscriptionFeatureService;

    private PromotionCampaignManagementService service;

    @BeforeEach
    void setUp() {
        service = new PromotionCampaignManagementService(
                campaignRepository, propertyAccessService, subscriptionFeatureService, new ObjectMapper());
        lenient().when(campaignRepository.save(any(PromotionCampaign.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void tenantCreateUsesServerResolvedPropertyAndSubscriptionQuota() {
        Hotel hotel = hotel(11L);
        when(propertyAccessService.requireManagedHotel(11L)).thenReturn(hotel);
        when(campaignRepository.countByHotelIdAndStatusIn(any(), any())).thenReturn(2L);

        PromotionCampaignDTO result = service.create(tenantRequest(11L));

        assertThat(result.hotelId()).isEqualTo(11L);
        assertThat(result.status()).isEqualTo(PromotionCampaignDTO.CampaignStatus.DRAFT);
        verify(subscriptionFeatureService).checkFeatureLimitForProperty(
                11L, PromotionCampaignManagementService.SUBSCRIPTION_FEATURE, 2L, 1L);
        verify(propertyAccessService).requireManagedHotel(11L);
    }

    @Test
    void nonAdminCannotCreateSystemCampaign() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        PromotionCampaignRequest request = new PromotionCampaignRequest(
                "WELCOME", PromotionCampaignRequest.OwnerType.SYSTEM, null,
                PromotionCampaignRequest.ApplicationType.AUTOMATIC, "Chào mừng", "Welcome",
                PromotionCampaignRequest.DiscountType.PERCENT, BigDecimal.TEN, BigDecimal.valueOf(100_000),
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600), "Asia/Ho_Chi_Minh",
                Map.of(), BigDecimal.valueOf(1_000_000), 100L, 1L,
                PromotionCampaignRequest.StackingPolicy.ALLOW_ONE_COUPON, 10);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("platform administrator");
    }

    @Test
    void percentageAboveOneHundredIsRejectedBeforePersistence() {
        PromotionCampaignRequest invalid = new PromotionCampaignRequest(
                "INVALID", PromotionCampaignRequest.OwnerType.TENANT, 11L,
                PromotionCampaignRequest.ApplicationType.AUTOMATIC, "Không hợp lệ", "Invalid",
                PromotionCampaignRequest.DiscountType.PERCENT, BigDecimal.valueOf(101), null,
                Instant.now(), Instant.now().plusSeconds(3600), "Asia/Ho_Chi_Minh",
                Map.of(), null, null, null, PromotionCampaignRequest.StackingPolicy.NO_COUPON, 0);

        assertThatThrownBy(() -> service.create(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed 100");
    }

    @Test
    void tenantCanScheduleThenPauseAnEligibleCampaign() {
        Hotel hotel = hotel(11L);
        Instant startsAt = Instant.now().plusSeconds(3600);
        PromotionCampaignRequest request = tenantRequest(11L, startsAt, startsAt.plusSeconds(3600));
        when(propertyAccessService.requireManagedHotel(11L)).thenReturn(hotel);
        when(propertyAccessService.requireAssignedHotel(11L)).thenReturn(hotel);
        when(campaignRepository.countByHotelIdAndStatusIn(any(), any())).thenReturn(0L);

        service.create(request);
        org.mockito.ArgumentCaptor<PromotionCampaign> campaignCaptor =
                org.mockito.ArgumentCaptor.forClass(PromotionCampaign.class);
        verify(campaignRepository).save(campaignCaptor.capture());
        PromotionCampaign stored = campaignCaptor.getValue();
        when(campaignRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(stored));

        PromotionCampaignDTO scheduled = service.activate(77L);
        PromotionCampaignDTO paused = service.pause(77L);

        assertThat(scheduled.status()).isEqualTo(PromotionCampaignDTO.CampaignStatus.SCHEDULED);
        assertThat(paused.status()).isEqualTo(PromotionCampaignDTO.CampaignStatus.PAUSED);
        verify(subscriptionFeatureService).requireFeatureForProperty(
                11L, PromotionCampaignManagementService.SUBSCRIPTION_FEATURE);
    }

    private PromotionCampaignRequest tenantRequest(Long hotelId) {
        return tenantRequest(hotelId, Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600));
    }

    private PromotionCampaignRequest tenantRequest(Long hotelId, Instant startsAt, Instant endsAt) {
        return new PromotionCampaignRequest(
                "SUMMER_2026", PromotionCampaignRequest.OwnerType.TENANT, hotelId,
                PromotionCampaignRequest.ApplicationType.AUTOMATIC, "Hè 2026", "Summer 2026",
                PromotionCampaignRequest.DiscountType.PERCENT, BigDecimal.TEN, BigDecimal.valueOf(200_000),
                startsAt, endsAt, "Asia/Ho_Chi_Minh",
                Map.of("minimumSubtotal", 500_000), BigDecimal.valueOf(5_000_000), 100L, 1L,
                PromotionCampaignRequest.StackingPolicy.ALLOW_ONE_COUPON, 20);
    }

    private Hotel hotel(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotel;
    }
}
