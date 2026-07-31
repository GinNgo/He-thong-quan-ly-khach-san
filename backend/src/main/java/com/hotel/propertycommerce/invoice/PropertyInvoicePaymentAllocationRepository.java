package com.hotel.propertycommerce.invoice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PropertyInvoicePaymentAllocationRepository
        extends JpaRepository<PropertyInvoicePaymentAllocation, Long> {

    List<PropertyInvoicePaymentAllocation> findByInvoiceIdOrderByIdAsc(Long invoiceId);

    List<PropertyInvoicePaymentAllocation> findByHotelIdAndInvoiceIdOrderByIdAsc(Long hotelId, Long invoiceId);

    Optional<PropertyInvoicePaymentAllocation> findByInvoiceIdAndFinancialTransactionId(
            Long invoiceId,
            Long transactionId);
}
