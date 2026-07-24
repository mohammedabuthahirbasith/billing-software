package com.billing.billing.repository;

import java.math.BigDecimal;

import com.billing.billing.model.PaymentMethod;

public record PaymentMethodBreakdownRow(PaymentMethod paymentMethod, BigDecimal revenue, Long invoiceCount) {}
