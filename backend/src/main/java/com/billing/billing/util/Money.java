package com.billing.billing.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Single home for the app's money arithmetic so invoice creation and refunds cannot drift apart:
// both must round per line at scale 2 and only then sum the already-rounded values, which is what
// keeps a document's total exactly equal to the sum of its printed line totals.
public final class Money {

    public static final int SCALE = 2;

    private Money() {}

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    }

    // price/gstRate are both scale-2 numeric columns, but their raw product/quotient in Java lands
    // at scale 4, so the rounding has to happen here rather than being deferred to persistence.
    public static BigDecimal lineSubtotal(BigDecimal unitPrice, int quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal gst(BigDecimal lineSubtotal, BigDecimal gstRatePercent) {
        return lineSubtotal.multiply(gstRatePercent)
                .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
    }
}
