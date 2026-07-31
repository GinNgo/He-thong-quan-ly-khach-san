package com.hotel.propertycommerce.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PropertyCreditNoteLineRepository extends JpaRepository<PropertyCreditNoteLine, Long> {

    @Query("select line from PropertyCreditNoteLine line "
            + "where line.hotel.id = :hotelId and line.creditNote.invoice.id = :invoiceId order by line.id")
    List<PropertyCreditNoteLine> findByHotelIdAndInvoiceIdOrderByIdAsc(
            @Param("hotelId") Long hotelId,
            @Param("invoiceId") Long invoiceId);
}
