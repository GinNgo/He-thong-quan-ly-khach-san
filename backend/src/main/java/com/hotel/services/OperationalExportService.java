package com.hotel.services;

import com.hotel.entities.HousekeepingTask;
import com.hotel.entities.Reservation;
import com.hotel.entities.Room;
import com.hotel.entities.User;
import com.hotel.repositories.HousekeepingTaskRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperationalExportService {
    public enum Dataset { RESERVATIONS, CUSTOMERS, ROOMS, HOUSEKEEPING }

    private final PropertyAccessService propertyAccessService;
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final HousekeepingTaskRepository housekeepingTaskRepository;

    @Transactional(readOnly = true)
    public Artifact export(Long propertyId, Dataset dataset, String status, LocalDate from, LocalDate to) {
        propertyAccessService.requireAssignedHotel(propertyId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Export from date must be on or before the to date.");
        }
        List<List<Object>> rows = switch (dataset) {
            case RESERVATIONS -> reservations(propertyId, status, from, to);
            case CUSTOMERS -> customers(propertyId, status, from, to);
            case ROOMS -> rooms(propertyId, status);
            case HOUSEKEEPING -> housekeeping(propertyId, status, from, to);
        };
        String content = rows.stream().map(this::csvRow).reduce((left, right) -> left + "\n" + right).orElse("") + "\n";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new Artifact(dataset.name().toLowerCase(Locale.ROOT) + "-property-" + propertyId + ".csv",
                bytes, Math.max(0, rows.size() - 1), sha256(bytes));
    }

    private List<List<Object>> reservations(Long propertyId, String status, LocalDate from, LocalDate to) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("reservationRef", "customerRef", "checkIn", "checkOut", "guests", "status", "roomNumber"));
        reservationRepository.findByHotelIdOrderByIdDesc(propertyId).stream()
                .filter(item -> matches(item.getStatus(), status))
                .filter(item -> inRange(item.getCheckInDate(), from, to))
                .forEach(item -> rows.add(List.of(ref("RES", item.getId()), ref("CUS", item.getUser().getId()),
                        item.getCheckInDate(), item.getCheckOutDate(), value(item.getGuests()), value(item.getStatus()),
                        item.getRoom() == null ? "" : item.getRoom().getRoomNumber())));
        return rows;
    }

    private List<List<Object>> customers(Long propertyId, String status, LocalDate from, LocalDate to) {
        Map<Long, User> customers = new LinkedHashMap<>();
        reservationRepository.findByHotelIdOrderByIdDesc(propertyId).stream()
                .filter(item -> matches(item.getStatus(), status))
                .filter(item -> inRange(item.getCheckInDate(), from, to))
                .forEach(item -> customers.putIfAbsent(item.getUser().getId(), item.getUser()));
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("customerRef", "maskedEmail", "maskedPhone", "accountStatus"));
        customers.values().forEach(user -> rows.add(List.of(ref("CUS", user.getId()), maskEmail(user.getEmail()),
                maskPhone(user.getPhone()), value(user.getStatus()))));
        return rows;
    }

    private List<List<Object>> rooms(Long propertyId, String status) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("roomRef", "roomNumber", "roomType", "floor", "status", "housekeepingStatus", "maintenanceStatus", "maxGuests"));
        roomRepository.findByHotelId(propertyId).stream().filter(item -> matches(item.getStatus(), status))
                .forEach(item -> rows.add(List.of(ref("ROOM", item.getId()), item.getRoomNumber(),
                        value(item.getRoomType().getCode()), value(item.getFloor()), value(item.getStatus()), value(item.getHousekeepingStatus()),
                        value(item.getMaintenanceStatus()), value(item.getMaxGuests()))));
        return rows;
    }

    private List<List<Object>> housekeeping(Long propertyId, String status, LocalDate from, LocalDate to) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("taskRef", "roomNumber", "reservationRef", "status", "assignedUserRef", "assignedAt", "completedAt"));
        housekeepingTaskRepository.findByHotelIdOrderByCreatedAtDesc(propertyId).stream()
                .filter(item -> matches(item.getStatus(), status))
                .filter(item -> inRange(item.getCreatedAt() == null ? null : item.getCreatedAt().toLocalDate(), from, to))
                .forEach(item -> rows.add(taskRow(item)));
        return rows;
    }

    private List<Object> taskRow(HousekeepingTask item) {
        return List.of(ref("TASK", item.getId()), item.getRoom().getRoomNumber(),
                item.getReservation() == null ? "" : ref("RES", item.getReservation().getId()), value(item.getStatus()),
                item.getAssignedTo() == null ? "" : ref("USR", item.getAssignedTo().getId()),
                value(item.getAssignedAt()), value(item.getCompletedAt()));
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank() || actual.equalsIgnoreCase(expected.trim());
    }

    private boolean inRange(LocalDate value, LocalDate from, LocalDate to) {
        if (value == null) return from == null && to == null;
        return (from == null || !value.isBefore(from)) && (to == null || !value.isAfter(to));
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "";
        String[] parts = email.split("@", 2);
        return parts[0].substring(0, Math.min(2, parts[0].length())) + "***@" + parts[1];
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return "";
        String digits = phone.replaceAll("\\D", "");
        return digits.length() <= 4 ? "****" : "***" + digits.substring(digits.length() - 4);
    }

    private String ref(String prefix, Long id) { return prefix + "-" + id; }
    private String value(Object value) { return value == null ? "" : value.toString(); }

    private String csvRow(List<Object> values) {
        return values.stream().map(this::csvValue).reduce((left, right) -> left + "," + right).orElse("");
    }

    private String csvValue(Object value) { return "\"" + this.value(value).replace("\"", "\"\"") + "\""; }

    private String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder();
            for (byte item : hash) result.append(String.format(Locale.ROOT, "%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record Artifact(String filename, byte[] content, long rowCount, String checksum) { }
}
