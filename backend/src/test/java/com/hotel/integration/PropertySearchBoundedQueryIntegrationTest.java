package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.dto.PropertySearchRequestDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyImage;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.services.impl.PropertySearchServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = BackendApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:t294-query-budget;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "payment.property.encryption-key=test-property-payment-encryption-key"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropertySearchBoundedQueryIntegrationTest {

    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private PropertyImageRepository propertyImageRepository;
    @Autowired private PropertySearchServiceImpl searchService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @BeforeAll
    void seedMaximumPage() {
        transactionTemplate.executeWithoutResult(status -> {
            List<Hotel> hotels = new ArrayList<>();
            for (int index = 1; index <= 100; index++) {
                Hotel hotel = new Hotel();
                hotel.setCode("T294-H-" + index);
                hotel.setSlug("t294-h-" + index);
                hotel.setName("T294 Hotel " + index);
                hotel.setNameVi("T294 Hotel " + index);
                hotel.setAddressLine(index + " Query Budget Street");
                hotel.setCity("Da Nang");
                hotel.setCountry("Viet Nam");
                hotel.setStatus("ACTIVE");
                hotel.setApprovalStatus("APPROVED");
                hotel.setOperationStatus("ACTIVE");
                hotel.setPropertyType("HOTEL");
                hotel.setReviewCount(index);
                hotel.setAverageRating(8.0);
                hotels.add(hotel);
            }
            hotels = hotelRepository.saveAllAndFlush(hotels);

            List<RoomType> roomTypes = new ArrayList<>();
            for (int index = 0; index < hotels.size(); index++) {
                RoomType roomType = new RoomType();
                roomType.setHotel(hotels.get(index));
                roomType.setCode("T294-RT-" + (index + 1));
                roomType.setNameVi("T294 Room " + (index + 1));
                roomType.setNameEn("T294 Room " + (index + 1));
                roomType.setMaxGuest(4);
                roomType.setMaxAdults(4);
                roomType.setMaxChildren(2);
                roomType.setMaxGuests(4);
                roomType.setBasePrice(BigDecimal.valueOf(500000L + index));
                roomType.setStatus("ACTIVE");
                roomTypes.add(roomType);
            }
            roomTypes = roomTypeRepository.saveAllAndFlush(roomTypes);

            List<Room> rooms = new ArrayList<>();
            List<PropertyImage> images = new ArrayList<>();
            for (int index = 0; index < hotels.size(); index++) {
                Room room = new Room();
                room.setHotel(hotels.get(index));
                room.setRoomType(roomTypes.get(index));
                room.setRoomNumber("T294-" + (index + 1));
                room.setFloor(1);
                room.setStatus("AVAILABLE");
                room.setHousekeepingStatus("CLEAN");
                room.setMaintenanceStatus("NONE");
                rooms.add(room);

                PropertyImage image = new PropertyImage();
                image.setHotel(hotels.get(index));
                image.setImageUrl("/t294/" + (index + 1) + ".webp");
                image.setIsPrimary(true);
                image.setSortOrder(0);
                image.setAltTextVi("T294 image " + (index + 1));
                images.add(image);
            }
            roomRepository.saveAllAndFlush(rooms);
            propertyImageRepository.saveAllAndFlush(images);
        });
        entityManager.clear();
    }

    @Test
    void statementCountRemainsBoundedForPageOneAndMaximumPageDatedAndUndated() {
        QueryMeasurement one = measure(request(1, null, null));
        QueryMeasurement hundred = measure(request(100, null, null));
        QueryMeasurement dated = measure(request(
                100, LocalDate.of(2032, 5, 10), LocalDate.of(2032, 5, 12)));

        assertThat(one.resultSize()).isEqualTo(1);
        assertThat(hundred.resultSize()).isEqualTo(100);
        assertThat(dated.resultSize()).isEqualTo(100);
        assertThat(hundred.statementCount()).isLessThanOrEqualTo(one.statementCount() + 1);
        // Dated availability adds fixed reservation and active-amendment aggregate queries.
        assertThat(dated.statementCount()).isLessThanOrEqualTo(hundred.statementCount() + 2);
        assertThat(dated.statementCount()).isLessThanOrEqualTo(8);
    }

    private QueryMeasurement measure(PropertySearchRequestDTO request) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        int resultSize = searchService.searchProperties(request).getContent().size();
        return new QueryMeasurement(resultSize, statistics.getPrepareStatementCount());
    }

    private PropertySearchRequestDTO request(int pageSize, LocalDate checkIn, LocalDate checkOut) {
        PropertySearchRequestDTO request = new PropertySearchRequestDTO();
        request.setPageNumber(1);
        request.setPageSize(pageSize);
        request.setSortBy("POPULAR");
        request.setAdultCount(2);
        request.setChildCount(0);
        request.setRoomCount(1);
        request.setCheckInDate(checkIn == null ? null : checkIn.toString());
        request.setCheckOutDate(checkOut == null ? null : checkOut.toString());
        return request;
    }

    private record QueryMeasurement(int resultSize, long statementCount) { }
}
