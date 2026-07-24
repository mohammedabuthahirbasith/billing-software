package com.billing.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.billing.billing.model.Invoice;

public record InvoiceSummaryResponse(
        Long id,
        String invoiceNumber,
        String customerName,
        String customerPhone,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String status,
        String paymentMethod,
        Instant createdAt,
        Instant voidedAt
) {
    public static InvoiceSummaryResponse from(Invoice invoice) {
        return new InvoiceSummaryResponse(
                invoice.getId(),
                "INV-%06d".formatted(invoice.getId()),
                invoice.getCustomerName(),
                invoice.getCustomerPhone(),
                invoice.getSubtotal(),
                invoice.getTaxAmount(),
                invoice.getTotalAmount(),
                invoice.getStatus().name(),
                invoice.getPaymentMethod().name(),
                invoice.getCreatedAt(),
                invoice.getVoidedAt()
        );
    }
}