package com.hotel.favorites;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
class CustomerFavoriteRepositoryTest {

    @Autowired
    private CustomerFavoriteRepository favoriteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Test
    void publicListDoesNotLeakAnotherOwnerOrAnIneligibleProperty() {
        User first = userRepository.saveAndFlush(customer("favorite-owner-a"));
        User second = userRepository.saveAndFlush(customer("favorite-owner-b"));
        Hotel publicHotel = hotelRepository.saveAndFlush(hotel("FAV-PUBLIC", "APPROVED", "ACTIVE"));
        Hotel suspendedHotel = hotelRepository.saveAndFlush(hotel("FAV-HIDDEN", "APPROVED", "SUSPENDED"));

        favoriteRepository.saveAndFlush(favorite(first, publicHotel));
        favoriteRepository.saveAndFlush(favorite(first, suspendedHotel));
        favoriteRepository.saveAndFlush(favorite(second, publicHotel));

        List<CustomerFavorite> result = favoriteRepository.findPublicFavorites(first.getId());

        assertEquals(List.of(publicHotel.getId()), result.stream().map(item -> item.getHotel().getId()).toList());
    }

    @Test
    void databaseConstraintMakesRepeatedFavoriteIdempotencyEnforceable() {
        User customer = userRepository.saveAndFlush(customer("favorite-unique-owner"));
        Hotel hotel = hotelRepository.saveAndFlush(hotel("FAV-UNIQUE", "APPROVED", "ACTIVE"));
        favoriteRepository.saveAndFlush(favorite(customer, hotel));

        assertThrows(DataIntegrityViolationException.class,
                () -> favoriteRepository.saveAndFlush(favorite(customer, hotel)));
    }

    private User customer(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPasswordHash("test-only-password-hash");
        user.setStatus("ACTIVE");
        return user;
    }

    private Hotel hotel(String code, String approvalStatus, String operationStatus) {
        Hotel hotel = new Hotel();
        hotel.setName(code);
        hotel.setCode(code);
        hotel.setAddressLine("1 Test Street");
        hotel.setCity("Test City");
        hotel.setCountry("VN");
        hotel.setApprovalStatus(approvalStatus);
        hotel.setOperationStatus(operationStatus);
        return hotel;
    }

    private CustomerFavorite favorite(User customer, Hotel hotel) {
        CustomerFavorite favorite = new CustomerFavorite();
        favorite.setCustomer(customer);
        favorite.setHotel(hotel);
        return favorite;
    }
}
