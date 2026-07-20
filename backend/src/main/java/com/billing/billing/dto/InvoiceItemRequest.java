package com.billing.billing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InvoiceItemRequest(
        @NotNull Long productId,
        @NotNull @Positive Integer quantity
) {}