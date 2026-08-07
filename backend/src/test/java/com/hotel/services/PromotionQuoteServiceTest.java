package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.PromotionQuoteDTO;
import com.hotel.entities.CustomerMembership;
import com.hotel.entities.Hotel;
import com.hotel.entities.MembershipTier;
import com.hotel.entities.PromotionCampaign;
import com.hotel.entities.PromotionRedemption;
import com.hotel.entities.Reservation;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.repositories.CustomerMembershipRepository;
import com.hotel.repositories.PromotionCampaignRepository;
import com.hotel.repositories.PromotionRedemptionRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.services.PromotionQuoteService;
import com.hotel.services.PublicInventoryEligibilityPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionQuoteServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T08:00:00Z");

    @Mock PromotionCampaignRepository campaignRepository;
    @Mock PromotionRedemptionRepository redemptionRepository;
    @Mock CustomerMembershipRepository membershipRepository;
    @Mock RoomTypeRepository roomTypeRepository;
    @Mock RoomAvailabilityService roomAvailabilityService;
    @Mock PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy;

    private PromotionQuoteService service;
    private RoomType roomType;

    @BeforeEach
    void setUp() {
        service = new PromotionQuoteService(
                campaignRepository,
                redemptionRepository,
                membershipRepository,
                roomTypeRepository,
                roomAvailabilityService,
                publicInventoryEligibilityPolicy,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        roomType = roomType(1L, 1_000_000);
        lenient().when(membershipRepository.findActiveMemberships(anyLong(), anyLong(), eq(NOW))).thenReturn(List.of());
        lenient().when(redemptionRepository.countByCampaignIdAndStatusIn(anyLong(), anyCollection())).thenReturn(0L);
        lenient().when(redemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(anyLong(), anyLong(), anyCollection())).thenReturn(0L);
        lenient().when(redemptionRepository.sumCommittedDiscount(anyLong())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void choosesTheLargestEligibleAutomaticCampaignAndRoundsVnd() {
        PromotionCampaign tenPercent = campaign(10L, "TEN", "AUTOMATIC", "PERCENT", "10", 0);
        PromotionCampaign twentyPercent = campaign(20L, "TWENTY", "AUTOMATIC", "PERCENT", "20", 0);
        stubCampaigns(tenPercent, twentyPercent);

        PromotionQuoteDTO quote = service.quoteForRoom(
                roomType, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12),
                1, 2, 0, null, null);

        assertEquals(new BigDecimal("2000000"), quote.baseSubtotal());
        assertEquals(new BigDecimal("400000"), quote.totalDiscount());
        assertEquals(new BigDecimal("1900000"), quote.finalTotal());
        assertEquals(List.of(20L), quote.appliedPromotions().stream()
                .map(PromotionQuoteDTO.AppliedPromotion::campaignId).toList());
    }

    @Test
    void excludesExpiredAndIneligibleCampaigns() {
        PromotionCampaign expired = campaign(11L, "EXPIRED", "AUTOMATIC", "FIXED", "100000", 0);
        expired.setEndsAt(NOW.minusSeconds(1));
        PromotionCampaign tooShort = campaign(12L, "SHORT", "AUTOMATIC", "FIXED", "100000", 0);
        tooShort.setEligibilityJson("{\"minNights\":3}");
        stubCampaigns(expired, tooShort);

        PromotionQuoteDTO quote = service.quoteForRoom(
                roomType, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12),
                1, 2, 0, null, null);

        assertTrue(quote.appliedPromotions().isEmpty());
        assertEquals(new BigDecimal("2300000"), quote.finalTotal());
    }

    @Test
    void appliesAtMostOneCouponAfterAutomaticAndCapsEachDiscount() {
        PromotionCampaign automatic = campaign(30L, "AUTO", "AUTOMATIC", "PERCENT", "50", 0);
        automatic.setMaxDiscount(new BigDecimal("100000"));
        automatic.setStackingPolicy("ALLOW_ONE_COUPON");
        PromotionCampaign coupon = campaign(31L, "SAVE50", "COUPON", "FIXED", "50000", 5);
        coupon.setStackingPolicy("ALLOW_ONE_COUPON");
        stubCampaigns(automatic, coupon);

        PromotionQuoteDTO quote = service.quoteForRoom(
                roomType, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12),
                1, 2, 0, "save50", null);

        assertEquals(new BigDecimal("150000"), quote.totalDiscount());
        assertEquals(2, quote.appliedPromotions().size());
        assertEquals(new BigDecimal("2150000"), quote.finalTotal());
    }

    @Test
    void skipsExhaustedQuotaAndBudget() {
        PromotionCampaign quota = campaign(40L, "LIMIT", "AUTOMATIC", "FIXED", "100000", 0);
        quota.setRedemptionLimit(1L);
        PromotionCampaign budget = campaign(41L, "BUDGET", "AUTOMATIC", "FIXED", "100000", 0);
        budget.setBudget(new BigDecimal("50000"));
        when(redemptionRepository.countByCampaignIdAndStatusIn(eq(40L), anyCollection())).thenReturn(1L);
        when(redemptionRepository.sumCommittedDiscount(eq(41L))).thenReturn(new BigDecimal("50000"));
        stubCampaigns(quota, budget);

        PromotionQuoteDTO quote = service.quoteForRoom(
                roomType, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12),
                1, 2, 0, null, null);

        assertTrue(quote.appliedPromotions().isEmpty());
    }

    @Test
    void requiresAnExplicitActiveMembershipTier() {
        MembershipTier tier = new MembershipTier();
        tier.setCode("GOLD");
        tier.setNameVi("Vàng");
        tier.setNameEn("Gold");
        tier.setStatus("ACTIVE");
        CustomerMembership membership = new CustomerMembership();
        membership.setTier(tier);
        membership.setStatus("ACTIVE");
        PromotionCampaign memberOnly = campaign(50L, "GOLD", "AUTOMATIC", "FIXED", "100000", 0);
        memberOnly.setEligibilityJson("{\"memberTierCodes\":[\"GOLD\"]}");
        stubCampaigns(memberOnly);
        when(membershipRepository.findActiveMemberships(eq(99L), eq(1L), eq(NOW))).thenReturn(List.of(membership));

        PromotionQuoteDTO guestQuote = service.quoteForRoom(
                roomType, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12),
                1, 2, 0, null, null);
        PromotionQuoteDTO memberQuote = service.quoteForRoom(
                roomType, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12),
                1, 2, 0, null, 99L);

        assertTrue(guestQuote.appliedPromotions().isEmpty());
        assertEquals(new BigDecimal("100000"), memberQuote.totalDiscount());
        assertTrue(memberQuote.memberBenefit().eligible());
    }

    @Test
    void redemptionReplayDoesNotCreateAnotherRow() {
        PromotionCampaign campaign = campaign(60L, "REPLAY", "AUTOMATIC", "FIXED", "100000", 0);
        Reservation reservation = new Reservation();
        reservation.setId(501L);
        Hotel hotel = new Hotel();
        hotel.setId(1L);
        reservation.setHotel(hotel);
        User customer = new User();
        customer.setId(99L);
        PromotionQuoteDTO quote = new PromotionQuoteDTO(
                "quote-1", NOW.plusSeconds(60), 1L, 1L,
                new BigDecimal("1000000"), 1, 1,
                new BigDecimal("1000000"), new BigDecimal("150000"), BigDecimal.ZERO,
                new BigDecimal("150000"),
                List.of(new PromotionQuoteDTO.AppliedPromotion(60L, "REPLAY", "AUTOMATIC", "Replay", "Replay", new BigDecimal("100000"))),
                new PromotionQuoteDTO.MemberBenefit(false, null, null, null, ""),
                new BigDecimal("100000"), new BigDecimal("1050000"), "VND");
        PromotionRedemption existing = new PromotionRedemption();
        existing.setReservation(reservation);
        existing.setDiscountAmount(new BigDecimal("100000"));
        when(campaignRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(campaign));
        when(redemptionRepository.findByIdempotencyKeyForUpdate("RESERVATION-501-CAMPAIGN-60"))
                .thenReturn(Optional.of(existing));

        service.redeem(reservation, customer, quote);

        verify(redemptionRepository, never()).save(any(PromotionRedemption.class));
    }

    private void stubCampaigns(PromotionCampaign... campaigns) {
        when(campaignRepository.findEligibleCampaigns(anyCollection(), eq(null), eq(NOW)))
                .thenReturn(List.of(campaigns));
    }

    private RoomType roomType(Long id, int nightlyPrice) {
        Hotel hotel = new Hotel();
        hotel.setId(1L);
        RoomType room = new RoomType();
        room.setId(id);
        room.setHotel(hotel);
        room.setCode("STANDARD");
        room.setNameVi("Phòng tiêu chuẩn");
        room.setNameEn("Standard room");
        room.setBasePrice(new BigDecimal(nightlyPrice));
        room.setMaxGuests(4);
        room.setMaxAdults(4);
        room.setMaxChildren(4);
        room.setStatus("ACTIVE");
        return room;
    }

    private PromotionCampaign campaign(Long id, String code, String applicationType,
                                       String discountType, String discountValue, int priority) {
        PromotionCampaign campaign = new PromotionCampaign();
        campaign.setId(id);
        campaign.setCode(code);
        campaign.setApplicationType(applicationType);
        campaign.setDiscountType(discountType);
        campaign.setDiscountValue(new BigDecimal(discountValue));
        campaign.setStartsAt(NOW.minusSeconds(60));
        campaign.setEndsAt(NOW.plusSeconds(3600));
        campaign.setStatus("ACTIVE");
        campaign.setPriority(priority);
        campaign.setStackingPolicy("NO_COUPON");
        campaign.setNameVi(code);
        campaign.setNameEn(code);
        campaign.setHotel(roomType.getHotel());
        return campaign;
    }
}
