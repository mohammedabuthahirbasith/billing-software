package com.billing.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.billing.billing.model.InvoiceReturnItem;

public interface InvoiceReturnItemRepository extends JpaRepository<InvoiceReturnItem, Long> {

    // The authoritative "how much of this line has already been returned" check — ReturnService
    // relies on this alone to reject over-returns; any client-side display of the same figure is
    // a UX hint only and can never diverge into a real correctness bug.
    @Query("SELECT COALESCE(SUM(ri.quantityReturned), 0) FROM InvoiceReturnItem ri "
            + "WHERE ri.invoiceItem.id = :invoiceItemId")
    int sumReturnedQuantityByInvoiceItemId(@Param("invoiceItemId") Long invoiceItemId);
}