package com.billing.billing.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.billing.billing.model.InvoiceItem;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {
    // Hardened with Product's store — safe today only by call-site discipline (the productId passed
    // in is already store-verified before this runs), not by the query's own design, so don't rely
    // on that discipline holding forever.
    boolean existsByProduct_IdAndProduct_Store_Id(Long productId, Long storeId);

    // How ReturnService validates a requested invoiceItemId actually belongs to the target invoice,
    // without loading the whole items collection just to filter one entry out of it.
    Optional<InvoiceItem> findByIdAndInvoice_Id(Long id, Long invoiceId);

    // Joins Invoice (for status/createdAt/store filtering) but not Product — InvoiceItem already carries
    // denormalized productName/sku snapshot columns, so ii.product.id resolves straight to the FK
    // column with no join needed for that part. List<T> (not Page<T>) + an unsorted Pageable applies
    // LIMIT/OFFSET with no extra COUNT query; ORDER BY is explicit in the JPQL, not left to a Sort.
    @Query("""
        SELECT new com.billing.billing.repository.TopProductRow(
            ii.product.id, ii.productName, ii.sku, SUM(ii.quantity), SUM(ii.lineTotal))
        FROM InvoiceItem ii
        JOIN ii.invoice i
        WHERE i.store.id = :storeId
          AND i.status = com.billing.billing.model.InvoiceStatus.COMPLETED
          AND i.createdAt >= :from AND i.createdAt < :to
        GROUP BY ii.product.id, ii.productName, ii.sku
        ORDER BY SUM(ii.quantity) DESC
        """)
    List<TopProductRow> findTopProducts(
            @Param("storeId") Long storeId, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
