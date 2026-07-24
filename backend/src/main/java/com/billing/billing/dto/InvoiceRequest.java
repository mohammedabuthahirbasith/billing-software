package com.billing.billing.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.billing.billing.model.PaymentMethod;

public record InvoiceRequest(
        @Size(max = 255) String customerName,
        @Size(max = 20) String customerPhone,
        @NotNull PaymentMethod paymentMethod,
        @NotEmpty @Valid List<InvoiceItemRequest> items
) {}