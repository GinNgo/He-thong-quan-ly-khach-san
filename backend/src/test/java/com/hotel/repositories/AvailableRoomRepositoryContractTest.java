package com.hotel.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AvailableRoomRepositoryContractTest {

    @Test
    void lookupScopesPropertyAndUsesImmutableAssignmentStayDates() throws Exception {
        Method method = RoomRepository.class.getMethod(
                "findAvailableRoomsByRoomTypeAndDate",
                Long.class,
                Long.class,
                List.class,
                List.class,
                LocalDate.class,
                LocalDate.class);
        String query = method.getAnnotation(Query.class).value();

        assertThat(query).contains("room.hotel.id = :hotelId");
        assertThat(query).contains("coalesce(assignment.stayStartDate, reservation.checkInDate)");
        assertThat(query).contains("coalesce(assignment.stayEndDate, reservation.checkOutDate)");
        assertThat(query).contains("assignment.status = 'ASSIGNED'");
        assertThat(query).contains("order by room.floor, room.roomNumber, room.id");
        assertThat(method.getReturnType()).isEqualTo(List.class);
    }
}
