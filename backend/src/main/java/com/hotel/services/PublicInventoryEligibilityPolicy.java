package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.RoomType;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class PublicInventoryEligibilityPolicy {

    private final HotelRepository hotelRepository;
    private final Environment environment;
    private final boolean allowPublicDemo;

    public PublicInventoryEligibilityPolicy(
            HotelRepository hotelRepository,
            Environment environment,
            @Value("${app.demo-data.allow-public-demo:false}") boolean allowPublicDemo) {
        this.hotelRepository = hotelRepository;
        this.environment = environment;
        this.allowPublicDemo = allowPublicDemo;
    }

    public Hotel requirePublicProperty(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("The requested property is not publicly available."));
        if (!isPublicProperty(hotel)) {
            throw new ResourceNotFoundException("The requested property is not publicly available.");
        }
        return hotel;
    }

    public boolean isPubliclySellable(RoomType roomType) {
        return roomType != null
                && "ACTIVE".equals(roomType.getStatus())
                && isPublicProperty(roomType.getHotel());
    }

    public void requireSellableRoomTypeForBooking(RoomType roomType) {
        if (!isPubliclySellable(roomType)) {
            throw new IllegalStateException("The selected room type is no longer available for booking.");
        }
    }

    public String publicSearchPredicate(String hotelAlias) {
        String alias = Objects.requireNonNull(hotelAlias, "hotelAlias");
        if (!alias.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("hotelAlias must be a simple SQL alias.");
        }

        String predicate = alias + ".approval_status='APPROVED' AND "
                + alias + ".operation_status='ACTIVE'";
        if (hidesDemoInventory()) {
            predicate += " AND COALESCE(" + alias + ".is_demo,0)=0";
        }
        return predicate;
    }

    private boolean isPublicProperty(Hotel hotel) {
        if (hotel == null
                || !"APPROVED".equals(hotel.getApprovalStatus())
                || !"ACTIVE".equals(hotel.getOperationStatus())) {
            return false;
        }
        return !Boolean.TRUE.equals(hotel.getIsDemo())
                || !hidesDemoInventory();
    }

    private boolean hidesDemoInventory() {
        return !allowPublicDemo && environment.acceptsProfiles(Profiles.of("production"));
    }
}
