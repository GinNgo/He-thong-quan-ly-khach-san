package com.hotel.favorites;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private CustomerFavoriteRepository favoriteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HotelRepository hotelRepository;

    private FavoriteService service;

    @BeforeEach
    void setUp() {
        service = new FavoriteService(favoriteRepository, userRepository, hotelRepository);
    }

    @Test
    void repeatedAddReturnsTheOwnedRecordWithoutCreatingADuplicate() {
        User customer = customer(41L);
        CustomerFavorite existing = favorite(7L, customer, eligibleHotel(9L));
        when(userRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(customer));
        when(favoriteRepository.findOwnedFavorite(41L, 9L)).thenReturn(Optional.of(existing));

        FavoritePropertyResponse response = service.addForCustomer(41L, 9L);

        assertEquals(7L, response.favoriteId());
        assertEquals(9L, response.hotelId());
        verify(favoriteRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(hotelRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void addRejectsAHotelThatIsNotPubliclyEligible() {
        User customer = customer(41L);
        Hotel suspended = eligibleHotel(9L);
        suspended.setOperationStatus("SUSPENDED");
        when(userRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(customer));
        when(favoriteRepository.findOwnedFavorite(41L, 9L)).thenReturn(Optional.empty());
        when(hotelRepository.findById(9L)).thenReturn(Optional.of(suspended));

        assertThrows(ResourceNotFoundException.class, () -> service.addForCustomer(41L, 9L));

        verify(favoriteRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listAndRemoveAreAlwaysScopedToTheAuthenticatedOwner() {
        User customer = customer(41L);
        when(favoriteRepository.findPublicFavorites(41L))
                .thenReturn(List.of(favorite(7L, customer, eligibleHotel(9L))));

        List<FavoritePropertyResponse> result = service.listForCustomer(41L);
        service.removeForCustomer(41L, 9L);

        assertEquals(List.of(9L), result.stream().map(FavoritePropertyResponse::hotelId).toList());
        verify(favoriteRepository).findPublicFavorites(41L);
        verify(favoriteRepository).deleteOwnedFavorite(41L, 9L);
    }

    private User customer(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("customer-" + id);
        return user;
    }

    private Hotel eligibleHotel(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setName("LuxeStay " + id);
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotel;
    }

    private CustomerFavorite favorite(Long id, User customer, Hotel hotel) {
        CustomerFavorite favorite = new CustomerFavorite();
        favorite.setId(id);
        favorite.setCustomer(customer);
        favorite.setHotel(hotel);
        favorite.setCreatedAt(LocalDateTime.of(2026, 8, 4, 8, 30));
        return favorite;
    }
}
