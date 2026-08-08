package com.billing.billing.util;

// The customer-facing invoice number is derived from the id, and every response that exposes it must
// derive it identically — otherwise the same invoice could be printed under two different numbers.
public final class InvoiceNumber {

    private static final String FORMAT = "INV-%06d";

    private InvoiceNumber() {}

    public static String of(Long invoiceId) {
        return FORMAT.formatted(invoiceId);
    }
}
