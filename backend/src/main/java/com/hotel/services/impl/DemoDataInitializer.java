package com.hotel.services.impl;

import com.hotel.entities.Hotel;
import com.hotel.entities.HotelService;
import com.hotel.entities.Location;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.HotelServiceRepository;
import com.hotel.repositories.LocationRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@Profile({"development", "demo", "test"})
@ConditionalOnProperty(name = "app.demo-data.legacy-seed", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataInitializer {

    private final LocationRepository locationRepository;
    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final HotelServiceRepository hotelServiceRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Order(200)
    @Transactional
    public void seedDemoData() {
        List<ProvinceSeed> provinces = List.of(
                province("1", "Hà Nội"),
                province("48", "Đà Nẵng"),
                province("82", "Đồng Tháp")
        );
        List<PropertySeed> properties = List.of(
                new PropertySeed("DEMO-HN-01", "Khách sạn Bình Minh Hồ Gươm", "Sunrise Hoan Kiem Hotel", "HOTEL", "18 Lê Thái Tổ", 21.0285, 105.8523, 5, 0, 0),
                new PropertySeed("DEMO-HN-02", "Nhà nghỉ Phúc Xá", "Phuc Xa Motel", "MOTEL", "42 Phúc Xá", 21.0452, 105.8481, 2, 0, 1),
                new PropertySeed("DEMO-HN-03", "Căn hộ Tây Hồ", "West Lake Apartment", "APARTMENT", "105 Trích Sài", 21.0583, 105.8192, 4, 0, 0),
                new PropertySeed("DEMO-HN-04", "Biệt thự Sóc Sơn Xanh", "Soc Son Green Villa", "VILLA", "12 Đường Núi Đôi", 21.2571, 105.8498, 4, 0, 1),
                new PropertySeed("DEMO-DN-01", "Khách sạn Sông Hàn", "Han River Hotel", "HOTEL", "88 Bạch Đằng", 16.0678, 108.2235, 4, 1, 0),
                new PropertySeed("DEMO-DN-02", "Sơn Trà Homestay", "Son Tra Homestay", "HOMESTAY", "25 Hoàng Sa", 16.0934, 108.2521, 3, 1, 1),
                new PropertySeed("DEMO-DN-03", "Khu nghỉ dưỡng Mỹ Khê", "My Khe Beach Resort", "RESORT", "120 Võ Nguyên Giáp", 16.0544, 108.2469, 5, 1, 0),
                new PropertySeed("DEMO-DT-01", "Khách sạn Ánh Dương Mỹ Tho", "Anh Duong My Tho Hotel", "HOTEL", "123 Lê Lợi, Mỹ Tho", 10.3600, 106.3600, 4, 2, 0),
                new PropertySeed("DEMO-DT-02", "Mekong Garden Homestay", "Mekong Garden Homestay", "HOMESTAY", "56 Đường ven sông Tiền", 10.3444, 106.3478, 3, 2, 1),
                new PropertySeed("DEMO-DT-03", "Biệt thự Sen Hồng Đồng Tháp", "Dong Thap Lotus Villa", "VILLA", "9 Nguyễn Huệ", 10.4581, 105.6332, 4, 2, 0)
        );

        for (int i = 0; i < properties.size(); i++) {
            PropertySeed seed = properties.get(i);
            ProvinceSeed province = provinces.get(seed.provinceIndex());
            Location ward = province.wards().get(seed.wardIndex() % province.wards().size());
            Hotel hotel = upsertHotel(seed, province.province(), ward, i);
            seedRoomType(hotel, "SINGLE", "Phòng đơn", "Single Room", "SINGLE", 1, 1, 1, 2,
                    BigDecimal.valueOf(350000L + (i % 5) * 50000L), List.of("101", "102"));
            seedRoomType(hotel, "DOUBLE", "Phòng đôi", "Double Room", "DOUBLE", 1, 2, 1, 3,
                    BigDecimal.valueOf(500000L + (i % 6) * 75000L), List.of("201", "202", "203"));
            seedRoomType(hotel, "FAMILY", "Phòng gia đình", "Family Room", "DOUBLE", 2, 4, 2, 6,
                    BigDecimal.valueOf(850000L + (i % 6) * 125000L), List.of("301"));
            seedService(hotel);
        }
    }

    private ProvinceSeed province(String sourceCode, String city) {
        Location province = locationRepository.findByLocationTypeAndSourceCode("PROVINCE", sourceCode)
                .orElseThrow(() -> new IllegalStateException("Thiếu tỉnh demo sourceCode=" + sourceCode));
        List<Location> wards = locationRepository.findByParentIdAndLocationTypeAndStatusOrderByNameViAsc(province.getId(), "WARD", "ACTIVE")
                .stream().limit(2).toList();
        if (wards.size() < 2) throw new IllegalStateException("Tỉnh demo phải có ít nhất 2 phường/xã: " + city);
        return new ProvinceSeed(province, wards, city);
    }

    private Hotel upsertHotel(PropertySeed seed, Location province, Location ward, int index) {
        Hotel hotel = hotelRepository.findByCode(seed.code()).orElseGet(Hotel::new);
        hotel.setCode(seed.code());
        hotel.setSlug(seed.code().toLowerCase());
        hotel.setName(seed.nameVi());
        hotel.setNameVi(seed.nameVi());
        hotel.setNameEn(seed.nameEn());
        hotel.setDescription("Cơ sở lưu trú demo phục vụ kiểm thử tìm kiếm, tồn phòng và đặt phòng.");
        hotel.setDescriptionVi("Cơ sở lưu trú demo có dữ liệu phòng vật lý, sức chứa và dịch vụ đầy đủ.");
        hotel.setDescriptionEn("Demo property with physical rooms, capacity and services.");
        hotel.setAddressLine(seed.address());
        hotel.setCity(province.getNameVi());
        hotel.setCountry("Việt Nam");
        hotel.setProvinceId(province.getId());
        hotel.setWardId(ward.getId());
        hotel.setLatitude(seed.latitude());
        hotel.setLongitude(seed.longitude());
        hotel.setPropertyType(seed.propertyType());
        hotel.setStarRating(seed.starRating());
        hotel.setMainImage(List.of(
                "/assets/properties/hotel-city-01.webp", "/assets/properties/homestay-01.webp",
                "/assets/properties/resort-01.webp", "/assets/properties/villa-01.webp",
                "/assets/properties/apartment-01.webp").get(index % 5));
        hotel.setMinPrice(350000D + (index % 5) * 50000D);
        hotel.setMaxPrice(850000D + (index % 6) * 125000D);
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel.setCheckinTime("14:00");
        hotel.setCheckoutTime("12:00");
        hotel.setPhone(String.format("090100%04d", index));
        hotel.setEmail(seed.code().toLowerCase() + "@demo.local");
        hotel.setAverageRating(null);
        hotel.setReviewCount(0);
        return hotelRepository.saveAndFlush(hotel);
    }

    private void seedRoomType(Hotel hotel, String code, String nameVi, String nameEn, String bedType,
                              int bedCount, int maxAdults, int maxChildren, int maxGuests,
                              BigDecimal price, List<String> roomNumbers) {
        RoomType roomType = roomTypeRepository.findByCodeAndHotelId(code, hotel.getId()).orElseGet(RoomType::new);
        roomType.setHotel(hotel);
        roomType.setCode(code);
        roomType.setNameVi(nameVi);
        roomType.setNameEn(nameEn);
        roomType.setDescriptionVi(nameVi + " với tiện nghi cơ bản và thông tin sức chứa rõ ràng.");
        roomType.setDescriptionEn(nameEn + " with standard amenities.");
        roomType.setBedType(bedType);
        roomType.setBedCount(bedCount);
        roomType.setMaxAdults(maxAdults);
        roomType.setMaxChildren(maxChildren);
        roomType.setMaxGuests(maxGuests);
        roomType.setMaxGuest(maxGuests);
        roomType.setBasePrice(price);
        roomType.setStatus("ACTIVE");
        roomType = roomTypeRepository.saveAndFlush(roomType);

        for (String roomNumber : roomNumbers) {
            Room room = roomRepository.findByHotelIdAndRoomNumber(hotel.getId(), roomNumber).orElseGet(Room::new);
            room.setHotel(hotel);
            room.setRoomType(roomType);
            room.setRoomNumber(roomNumber);
            room.setFloor(Integer.parseInt(roomNumber.substring(0, 1)));
            room.setStatus("AVAILABLE");
            room.setMaintenanceStatus("NONE");
            room.setMaxGuests(maxGuests);
            room.setDescriptionVi("Phòng " + roomNumber + " thuộc " + nameVi + ".");
            room.setDescriptionEn("Room " + roomNumber + " of " + nameEn + ".");
            roomRepository.save(room);
        }
    }

    private void seedService(Hotel hotel) {
        String code = hotel.getCode() + "-BREAKFAST";
        HotelService service = hotelServiceRepository.findByCode(code).orElseGet(HotelService::new);
        service.setCode(code);
        service.setHotel(hotel);
        service.setSystemService(false);
        service.setNameVi("Ăn sáng tại cơ sở");
        service.setNameEn("Property breakfast");
        service.setDescriptionVi("Suất ăn sáng tính theo số lượng thực tế sử dụng.");
        service.setDescriptionEn("Breakfast charged by actual quantity.");
        service.setPrice(new BigDecimal("120000"));
        service.setStatus("ACTIVE");
        hotelServiceRepository.save(service);
    }

    private record ProvinceSeed(Location province, List<Location> wards, String city) { }
    private record PropertySeed(String code, String nameVi, String nameEn, String propertyType, String address,
                                double latitude, double longitude, int starRating, int provinceIndex, int wardIndex) { }
}
