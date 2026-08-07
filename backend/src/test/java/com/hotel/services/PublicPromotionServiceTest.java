package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.CustomerMembership;
import com.hotel.entities.MembershipTier;
import com.hotel.entities.PromotionCampaign;
import com.hotel.entities.User;
import com.hotel.repositories.CustomerMembershipRepository;
import com.hotel.repositories.PromotionCampaignRepository;
import com.hotel.repositories.PromotionRedemptionRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicPromotionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T08:00:00Z");

    @Mock PromotionCampaignRepository campaignRepository;
    @Mock PromotionRedemptionRepository redemptionRepository;
    @Mock CustomerMembershipRepository membershipRepository;
    @Mock UserRepository userRepository;

    private PublicPromotionService service;

    @BeforeEach
    void setUp() {
        service = new PublicPromotionService(
                campaignRepository,
                redemptionRepository,
                membershipRepository,
                userRepository,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(redemptionRepository.countByCampaignIdAndStatusIn(
                org.mockito.ArgumentMatchers.anyLong(), anyCollection())).thenReturn(0L);
        lenient().when(redemptionRepository.sumCommittedDiscount(
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void returnsTypedActiveCampaignsAndExplainsMemberOnlyRules() {
        PromotionCampaign publicDeal = campaign(10L, "PUBLIC10", "{}");
        PromotionCampaign memberDeal = campaign(20L, "GOLD20", "{\"memberTierCodes\":[\"GOLD\"]}");
        when(campaignRepository.findPublicActive(NOW)).thenReturn(List.of(publicDeal, memberDeal));

        var promotions = service.list(6);

        assertThat(promotions).hasSize(2);
        assertThat(promotions.get(0).memberOnly()).isFalse();
        assertThat(promotions.get(1).memberOnly()).isTrue();
        assertThat(promotions.get(1).requiredTierCodes()).containsExactly("GOLD");
        assertThat(promotions.get(1).code()).isEqualTo("GOLD20");
    }

    @Test
    void excludesCampaignsWhoseQuotaOrBudgetIsAlreadyConsumed() {
        PromotionCampaign exhaustedQuota = campaign(30L, "QUOTA", "{}");
        exhaustedQuota.setRedemptionLimit(1L);
        PromotionCampaign exhaustedBudget = campaign(40L, "BUDGET", "{}");
        exhaustedBudget.setBudget(new BigDecimal("100000"));
        PromotionCampaign available = campaign(50L, "AVAILABLE", "{}");
        when(campaignRepository.findPublicActive(NOW))
                .thenReturn(List.of(exhaustedQuota, exhaustedBudget, available));
        when(redemptionRepository.countByCampaignIdAndStatusIn(eq(30L), anyCollection())).thenReturn(1L);
        when(redemptionRepository.sumCommittedDiscount(40L)).thenReturn(new BigDecimal("100000"));

        var promotions = service.list(6);

        assertThat(promotions).extracting(promotion -> promotion.code()).containsExactly("AVAILABLE");
    }

    @Test
    void returnsOnlyAnExplicitManagedMembershipTier() {
        User user = new User();
        user.setId(99L);
        user.setUsername("member@example.com");
        MembershipTier tier = new MembershipTier();
        tier.setCode("GOLD");
        tier.setNameVi("Vàng");
        tier.setNameEn("Gold");
        CustomerMembership membership = new CustomerMembership();
        membership.setTier(tier);
        when(userRepository.findByUsername("member@example.com")).thenReturn(Optional.of(user));
        when(membershipRepository.findActiveMemberships(99L, null, NOW)).thenReturn(List.of(membership));

        var result = service.membership("member@example.com");

        assertThat(result.eligible()).isTrue();
        assertThat(result.tierCode()).isEqualTo("GOLD");
        assertThat(result.tierNameVi()).isEqualTo("Vàng");
        assertThat(result.tierNameEn()).isEqualTo("Gold");
    }

    private PromotionCampaign campaign(Long id, String code, String eligibilityJson) {
        PromotionCampaign campaign = new PromotionCampaign();
        campaign.setId(id);
        campaign.setCode(code);
        campaign.setOwnerType("SYSTEM");
        campaign.setApplicationType("AUTOMATIC");
        campaign.setNameVi(code);
        campaign.setNameEn(code);
        campaign.setDiscountType("PERCENT");
        campaign.setDiscountValue(BigDecimal.TEN);
        campaign.setStartsAt(NOW.minusSeconds(60));
        campaign.setEndsAt(NOW.plusSeconds(3600));
        campaign.setTimezone("Asia/Ho_Chi_Minh");
        campaign.setEligibilityJson(eligibilityJson);
        campaign.setStackingPolicy("NO_COUPON");
        campaign.setPriority(10);
        campaign.setStatus("ACTIVE");
        return campaign;
    }
}
