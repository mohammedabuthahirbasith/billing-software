package com.billing.billing.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) String sku,
        @Size(max = 1000) String description,
        // Upper bounds match the numeric(12,2) / numeric(5,2) columns behind these fields: without
        // them an out-of-range figure is only caught by Postgres, surfacing as a 500 instead of a 400.
        @NotNull @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) @DecimalMax(value = "100.0") BigDecimal gstRate,
        @Size(max = 20) String hsnCode,
        @NotNull @Min(0) Integer stockQuantity
) {}