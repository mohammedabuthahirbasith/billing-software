package com.billing.billing.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.billing.billing.model.Invoice;
import com.billing.billing.model.InvoiceStatus;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByIdAndStore_Id(Long id, Long storeId);

    List<Invoice> findAllByStore_Id(Long storeId, Sort sort);

    @Query("""
        SELECT new com.billing.billing.repository.SalesSummaryRow(
            COALESCE(SUM(i.subtotal), 0), COALESCE(SUM(i.taxAmount), 0), COALESCE(SUM(i.totalAmount), 0), COUNT(i))
        FROM Invoice i
        WHERE i.store.id = :storeId
          AND i.status = com.billing.billing.model.InvoiceStatus.COMPLETED
          AND i.createdAt >= :from AND i.createdAt < :to
        """)
    SalesSummaryRow getSalesSummary(@Param("storeId") Long storeId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
        SELECT new com.billing.billing.repository.PaymentMethodBreakdownRow(
            i.paymentMethod, COALESCE(SUM(i.totalAmount), 0), COUNT(i))
        FROM Invoice i
        WHERE i.store.id = :storeId
          AND i.status = com.billing.billing.model.InvoiceStatus.COMPLETED
          AND i.createdAt >= :from AND i.createdAt < :to
        GROUP BY i.paymentMethod
        """)
    List<PaymentMethodBreakdownRow> getBreakdownByPaymentMethod(
            @Param("storeId") Long storeId, @Param("from") Instant from, @Param("to") Instant to);

    long countByStore_IdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long storeId, InvoiceStatus status, Instant from, Instant toExclusive);
}
