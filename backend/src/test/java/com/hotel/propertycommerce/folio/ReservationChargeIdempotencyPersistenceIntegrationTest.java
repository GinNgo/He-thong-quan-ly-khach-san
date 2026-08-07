package com.hotel.propertycommerce.folio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.HotelService;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyRepository;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyService;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.HotelServiceRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        ReservationChargeService.class,
        FinancialIdempotencyService.class,
        ReservationChargeIdempotencyPersistenceIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReservationChargeIdempotencyPersistenceIntegrationTest {

    @TestConfiguration
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    @Autowired private HotelRepository hotelRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private HotelServiceRepository hotelServiceRepository;
    @Autowired private ReservationChargeLineRepository chargeLineRepository;
    @Autowired private FinancialIdempotencyRepository idempotencyRepository;
    @Autowired private ReservationChargeService chargeService;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private PropertyAccessService propertyAccessService;

    @BeforeEach
    void clearChargeEvidence() {
        chargeLineRepository.deleteAll();
        idempotencyRepository.deleteAll();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void equivalentReplaySurvivesTransactionsAndPersistsOneChargeLine() {
        Fixture fixture = transactionTemplate.execute(status -> fixture());
        assertThat(fixture).isNotNull();
        authorize(fixture);
        ReservationChargeService.AddServiceChargeCommand command = command(
                fixture.reservationId(), fixture.serviceId(), "persisted-service-replay");

        ReservationChargeService.AddServiceChargeResult first = chargeService.addServiceCharge(command);
        ReservationChargeService.AddServiceChargeResult replay = chargeService.addServiceCharge(command);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.line().getId()).isEqualTo(first.line().getId());
        assertThat(chargeLineRepository.findByHotelIdAndReservationIdOrderByCreatedAtAscIdAsc(
                fixture.hotelId(), fixture.reservationId())).hasSize(1);
        assertThat(idempotencyRepository.count()).isEqualTo(1);
    }

    @Test
    void crossPropertyCatalogIdFailsBeforeIdempotencyOrChargePersistence() {
        Fixture fixture = transactionTemplate.execute(status -> fixture());
        assertThat(fixture).isNotNull();
        authorize(fixture);

        assertThatThrownBy(() -> chargeService.addServiceCharge(command(
                fixture.reservationId(), fixture.otherHotelServiceId(), "cross-property-service")))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.RESOURCE_NOT_FOUND));

        assertThat(chargeLineRepository.findByHotelIdAndReservationIdOrderByCreatedAtAscIdAsc(
                fixture.hotelId(), fixture.reservationId())).isEmpty();
        assertThat(idempotencyRepository.count()).isZero();
    }

    private void authorize(Fixture fixture) {
        User actor = userRepository.findById(fixture.actorId()).orElseThrow();
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.currentUser()).thenReturn(actor);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(fixture.hotelId()));
        CustomUserDetails principal = new CustomUserDetails(
                actor.getUsername(),
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")),
                Map.of(FunctionCode.RESERVATION_SERVICE, ActionCode.CREATE),
                actor.getId(),
                fixture.hotelId(),
                Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private ReservationChargeService.AddServiceChargeCommand command(
            Long reservationId,
            Long serviceId,
            String idempotencyKey) {
        return new ReservationChargeService.AddServiceChargeCommand(
                reservationId,
                serviceId,
                ReservationChargeLine.ChargeType.MINIBAR,
                BigDecimal.valueOf(2),
                LocalDateTime.of(2026, 8, 3, 18, 0),
                idempotencyKey,
                "corr-" + idempotencyKey);
    }

    private Fixture fixture() {
        String suffix = Long.toString(System.nanoTime());
        Hotel hotel = hotelRepository.saveAndFlush(hotel("A-" + suffix));
        Hotel otherHotel = hotelRepository.saveAndFlush(hotel("B-" + suffix));
        User actor = userRepository.saveAndFlush(user(suffix));
        Reservation reservation = reservationRepository.saveAndFlush(reservation(hotel, actor));
        HotelService service = hotelServiceRepository.saveAndFlush(service(hotel, "MINIBAR-A-" + suffix));
        HotelService otherService = hotelServiceRepository.saveAndFlush(service(otherHotel, "MINIBAR-B-" + suffix));
        return new Fixture(hotel.getId(), actor.getId(), reservation.getId(), service.getId(), otherService.getId());
    }

    private Hotel hotel(String suffix) {
        Hotel hotel = new Hotel();
        hotel.setCode("T304-" + suffix);
        hotel.setSlug(("t304-" + suffix).toLowerCase());
        hotel.setName("T304 Hotel " + suffix);
        hotel.setNameVi("T304 Hotel " + suffix);
        hotel.setAddressLine("1 Tenant Street");
        hotel.setCity("Ho Chi Minh City");
        hotel.setCountry("Viet Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotel;
    }

    private User user(String suffix) {
        User user = new User();
        user.setUsername("t304-" + suffix);
        user.setEmail("t304-" + suffix + "@example.test");
        user.setPasswordHash("test");
        user.setFullName("T304 Staff");
        user.setStatus("ACTIVE");
        return user;
    }

    private Reservation reservation(Hotel hotel, User actor) {
        Reservation reservation = new Reservation();
        reservation.setHotel(hotel);
        reservation.setUser(actor);
        reservation.setStatus("CHECKED_IN");
        reservation.setCheckInDate(LocalDate.of(2026, 8, 3));
        reservation.setCheckOutDate(LocalDate.of(2026, 8, 5));
        reservation.setGuests(2);
        reservation.setTotalAmount(BigDecimal.valueOf(2_000_000));
        return reservation;
    }

    private HotelService service(Hotel hotel, String code) {
        HotelService service = new HotelService();
        service.setHotel(hotel);
        service.setSystemService(false);
        service.setCode(code);
        service.setNameVi("Nuoc minibar");
        service.setNameEn("Minibar water");
        service.setPrice(BigDecimal.valueOf(50_000));
        service.setStatus("ACTIVE");
        return service;
    }

    private record Fixture(
            Long hotelId,
            Long actorId,
            Long reservationId,
            Long serviceId,
            Long otherHotelServiceId) {
    }
}
