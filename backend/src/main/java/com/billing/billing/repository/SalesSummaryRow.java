package com.billing.billing.repository;

import java.math.BigDecimal;

public record SalesSummaryRow(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal totalAmount, Long invoiceCount) {}
