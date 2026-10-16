package com.modulargrid.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formats channel values for display.
 *
 * <ul>
 *     <li>{@link #display(long)} / {@link #display(float)}: the compact form used in a channel row. Values of more
 *         than {@link #MAX_PLAIN_DIGITS} digits become {@code N.NNeNN} with two decimals in the mantissa, which keeps
 *         the value and its unit inside the row.</li>
 *     <li>{@link #exact(long)} / {@link #exact(float)}: the form used in the details panel, which the player opened on
 *         purpose and which therefore shows the whole value. A fractional value keeps at most
 *         {@link #MAX_EXACT_DECIMALS} decimals.</li>
 * </ul>
 */
public final class ValueFormat {

    /** Digits a value may have in a channel row before it is shortened: the unit stops fitting beyond that. */
    public static final int MAX_PLAIN_DIGITS = 10;

    /** First value that needs shortening in a row: {@code 1e9}, the smallest number with ten digits. */
    public static final long SCIENTIFIC_THRESHOLD = 1_000_000_000L;

    /** Decimals kept by the exact form of a fractional value. */
    public static final int MAX_EXACT_DECIMALS = 4;

    private ValueFormat() {
    }

    /** @return the energy value for a channel row, exact while it fits, otherwise in {@code N.NNeNN} form */
    public static String display(long value) {
        if (value > -SCIENTIFIC_THRESHOLD && value < SCIENTIFIC_THRESHOLD) {
            return Long.toString(value);
        }
        return scientific(BigDecimal.valueOf(value));
    }

    /** @return a fractional value for a channel row, exact while it fits, otherwise in {@code N.NNeNN} form */
    public static String display(float value) {
        if (Math.abs(value) < SCIENTIFIC_THRESHOLD) {
            return Float.toString(value);
        }
        return scientific(new BigDecimal(Float.toString(value)));
    }

    /** @return the whole energy value, without shortening: the details panel has room for all of its digits */
    public static String exact(long value) {
        return Long.toString(value);
    }

    /**
     * @return a fractional value with at most {@link #MAX_EXACT_DECIMALS} decimals. Very large values fall back to the
     *         float's own {@code float} notation, a number written out in full would not fit into any panel.
     */
    public static String exact(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || Math.abs(value) >= SCIENTIFIC_THRESHOLD) {
            return Float.toString(value);
        }
        BigDecimal decimal = new BigDecimal(Float.toString(value));
        if (decimal.scale() > MAX_EXACT_DECIMALS) {
            decimal = decimal.setScale(MAX_EXACT_DECIMALS, RoundingMode.HALF_UP);
        }
        return decimal.stripTrailingZeros().toPlainString();
    }

    /**
     * @return the value with two decimals and its decimal exponent, for example {@code 8.22e11}. A mantissa that
     *         would round up to ten is carried into the exponent, so the mantissa always stays below ten.
     */
    private static String scientific(BigDecimal value) {
        int exponent = value.precision() - value.scale() - 1;
        BigDecimal mantissa = value.movePointLeft(exponent).setScale(2, RoundingMode.HALF_UP);
        if (mantissa.abs().compareTo(BigDecimal.TEN) >= 0) {
            mantissa = mantissa.movePointLeft(1).setScale(2, RoundingMode.HALF_UP);
            exponent++;
        }
        return mantissa.toPlainString() + "e" + exponent;
    }
}
