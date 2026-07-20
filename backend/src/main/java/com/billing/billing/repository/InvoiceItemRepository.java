package com.billing.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.billing.billing.model.InvoiceItem;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {
    boolean existsByProduct_Id(Long productId);
}