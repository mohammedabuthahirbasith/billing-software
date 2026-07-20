package com.billing.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.billing.billing.model.Invoice;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        String customerName,
        String customerPhone,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String status,
        Instant createdAt,
        Instant voidedAt,
        List<InvoiceItemResponse> items
) {
    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                "INV-%06d".formatted(invoice.getId()),
                invoice.getCustomerName(),
                invoice.getCustomerPhone(),
                invoice.getSubtotal(),
                invoice.getTaxAmount(),
                invoice.getTotalAmount(),
                invoice.getStatus().name(),
                invoice.getCreatedAt(),
                invoice.getVoidedAt(),
                invoice.getItems().stream().map(InvoiceItemResponse::from).toList()
        );
    }
}