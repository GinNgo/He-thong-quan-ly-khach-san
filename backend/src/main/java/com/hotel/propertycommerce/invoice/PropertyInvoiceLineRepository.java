package com.hotel.propertycommerce.invoice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropertyInvoiceLineRepository extends JpaRepository<PropertyInvoiceLine, Long> {

    List<PropertyInvoiceLine> findByInvoiceIdOrderByIdAsc(Long invoiceId);

    List<PropertyInvoiceLine> findByHotelIdAndInvoiceIdOrderByIdAsc(Long hotelId, Long invoiceId);
}
