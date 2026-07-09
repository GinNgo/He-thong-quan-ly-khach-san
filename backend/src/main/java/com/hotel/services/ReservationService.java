package com.hotel.services;

import com.hotel.dtos.ReservationDTO;
import com.hotel.dtos.ReservationDetailDTO;
import com.hotel.dtos.ReservationRequest;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.Room;
import com.hotel.entities.User;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationDetailRepository reservationDetailRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomAvailabilityService roomAvailabilityService;

    public ReservationService(ReservationRepository reservationRepository,
                              ReservationDetailRepository reservationDetailRepository,
                              RoomRepository roomRepository,
                              UserRepository userRepository,
                              RoomAvailabilityService roomAvailabilityService) {
        this.reservationRepository = reservationRepository;
        this.reservationDetailRepository = reservationDetailRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.roomAvailabilityService = roomAvailabilityService;
    }

    @Transactional
    public Reservation createReservation(String username, ReservationRequest request) {
        validateReservationRequest(request);

        if (username == null || username.isEmpty()) {
            username = "guest";
        }

        User user = userRepository.findByUsername(username)
                .orElseGet(() -> createGuestUser(request));

        Room room = roomAvailabilityService.findFirstAvailableRoomForBooking(
                request.getRoomTypeId(),
                request.getCheckInDate(),
                request.getCheckOutDate(),
                request.getGuests()
        );

        long nights = roomAvailabilityService.getNights(request.getCheckInDate(), request.getCheckOutDate());
        BigDecimal totalAmount = roomAvailabilityService.calculateTotal(room.getRoomType().getBasePrice(), nights);

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setRoom(room);
        reservation.setHotel(room.getRoomType().getHotel());
        reservation.setCheckInDate(request.getCheckInDate());
        reservation.setCheckOutDate(request.getCheckOutDate());
        reservation.setGuests(request.getGuests());
        reservation.setStatus("PENDING_PAYMENT");
        reservation.setPaymentMethod(request.getPaymentMethod());
        reservation.setSpecialRequests(request.getSpecialRequests());
        reservation.setTotalAmount(totalAmount);
        reservation.setCreatedAt(LocalDateTime.now());

        Reservation savedReservation = reservationRepository.save(reservation);

        ReservationDetail detail = new ReservationDetail();
        detail.setReservation(savedReservation);
        detail.setRoom(room);
        detail.setPrice(room.getRoomType().getBasePrice());
        reservationDetailRepository.save(detail);

        return savedReservation;
    }

    @Transactional(readOnly = true)
    public List<ReservationDTO> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReservationDTO getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));
        return mapToDTO(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationDTO> getMyReservations(String username) {
        return getAllReservations().stream()
                .filter(r -> r.getUsername().equals(username))
                .collect(Collectors.toList());
    }

    @Transactional
    public Reservation updateReservationStatus(Long id, String status) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        reservation.setStatus(status);

        if ("CHECKED_IN".equals(status)) {
            reservationDetailRepository.findByReservationId(id).forEach(detail -> {
                Room room = detail.getRoom();
                room.setStatus("OCCUPIED");
                roomRepository.save(room);
            });
        } else if ("CHECKED_OUT".equals(status) || "CANCELLED".equals(status)) {
            reservationDetailRepository.findByReservationId(id).forEach(detail -> {
                Room room = detail.getRoom();
                room.setStatus("AVAILABLE");
                roomRepository.save(room);
            });
        }

        return reservationRepository.save(reservation);
    }

    private User createGuestUser(ReservationRequest request) {
        User guest = new User();
        guest.setUsername("guest_" + System.currentTimeMillis());
        guest.setEmail(guest.getUsername() + "@guest.com");
        guest.setPasswordHash("NOPASSWORD");
        String fullName = (request.getFirstName() != null ? request.getFirstName() : "") + " "
                + (request.getLastName() != null ? request.getLastName() : "");
        guest.setFullName(fullName.trim().isEmpty() ? "Guest User" : fullName.trim());
        guest.setPhone(request.getPhone());
        guest.setStatus("GUEST");
        return userRepository.save(guest);
    }

    private void validateReservationRequest(ReservationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Thiếu thông tin đặt phòng.");
        }

        if (request.getRoomTypeId() == null) {
            throw new IllegalArgumentException("Vui lòng chọn loại phòng.");
        }

        if (request.getGuests() == null || request.getGuests() < 1) {
            throw new IllegalArgumentException("Số khách phải lớn hơn 0.");
        }
    }

    private ReservationDTO mapToDTO(Reservation reservation) {
        ReservationDTO dto = new ReservationDTO();
        dto.setId(reservation.getId());
        dto.setUserId(reservation.getUser().getId());
        dto.setUsername(reservation.getUser().getUsername());
        dto.setUserFullName(reservation.getUser().getFullName());
        dto.setCheckInDate(reservation.getCheckInDate());
        dto.setCheckOutDate(reservation.getCheckOutDate());
        dto.setGuests(reservation.getGuests());
        dto.setTotalAmount(reservation.getTotalAmount());
        dto.setStatus(reservation.getStatus());
        dto.setPaymentMethod(reservation.getPaymentMethod());
        dto.setSpecialRequests(reservation.getSpecialRequests());

        List<ReservationDetailDTO> detailDTOs = reservationDetailRepository
                .findByReservationId(reservation.getId()).stream()
                .map(this::mapDetailToDTO)
                .collect(Collectors.toList());

        dto.setDetails(detailDTOs);
        return dto;
    }

    private ReservationDetailDTO mapDetailToDTO(ReservationDetail detail) {
        ReservationDetailDTO detailDto = new ReservationDetailDTO();
        detailDto.setId(detail.getId());
        detailDto.setReservationId(detail.getReservation().getId());
        detailDto.setRoomId(detail.getRoom().getId());
        detailDto.setRoomNumber(detail.getRoom().getRoomNumber());
        detailDto.setPriceAtBooking(detail.getPrice());
        return detailDto;
    }
}
