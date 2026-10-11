package dev.sharpsmp.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FormatTest {
    @Test
    void formatsBalances() {
        assertEquals("$1,500", Format.money(1_500, "$"));
        assertEquals("$12.5K", Format.money(12_500, "$"));
        assertEquals("$2M", Format.money(2_000_000, "$"));
        assertEquals("$1.5M", Format.money(1_500_000, "$"));
    }

    @Test
    void parsesAmounts() {
        assertEquals(1_500L, Format.amount("1.5k"));
        assertEquals(2_000_000L, Format.amount("2M"));
        assertEquals(1_000_000_000L, Format.amount("1b"));
        assertEquals(10_000L, Format.amount("10,000"));
        assertEquals(500L, Format.amount("$500"));
        assertNull(Format.amount("abc"));
        assertNull(Format.amount("-5"));
        assertNull(Format.amount("0"));
        assertNull(Format.amount("10.5"));
    }

    @Test
    void calculatesBountyTax() {
        assertEquals(50L, Format.tax(1_000, 5));
        assertEquals(1_050L, Format.cost(1_000, 5));
        assertEquals(0L, Format.tax(0, 5));
    }

    @Test
    void formatsTimeAndRatio() {
        assertEquals("12m", Format.playtime(12));
        assertEquals("2h 5m", Format.playtime(125));
        assertEquals("45s", Format.duration(45));
        assertEquals("1m 5s", Format.duration(65));
        assertEquals("3.00", Format.kdr(3, 1));
        assertEquals("0.00", Format.kdr(0, 0));
    }
}
