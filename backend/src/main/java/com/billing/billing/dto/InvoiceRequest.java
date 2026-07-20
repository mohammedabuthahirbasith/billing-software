package com.billing.billing.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record InvoiceRequest(
        @Size(max = 255) String customerName,
        @Size(max = 20) String customerPhone,
        @NotEmpty @Valid List<InvoiceItemRequest> items
) {}