package com.smartbillpro.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Centralizes GST/line-total math so every caller (billing, reports, PDF generation)
 * rounds the same way. All monetary values are rounded HALF_UP to 2 decimal places,
 * matching standard invoicing conventions in India.
 */
public final class GstCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private GstCalculator() {
    }

    /** Base amount before tax = quantity * pricePerUnit */
    public static BigDecimal lineBaseAmount(BigDecimal quantity, BigDecimal pricePerUnit) {
        return quantity.multiply(pricePerUnit).setScale(SCALE, ROUNDING);
    }

    /** GST amount for a line = baseAmount * gstPercentage / 100 */
    public static BigDecimal lineGstAmount(BigDecimal baseAmount, BigDecimal gstPercentage) {
        if (gstPercentage == null || gstPercentage.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return baseAmount.multiply(gstPercentage)
                .divide(HUNDRED, SCALE, ROUNDING);
    }

    /** Line total = base + gst */
    public static BigDecimal lineTotal(BigDecimal baseAmount, BigDecimal gstAmount) {
        return baseAmount.add(gstAmount).setScale(SCALE, ROUNDING);
    }

    /**
     * Splits a combined GST amount into CGST/SGST halves for intra-state sales.
     * (IGST is used instead of CGST+SGST for inter-state sales; that split is a
     * business-rule choice left to the caller since it depends on customer state.)
     */
    public static BigDecimal[] splitCgstSgst(BigDecimal totalGst) {
        BigDecimal half = totalGst.divide(BigDecimal.valueOf(2), SCALE, ROUNDING);
        return new BigDecimal[]{half, half};
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }
}
