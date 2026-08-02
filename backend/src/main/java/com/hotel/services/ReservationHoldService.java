package com.hotel.services;

import com.hotel.domain.lifecycle.ReservationHoldStatus;
import com.hotel.domain.lifecycle.ReservationStatus;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationHold;
import com.hotel.entities.RoomType;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationHoldRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.RoomTypeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationHoldService {

    private final ReservationHoldRepository holdRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationDetailRepository reservationDetailRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final long ttlMinutes;

    public ReservationHoldService(
            ReservationHoldRepository holdRepository,
            ReservationRepository reservationRepository,
            ReservationDetailRepository reservationDetailRepository,
            RoomTypeRepository roomTypeRepository,
            @Value("${app.reservation-hold.ttl-minutes:15}") long ttlMinutes) {
        this.holdRepository = holdRepository;
        this.reservationRepository = reservationRepository;
        this.reservationDetailRepository = reservationDetailRepository;
        this.roomTypeRepository = roomTypeRepository;
        if (ttlMinutes < 1) {
            throw new IllegalArgumentException("Reservation hold TTL must be at least one minute.");
        }
        this.ttlMinutes = ttlMinutes;
    }

    @Transactional
    public ReservationHold createHold(
            Long reservationId,
            Long roomTypeId,
            int quantity,
            String holdKey) {
        return createHold(reservationId, roomTypeId, quantity, holdKey, LocalDateTime.now());
    }

    @Transactional
    public ReservationHold createHold(
            Long reservationId,
            Long roomTypeId,
            int quantity,
            String holdKey,
            LocalDateTime now) {
        validateHoldKey(holdKey);
        if (quantity < 1) {
            throw new IllegalArgumentException("Reservation hold quantity must be positive.");
        }

        String normalizedKey = holdKey.trim();
        ReservationHold existing = holdRepository.findByHoldKeyForUpdate(normalizedKey).orElse(null);
        if (existing != null) {
            validateReplay(existing, reservationId, roomTypeId, quantity);
            return existing;
        }

        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        RoomType roomType = roomTypeRepository.findByIdForUpdate(roomTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Room type not found."));
        validateReservation(reservation, roomType, quantity);

        ReservationHold active = holdRepository.findActiveByReservationIdForUpdate(reservationId).orElse(null);
        if (active != null) {
            throw new IllegalStateException("Reservation already has an active hold.");
        }

        ReservationHold hold = new ReservationHold();
        hold.setReservation(reservation);
        hold.setRoomType(roomType);
        hold.setHotel(reservation.getHotel());
        hold.setQuantity(quantity);
        hold.setHoldKey(normalizedKey);
        hold.setStatus(ReservationHoldStatus.ACTIVE.name());
        hold.setExpiresAt(now.plusMinutes(ttlMinutes));
        return holdRepository.save(hold);
    }

    @Transactional
    public boolean consumeActiveHold(Long reservationId, LocalDateTime now) {
        return completeActiveHold(reservationId, ReservationHoldStatus.CONSUMED, now);
    }

    @Transactional
    public boolean releaseActiveHold(Long reservationId, LocalDateTime now) {
        return completeActiveHold(reservationId, ReservationHoldStatus.RELEASED, now);
    }

    @Transactional
    public boolean expireActiveHold(Long reservationId, LocalDateTime now) {
        return completeActiveHold(reservationId, ReservationHoldStatus.EXPIRED, now);
    }

    @Transactional
    public int expireDueHolds(LocalDateTime now) {
        List<Long> dueHoldIds = holdRepository.findExpiredActiveIds(now);
        int processed = 0;
        for (Long holdId : dueHoldIds) {
            Long reservationId = holdRepository.findReservationIdById(holdId).orElse(null);
            if (reservationId == null) {
                continue;
            }

            Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                    .orElseThrow(() -> new IllegalStateException("Reservation for hold no longer exists."));
            ReservationHold hold = holdRepository.findByIdForUpdate(holdId).orElse(null);
            if (hold == null
                    || !ReservationHoldStatus.ACTIVE.name().equals(hold.getStatus())
                    || hold.getExpiresAt().isAfter(now)) {
                continue;
            }
            ReservationStatus reservationStatus = ReservationStatus.fromStorage(reservation.getStatus());

            if (reservationStatus == ReservationStatus.PENDING_PAYMENT) {
                reservation.setStatus(ReservationStatus.EXPIRED.name());
                reservationRepository.save(reservation);
                completeHold(hold, ReservationHoldStatus.EXPIRED, now);
            } else if (reservationStatus == ReservationStatus.EXPIRED) {
                completeHold(hold, ReservationHoldStatus.EXPIRED, now);
            } else if (reservationStatus == ReservationStatus.CANCELLED
                    || reservationStatus == ReservationStatus.REJECTED
                    || reservationStatus == ReservationStatus.NO_SHOW) {
                completeHold(hold, ReservationHoldStatus.RELEASED, now);
            } else {
                completeHold(hold, ReservationHoldStatus.CONSUMED, now);
            }
            processed++;
        }
        return processed;
    }

    private boolean completeActiveHold(
            Long reservationId,
            ReservationHoldStatus target,
            LocalDateTime now) {
        ReservationHold hold = holdRepository.findActiveByReservationIdForUpdate(reservationId).orElse(null);
        if (hold == null) {
            return false;
        }
        completeHold(hold, target, now);
        return true;
    }

    private void completeHold(ReservationHold hold, ReservationHoldStatus target, LocalDateTime now) {
        hold.setStatus(target.name());
        hold.setReleasedAt(now);
        holdRepository.save(hold);
    }

    private void validateReservation(Reservation reservation, RoomType roomType, int quantity) {
        if (ReservationStatus.fromStorage(reservation.getStatus()) != ReservationStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending-payment reservations can receive an active hold.");
        }
        if (reservation.getHotel() == null || roomType.getHotel() == null
                || !reservation.getHotel().getId().equals(roomType.getHotel().getId())) {
            throw new IllegalArgumentException("Reservation hold must stay within one hotel.");
        }

        List<ReservationDetail> matchingDetails = reservationDetailRepository.findByReservationId(reservation.getId())
                .stream()
                .filter(detail -> detail.getRoomType() != null
                        && detail.getRoomType().getId().equals(roomType.getId()))
                .toList();
        int reservedQuantity = matchingDetails.stream()
                .mapToInt(detail -> detail.getQuantity() == null ? 1 : detail.getQuantity())
                .sum();
        if (reservedQuantity != quantity) {
            throw new IllegalArgumentException("Reservation hold quantity must match reservation details.");
        }
    }

    private void validateReplay(ReservationHold existing, Long reservationId, Long roomTypeId, int quantity) {
        if (!existing.getReservation().getId().equals(reservationId)
                || !existing.getRoomType().getId().equals(roomTypeId)
                || !existing.getQuantity().equals(quantity)) {
            throw new IllegalArgumentException("Hold key belongs to another reservation payload.");
        }
    }

    private void validateHoldKey(String holdKey) {
        if (holdKey == null || holdKey.isBlank()) {
            throw new IllegalArgumentException("Reservation hold key is required.");
        }
        if (holdKey.trim().length() > 120) {
            throw new IllegalArgumentException("Reservation hold key is too long.");
        }
    }
}
