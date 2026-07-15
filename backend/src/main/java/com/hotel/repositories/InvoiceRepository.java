package com.hotel.repositories;

import com.hotel.entities.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByInvoiceCode(String invoiceCode);
    Optional<Invoice> findByReservationId(Long reservationId);
    List<Invoice> findByReservationUserIdOrderByIssueDateDesc(Long userId);
}
