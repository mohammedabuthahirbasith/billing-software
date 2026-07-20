package com.billing.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.billing.billing.model.Product;

public record ProductResponse(
        Long id,
        String name,
        String sku,
        String description,
        BigDecimal price,
        BigDecimal gstRate,
        String hsnCode,
        int stockQuantity,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getPrice(),
                product.getGstRate(),
                product.getHsnCode(),
                product.getStockQuantity(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}