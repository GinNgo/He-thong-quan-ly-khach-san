package com.hotel.propertycommerce.invoice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropertyCreditNoteRepository extends JpaRepository<PropertyCreditNote, Long> {

    List<PropertyCreditNote> findByInvoiceIdOrderByIssuedAtAscIdAsc(Long invoiceId);

    List<PropertyCreditNote> findByHotelIdAndInvoiceIdOrderByIssuedAtAscIdAsc(Long hotelId, Long invoiceId);
}
