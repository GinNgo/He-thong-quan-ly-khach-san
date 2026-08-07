package com.hotel.repositories;

import com.hotel.entities.PromotionCampaign;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PromotionCampaignRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-03T08:00:00Z");

    @Autowired PromotionCampaignRepository campaignRepository;

    @Test
    void publicQueryReturnsOnlyActiveInWindowCampaignsInPriorityOrder() {
        campaignRepository.save(campaign("ACTIVE_LOW", "ACTIVE", NOW.minusSeconds(60), NOW.plusSeconds(60), 5));
        campaignRepository.save(campaign("ACTIVE_HIGH", "ACTIVE", NOW.minusSeconds(60), NOW.plusSeconds(60), 20));
        campaignRepository.save(campaign("EXPIRED", "ACTIVE", NOW.minusSeconds(120), NOW.minusSeconds(1), 50));
        campaignRepository.save(campaign("FUTURE", "ACTIVE", NOW.plusSeconds(1), NOW.plusSeconds(120), 50));
        campaignRepository.save(campaign("PAUSED", "PAUSED", NOW.minusSeconds(60), NOW.plusSeconds(60), 50));
        campaignRepository.flush();

        var campaigns = campaignRepository.findPublicActive(NOW);

        assertThat(campaigns).extracting(PromotionCampaign::getCode)
                .containsExactly("ACTIVE_HIGH", "ACTIVE_LOW");
    }

    private PromotionCampaign campaign(String code, String status, Instant startsAt, Instant endsAt, int priority) {
        PromotionCampaign campaign = new PromotionCampaign();
        campaign.setCode(code);
        campaign.setOwnerType("SYSTEM");
        campaign.setApplicationType("AUTOMATIC");
        campaign.setNameVi(code);
        campaign.setNameEn(code);
        campaign.setDiscountType("PERCENT");
        campaign.setDiscountValue(BigDecimal.TEN);
        campaign.setStartsAt(startsAt);
        campaign.setEndsAt(endsAt);
        campaign.setTimezone("Asia/Ho_Chi_Minh");
        campaign.setEligibilityJson("{}");
        campaign.setStackingPolicy("NO_COUPON");
        campaign.setPriority(priority);
        campaign.setStatus(status);
        return campaign;
    }
}
