package com.saleh.enezi;

import org.junit.Test;
import static org.junit.Assert.*;

public class NumericInputTest {
    @Test public void parsesArabicIndicDigitsAndArabicSeparators() {
        assertEquals(41350d, NumericInput.parseDouble("٤١٬٣٥٠", -1), 0.000001d);
        assertEquals(41.5d, NumericInput.parseDouble("٤١٫٥", -1), 0.000001d);
        assertEquals(125000d, NumericInput.parseDouble("۱۲۵٬۰۰۰", -1), 0.000001d);
    }

    @Test public void parsesThousandsAndDecimalSeparators() {
        assertEquals(41350d, NumericInput.parseDouble("41,350", -1), 0.000001d);
        assertEquals(41.5d, NumericInput.parseDouble("41,50", -1), 0.000001d);
        assertEquals(1234.56d, NumericInput.parseDouble("1,234.56", -1), 0.000001d);
        assertEquals(1234.56d, NumericInput.parseDouble("1.234,56", -1), 0.000001d);
        assertEquals(1.234d, NumericInput.parseDouble("1.234", -1), 0.000001d);
    }

    @Test public void invalidAndEmptyInputUseFallback() {
        assertEquals(-1d, NumericInput.parseDouble("", -1), 0d);
        assertEquals(-1d, NumericInput.parseDouble("abc", -1), 0d);
        assertEquals(-1d, NumericInput.parseDouble("-", -1), 0d);
        assertEquals(-1d, NumericInput.parseDouble("12abc", -1), 0d);
        assertEquals(-1d, NumericInput.parseDouble("12-3", -1), 0d);
        assertEquals(-12.5d, NumericInput.parseDouble("-12,5", -1), 0.000001d);
    }
}
