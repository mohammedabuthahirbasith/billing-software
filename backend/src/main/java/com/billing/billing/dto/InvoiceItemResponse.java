package com.billing.billing.dto;

import java.math.BigDecimal;

import com.billing.billing.model.InvoiceItem;

public record InvoiceItemResponse(
        Long productId,
        String productName,
        String sku,
        String hsnCode,
        BigDecimal unitPrice,
        BigDecimal gstRate,
        int quantity,
        BigDecimal lineSubtotal,
        BigDecimal lineTax,
        BigDecimal lineTotal
) {
    public static InvoiceItemResponse from(InvoiceItem item) {
        return new InvoiceItemResponse(
                item.getProduct().getId(),
                item.getProductName(),
                item.getSku(),
                item.getHsnCode(),
                item.getUnitPrice(),
                item.getGstRate(),
                item.getQuantity(),
                item.getLineSubtotal(),
                item.getLineTax(),
                item.getLineTotal()
        );
    }
}