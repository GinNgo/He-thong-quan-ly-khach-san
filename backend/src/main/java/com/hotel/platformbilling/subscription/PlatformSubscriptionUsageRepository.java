package com.hotel.platformbilling.subscription;

import com.hotel.entities.UserProperty;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.LinkedHashMap;
import java.util.Map;

public interface PlatformSubscriptionUsageRepository extends Repository<UserProperty, Long> {

    @Query("select count(mapping) from UserProperty mapping "
            + "where mapping.user.id = :ownerId and mapping.status = 'ACTIVE' "
            + "and mapping.relationshipType = 'OWNER'")
    long countActiveOwnedProperties(@Param("ownerId") Long ownerId);

    @Query("select count(roomType) from RoomType roomType where roomType.hotel.id = :hotelId")
    long countRoomTypes(@Param("hotelId") Long hotelId);

    @Query("select count(room) from Room room where room.hotel.id = :hotelId")
    long countRooms(@Param("hotelId") Long hotelId);

    @Query("select count(image) from PropertyImage image where image.hotel.id = :hotelId")
    long countPropertyImages(@Param("hotelId") Long hotelId);

    @Query("select count(image) from RoomTypeImage image where image.roomType.hotel.id = :hotelId")
    long countRoomTypeImages(@Param("hotelId") Long hotelId);

    @Query("select count(image) from RoomImage image where image.room.hotel.id = :hotelId")
    long countRoomImages(@Param("hotelId") Long hotelId);

    @Query("select count(distinct mapping.user.id) from UserProperty mapping "
            + "where mapping.hotel.id = :hotelId and mapping.status = 'ACTIVE' "
            + "and mapping.relationshipType <> 'OWNER'")
    long countActiveStaff(@Param("hotelId") Long hotelId);

    default Long currentUsage(String code, Long ownerId, Long hotelId) {
        return switch (code) {
            case "MAX_PROPERTIES" -> countActiveOwnedProperties(ownerId);
            case "MAX_ROOM_TYPES" -> countRoomTypes(hotelId);
            case "MAX_ROOMS" -> countRooms(hotelId);
            case "MAX_IMAGES" -> countPropertyImages(hotelId) + countRoomTypeImages(hotelId) + countRoomImages(hotelId);
            case "MAX_STAFF" -> countActiveStaff(hotelId);
            default -> null;
        };
    }

    default Map<String, Long> snapshot(Long ownerId, Long hotelId) {
        Map<String, Long> usage = new LinkedHashMap<>();
        for (String code : java.util.List.of("MAX_PROPERTIES", "MAX_ROOM_TYPES", "MAX_ROOMS", "MAX_IMAGES", "MAX_STAFF")) {
            usage.put(code, currentUsage(code, ownerId, hotelId));
        }
        return usage;
    }
}
