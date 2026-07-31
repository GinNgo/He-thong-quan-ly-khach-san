package com.hotel.propertycommerce.invoice;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PropertyInvoiceRepository extends JpaRepository<PropertyInvoice, Long> {

    Optional<PropertyInvoice> findByInvoiceNumber(String invoiceNumber);

    Optional<PropertyInvoice> findByIdAndHotelId(Long id, Long hotelId);

    Optional<PropertyInvoice> findByReservationIdAndStatus(
            Long reservationId,
            PropertyInvoice.Status status);

    boolean existsByReservationIdAndStatus(Long reservationId, PropertyInvoice.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from PropertyInvoice invoice where invoice.id = :id")
    Optional<PropertyInvoice> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from PropertyInvoice invoice "
            + "where invoice.id = :id and invoice.hotel.id = :hotelId")
    Optional<PropertyInvoice> findByIdForUpdate(
            @Param("id") Long id,
            @Param("hotelId") Long hotelId);
}
