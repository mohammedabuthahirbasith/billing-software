package com.billing.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesReportResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal totalSubtotal,
        BigDecimal totalTax,
        BigDecimal totalRevenue,
        long invoiceCount,
        long voidedCount,
        List<PaymentMethodBreakdown> byPaymentMethod,
        List<TopProduct> topProducts
) {
    public record PaymentMethodBreakdown(String paymentMethod, BigDecimal revenue, long invoiceCount) {}

    public record TopProduct(Long productId, String productName, String sku, long quantitySold, BigDecimal revenue) {}
}
