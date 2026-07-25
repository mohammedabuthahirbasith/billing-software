package com.billing.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.billing.billing.model.InvoiceReturn;
import com.billing.billing.model.InvoiceReturnItem;

public record ReturnResponse(
        Long id,
        Long invoiceId,
        BigDecimal refundSubtotal,
        BigDecimal refundTax,
        BigDecimal refundTotal,
        Instant createdAt,
        List<ReturnItemResponse> items
) {
    public record ReturnItemResponse(
            Long invoiceItemId,
            String productName,
            String sku,
            int quantityReturned,
            BigDecimal lineSubtotalRefund,
            BigDecimal lineTaxRefund,
            BigDecimal lineTotalRefund
    ) {
        public static ReturnItemResponse from(InvoiceReturnItem item) {
            return new ReturnItemResponse(
                    item.getInvoiceItem().getId(),
                    item.getInvoiceItem().getProductName(),
                    item.getInvoiceItem().getSku(),
                    item.getQuantityReturned(),
                    item.getLineSubtotalRefund(),
                    item.getLineTaxRefund(),
                    item.getLineTotalRefund()
            );
        }
    }

    public static ReturnResponse from(InvoiceReturn invoiceReturn) {
        return new ReturnResponse(
                invoiceReturn.getId(),
                invoiceReturn.getInvoice().getId(),
                invoiceReturn.getRefundSubtotal(),
                invoiceReturn.getRefundTax(),
                invoiceReturn.getRefundTotal(),
                invoiceReturn.getCreatedAt(),
                invoiceReturn.getItems().stream().map(ReturnItemResponse::from).toList()
        );
    }
}