package com.hotel.repositories;

import com.hotel.entities.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByStatus(String status);
    List<Room> findByFloor(Integer floor);
    List<Room> findByRoomTypeId(Long roomTypeId);
    List<Room> findByHotelId(Long hotelId);
    List<Room> findByHotelIdIn(java.util.Collection<Long> hotelIds);
    java.util.Optional<Room> findByHotelIdAndRoomNumber(Long hotelId, String roomNumber);
    long countByRoomTypeId(Long roomTypeId);
    long countByHotelId(Long hotelId);
    long countByHotelIdIn(java.util.Collection<Long> hotelIds);
    long countByHotelIdAndStatus(Long hotelId, String status);
    long countByHotelIdAndHousekeepingStatus(Long hotelId, String housekeepingStatus);

    @Query("""
            select count(room)
            from Room room
            where room.roomType.id = :roomTypeId
              and room.status not in :excludedRoomStatuses
              and (room.maintenanceStatus is null or room.maintenanceStatus = 'NONE')
            """)
    long countBookableRoomsByRoomTypeId(
            @Param("roomTypeId") Long roomTypeId,
            @Param("excludedRoomStatuses") List<String> excludedRoomStatuses
    );

    @Query("""
            select room
            from Room room
            where room.roomType.id = :roomTypeId
              and room.status not in :excludedRoomStatuses
              and (room.maintenanceStatus is null or room.maintenanceStatus = 'NONE')
              and room.id not in (
                  select assignment.room.id
                  from ReservationRoom assignment
                  join assignment.reservationDetail detail
                  join detail.reservation reservation
                  where detail.roomType.id = :roomTypeId
                    and assignment.status = 'ASSIGNED'
                    and reservation.status not in :excludedReservationStatuses
                    and reservation.checkInDate < :checkOut
                    and reservation.checkOutDate > :checkIn
              )
            order by room.id
            """)
    List<Room> findAvailableRoomsByRoomTypeAndDate(
            @Param("roomTypeId") Long roomTypeId,
            @Param("excludedRoomStatuses") List<String> excludedRoomStatuses,
            @Param("excludedReservationStatuses") List<String> excludedReservationStatuses,
            @Param("checkIn") java.time.LocalDate checkIn,
            @Param("checkOut") java.time.LocalDate checkOut
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select room
            from Room room
            where room.roomType.id = :roomTypeId
              and room.status not in :excludedRoomStatuses
              and (room.maintenanceStatus is null or room.maintenanceStatus = 'NONE')
              and room.id not in (
                  select assignment.room.id
                  from ReservationRoom assignment
                  join assignment.reservationDetail detail
                  join detail.reservation reservation
                  where detail.roomType.id = :roomTypeId
                    and assignment.status = 'ASSIGNED'
                    and reservation.status not in :excludedReservationStatuses
                    and reservation.checkInDate < :checkOut
                    and reservation.checkOutDate > :checkIn
              )
            order by room.id
            """)
    List<Room> findAvailableRoomsByRoomTypeAndDateForUpdate(
            @Param("roomTypeId") Long roomTypeId,
            @Param("excludedRoomStatuses") List<String> excludedRoomStatuses,
            @Param("excludedReservationStatuses") List<String> excludedReservationStatuses,
            @Param("checkIn") java.time.LocalDate checkIn,
            @Param("checkOut") java.time.LocalDate checkOut
    );
}
