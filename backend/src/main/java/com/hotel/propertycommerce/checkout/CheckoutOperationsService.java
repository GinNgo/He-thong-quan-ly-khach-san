package com.hotel.propertycommerce.checkout;

import com.hotel.entities.HousekeepingTask;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationRoom;
import com.hotel.entities.Room;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.repositories.HousekeepingTaskRepository;
import com.hotel.repositories.ReservationRoomRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.services.RoomStatePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CheckoutOperationsService {

    private static final String ASSIGNED = "ASSIGNED";
    private static final String RELEASED = "RELEASED";

    private final ReservationRoomRepository reservationRoomRepository;
    private final RoomRepository roomRepository;
    private final HousekeepingTaskRepository housekeepingTaskRepository;
    private final Clock clock;

    @Autowired
    public CheckoutOperationsService(
            ReservationRoomRepository reservationRoomRepository,
            RoomRepository roomRepository,
            HousekeepingTaskRepository housekeepingTaskRepository) {
        this(reservationRoomRepository, roomRepository, housekeepingTaskRepository, Clock.systemUTC());
    }

    CheckoutOperationsService(
            ReservationRoomRepository reservationRoomRepository,
            RoomRepository roomRepository,
            HousekeepingTaskRepository housekeepingTaskRepository,
            Clock clock) {
        this.reservationRoomRepository = reservationRoomRepository;
        this.roomRepository = roomRepository;
        this.housekeepingTaskRepository = housekeepingTaskRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CheckoutOperationsResult apply(Reservation reservation) {
        validateReservation(reservation);
        List<ReservationRoom> assignments = reservationRoomRepository
                .findCheckoutAssignmentsByReservationIdForUpdate(reservation.getId());
        if (assignments.isEmpty()) {
            throw new IllegalStateException("Checkout requires room assignment evidence.");
        }

        List<Long> roomIds = assignments.stream()
                .map(ReservationRoom::getRoom)
                .map(Room::getId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, Room> lockedRooms = new LinkedHashMap<>();
        roomRepository.findAllByIdForUpdate(roomIds).forEach(room -> lockedRooms.put(room.getId(), room));
        if (lockedRooms.size() != roomIds.size()) {
            throw concurrent("An assigned room changed during checkout; retry safely.");
        }

        LocalDateTime occurredAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<ReservationRoom> changedAssignments = new ArrayList<>();
        List<Room> changedRooms = new ArrayList<>();
        List<HousekeepingTask> createdTasks = new ArrayList<>();
        for (ReservationRoom assignment : assignments) {
            Room room = lockedRooms.get(assignment.getRoom().getId());
            validateOwnership(reservation, room);
            if (RELEASED.equals(assignment.getStatus())) {
                if (assignment.getReleasedAt() == null) {
                    throw concurrent("Released room assignment is missing checkout evidence.");
                }
                continue;
            }
            if (!ASSIGNED.equals(assignment.getStatus())) {
                throw concurrent("Room assignment changed to an unsupported checkout state.");
            }

            RoomStatePolicy.checkout(room);
            assignment.setStatus(RELEASED);
            assignment.setReleasedAt(occurredAt);
            changedAssignments.add(assignment);
            changedRooms.add(room);

            String effectKey = checkoutEffectKey(reservation.getId(), room.getId());
            if (housekeepingTaskRepository.findByHotelIdAndCheckoutEffectKey(
                    reservation.getHotel().getId(), effectKey).isEmpty()) {
                HousekeepingTask task = new HousekeepingTask();
                task.setHotel(reservation.getHotel());
                task.setRoom(room);
                task.setReservation(reservation);
                task.setStatus("PENDING");
                task.setNote("Dọn phòng sau check-out booking #" + reservation.getId());
                task.setCheckoutEffectKey(effectKey);
                createdTasks.add(housekeepingTaskRepository.saveAndFlush(task));
            }
        }

        if (!changedAssignments.isEmpty()) {
            reservationRoomRepository.saveAllAndFlush(changedAssignments);
            roomRepository.saveAllAndFlush(changedRooms);
        }
        return new CheckoutOperationsResult(
                List.copyOf(roomIds),
                changedAssignments.size(),
                createdTasks.size());
    }

    static String checkoutEffectKey(Long reservationId, Long roomId) {
        if (reservationId == null || roomId == null) {
            throw new IllegalArgumentException("Reservation and room IDs are required for checkout idempotency.");
        }
        return "CHECKOUT:" + reservationId + ":ROOM:" + roomId;
    }

    private void validateReservation(Reservation reservation) {
        if (reservation == null || reservation.getId() == null
                || reservation.getHotel() == null || reservation.getHotel().getId() == null) {
            throw new IllegalArgumentException("Checkout reservation ownership is required.");
        }
    }

    private void validateOwnership(Reservation reservation, Room room) {
        if (room == null || room.getHotel() == null
                || !reservation.getHotel().getId().equals(room.getHotel().getId())) {
            throw concurrent("An assigned room no longer belongs to the checkout property.");
        }
    }

    private FinancialException concurrent(String message) {
        return new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION, message);
    }

    public record CheckoutOperationsResult(
            List<Long> roomIds,
            int releasedAssignmentCount,
            int createdHousekeepingTaskCount) {
    }
}
