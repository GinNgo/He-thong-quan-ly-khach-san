package com.hotel.propertycommerce.booking.staff;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "staff_booking_quotes", uniqueConstraints =
        @UniqueConstraint(name = "uq_staff_booking_quotes_public_id", columnNames = "public_id"))
@Getter
@Setter
public class StaffBookingQuote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, length = 36) private String publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "hotel_id") private Hotel hotel;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id") private User customer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_type_id") private RoomType roomType;
    @Column(name = "check_in_date", nullable = false) private LocalDate checkInDate;
    @Column(name = "check_out_date", nullable = false) private LocalDate checkOutDate;
    @Column(nullable = false) private Integer quantity;
    @Column(nullable = false) private Integer adults;
    @Column(nullable = false) private Integer children;
    @Column(name = "payment_method", nullable = false, length = 40) private String paymentMethod;
    @Column(name = "special_requests", length = 1000) private String specialRequests;
    @Column(name = "base_price", nullable = false, precision = 19, scale = 0) private BigDecimal basePrice;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 0) private BigDecimal totalAmount;
    @Column(name = "deposit_amount", nullable = false, precision = 19, scale = 0) private BigDecimal depositAmount;
    @Column(name = "payment_config_id", nullable = false) private Long paymentConfigurationId;
    @Column(name = "payment_config_version", nullable = false) private Long paymentConfigurationVersion;
    @Column(name = "available_rooms", nullable = false) private Long availableRooms;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(nullable = false, length = 20) private String status;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reservation_id") private Reservation reservation;
    @Version @Column(nullable = false) private Long version;
}
