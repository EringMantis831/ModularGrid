package com.modulargrid.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ValueFormat}: values are exact while they fit into a GUI row and are only shortened past
 * {@link ValueFormat#MAX_PLAIN_DIGITS} digits.
 */
class ValueFormatTest {

    @Test
    void valuesUpToNineDigitsStayExact() {
        assertEquals("0", ValueFormat.display(0L));
        assertEquals("1", ValueFormat.display(1L));
        assertEquals("-1", ValueFormat.display(-1L));
        assertEquals("999999999", ValueFormat.display(999999999L));
        assertEquals("-999999999", ValueFormat.display(-999999999L));
    }

    @Test
    void tenDigitsAndMoreUseScientificNotation() {
        assertEquals("1.00e9", ValueFormat.display(1000000000L));
        assertEquals("1.00e11", ValueFormat.display(100000000000L));
        assertEquals("1.23e10", ValueFormat.display(12345678901L));
        assertEquals("8.22e11", ValueFormat.display(822049568123L));
        assertEquals("9.22e18", ValueFormat.display(Long.MAX_VALUE));
        assertEquals("-9.22e18", ValueFormat.display(Long.MIN_VALUE));
    }

    @Test
    void aMantissaThatRoundsUpCarriesIntoTheExponent() {
        // 9.999e11 rounds to 10.00, which has to become 1.00e12
        assertEquals("1.00e12", ValueFormat.display(999_900_000_000L));
        assertEquals("1.00e12", ValueFormat.display(999_999_999_999L));
    }

    @Test
    void floatsKeepTheirOwnRepresentationBelowTheThreshold() {
        assertEquals("0.0", ValueFormat.display(0.0F));
        assertEquals("123.456", ValueFormat.display(123.456F));
        assertEquals("1.4E-45", ValueFormat.display(Float.MIN_VALUE));
        assertEquals("-1.5", ValueFormat.display(-1.5F));
    }

    @Test
    void largeFloatsAreShortenedToo() {
        assertEquals("1.00e9", ValueFormat.display(1.0E9F));
        assertEquals("1.00e11", ValueFormat.display(1.0E11F));
        assertEquals("3.40e38", ValueFormat.display(Float.MAX_VALUE));
    }

    @Test
    void theExactFormNeverShortensWholeValues() {
        assertEquals("999999999", ValueFormat.exact(999999999L));
        assertEquals("123456789012", ValueFormat.exact(123456789012L));
        assertEquals("9223372036854775807", ValueFormat.exact(Long.MAX_VALUE));
        assertEquals("-9223372036854775808", ValueFormat.exact(Long.MIN_VALUE));
    }

    @Test
    void theExactFormKeepsAtMostFourDecimals() {
        assertEquals("0", ValueFormat.exact(0.0F));
        assertEquals("1", ValueFormat.exact(1.0F));
        assertEquals("0.1", ValueFormat.exact(0.1F));
        assertEquals("123.456", ValueFormat.exact(123.456F));
        assertEquals("1234.5679", ValueFormat.exact(1234.56789F));
        assertEquals("-1.5", ValueFormat.exact(-1.5F));
        assertEquals("3.4028235E38", ValueFormat.exact(Float.MAX_VALUE));
    }
}
