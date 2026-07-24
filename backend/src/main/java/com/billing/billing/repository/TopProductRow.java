package com.billing.billing.repository;

import java.math.BigDecimal;

public record TopProductRow(Long productId, String productName, String sku, Long quantitySold, BigDecimal revenue) {}
