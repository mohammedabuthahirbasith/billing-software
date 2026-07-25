package com.billing.billing.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.billing.billing.model.InvoiceReturn;

public interface InvoiceReturnRepository extends JpaRepository<InvoiceReturn, Long> {
    Optional<InvoiceReturn> findByIdAndStore_Id(Long id, Long storeId);
    List<InvoiceReturn> findAllByInvoice_IdOrderByCreatedAtAsc(Long invoiceId);

    // Backs InvoiceService.voidInvoice()'s guard: voiding restocks each item's full original
    // quantity, so an invoice that already has a return would get double-restocked if voided too.
    boolean existsByInvoice_Id(Long invoiceId);
}