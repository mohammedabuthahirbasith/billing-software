package com.billing.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.billing.billing.model.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
}