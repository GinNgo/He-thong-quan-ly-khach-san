package com.hotel.favorites;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final CustomerFavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;

    @Transactional(readOnly = true)
    public List<FavoritePropertyResponse> listForCustomer(Long customerId) {
        return favoriteRepository.findPublicFavorites(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FavoritePropertyResponse addForCustomer(Long customerId, Long hotelId) {
        User customer = userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));

        CustomerFavorite existing = favoriteRepository.findOwnedFavorite(customerId, hotelId).orElse(null);
        if (existing != null) {
            ensurePubliclyEligible(existing.getHotel());
            return toResponse(existing);
        }

        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(this::propertyNotFound);
        ensurePubliclyEligible(hotel);

        CustomerFavorite favorite = new CustomerFavorite();
        favorite.setCustomer(customer);
        favorite.setHotel(hotel);
        return toResponse(favoriteRepository.saveAndFlush(favorite));
    }

    @Transactional
    public void removeForCustomer(Long customerId, Long hotelId) {
        favoriteRepository.deleteOwnedFavorite(customerId, hotelId);
    }

    private FavoritePropertyResponse toResponse(CustomerFavorite favorite) {
        Hotel hotel = favorite.getHotel();
        String displayName = firstNonBlank(hotel.getNameVi(), hotel.getName(), hotel.getNameEn());
        return new FavoritePropertyResponse(
                favorite.getId(),
                hotel.getId(),
                displayName,
                hotel.getSlug(),
                hotel.getAddressLine(),
                hotel.getCity(),
                hotel.getMainImage(),
                hotel.getPropertyType(),
                hotel.getAverageRating(),
                hotel.getReviewCount(),
                hotel.getMinPrice(),
                favorite.getCreatedAt());
    }

    private void ensurePubliclyEligible(Hotel hotel) {
        if (!"APPROVED".equals(normalize(hotel.getApprovalStatus()))
                || !"ACTIVE".equals(normalize(hotel.getOperationStatus()))) {
            throw propertyNotFound();
        }
    }

    private ResourceNotFoundException propertyNotFound() {
        return new ResourceNotFoundException("Property not found.");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "Property";
    }
}
