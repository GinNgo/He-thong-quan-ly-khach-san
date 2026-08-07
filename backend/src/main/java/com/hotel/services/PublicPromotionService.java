package com.hotel.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.PublicPromotionDTO;
import com.hotel.entities.PromotionCampaign;
import com.hotel.entities.User;
import com.hotel.repositories.PromotionCampaignRepository;
import com.hotel.repositories.CustomerMembershipRepository;
import com.hotel.repositories.PromotionRedemptionRepository;
import com.hotel.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Service
public class PublicPromotionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicPromotionService.class);
    private static final List<String> COMMITTED_REDEMPTION_STATUSES = List.of("RESERVED", "APPLIED");

    private final PromotionCampaignRepository campaignRepository;
    private final PromotionRedemptionRepository redemptionRepository;
    private final CustomerMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PublicPromotionService(
            PromotionCampaignRepository campaignRepository,
            PromotionRedemptionRepository redemptionRepository,
            CustomerMembershipRepository membershipRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.campaignRepository = campaignRepository;
        this.redemptionRepository = redemptionRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PublicPromotionDTO> list(int limit) {
        return campaignRepository.findPublicActive(clock.instant()).stream()
                .filter(this::hasRemainingCapacity)
                .limit(Math.min(Math.max(limit, 1), 12))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public com.hotel.dtos.PromotionQuoteDTO.MemberBenefit membership(String username) {
        if (username == null || username.isBlank()) {
            return new com.hotel.dtos.PromotionQuoteDTO.MemberBenefit(false, null, null, null, "");
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return new com.hotel.dtos.PromotionQuoteDTO.MemberBenefit(false, null, null, null, "");
        }
        var memberships = membershipRepository.findActiveMemberships(user.getId(), null, clock.instant());
        if (memberships.isEmpty() || memberships.get(0).getTier() == null) {
            return new com.hotel.dtos.PromotionQuoteDTO.MemberBenefit(false, null, null, null, "");
        }
        var tier = memberships.get(0).getTier();
        return new com.hotel.dtos.PromotionQuoteDTO.MemberBenefit(
                true, tier.getCode(), tier.getNameVi(), tier.getNameEn(),
                "Membership tier assigned by the managed policy.");
    }

    private PublicPromotionDTO toDto(PromotionCampaign campaign) {
        JsonNode rules = readTree(campaign.getEligibilityJson());
        List<String> tiers = new ArrayList<>();
        JsonNode tierNode = rules.has("memberTierCodes") ? rules.path("memberTierCodes") : rules.path("memberTierCode");
        if (tierNode.isArray()) tierNode.forEach(item -> tiers.add(item.asText()));
        else if (!tierNode.isMissingNode() && !tierNode.isNull() && !tierNode.asText().isBlank()) tiers.add(tierNode.asText());
        return new PublicPromotionDTO(
                campaign.getId(), campaign.getCode(), campaign.getHotel() == null ? null : campaign.getHotel().getId(),
                campaign.getNameVi(), campaign.getNameEn(), campaign.getApplicationType(), campaign.getDiscountType(),
                campaign.getDiscountValue(), campaign.getMaxDiscount(), campaign.getEndsAt(),
                rules.path("memberOnly").asBoolean(false) || !tiers.isEmpty(), List.copyOf(tiers));
    }

    private boolean hasRemainingCapacity(PromotionCampaign campaign) {
        if (campaign.getRedemptionLimit() != null
                && redemptionRepository.countByCampaignIdAndStatusIn(
                        campaign.getId(), COMMITTED_REDEMPTION_STATUSES) >= campaign.getRedemptionLimit()) {
            return false;
        }
        if (campaign.getBudget() != null) {
            BigDecimal committed = redemptionRepository.sumCommittedDiscount(campaign.getId());
            if (committed != null && committed.compareTo(campaign.getBudget()) >= 0) return false;
        }
        return true;
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            LOGGER.warn("Ignoring invalid public promotion eligibility metadata: {}", exception.getMessage());
            return objectMapper.createObjectNode();
        }
    }
}
