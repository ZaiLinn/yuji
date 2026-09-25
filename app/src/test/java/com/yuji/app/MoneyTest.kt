package com.yuji.app

import com.yuji.app.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {
    @Test fun cny() {
        assertEquals("¥1,234.56", Money.cny(BigDecimal("1234.56")))
        assertEquals("-¥1,234.50", Money.cny(BigDecimal("-1234.5")))
        assertEquals("¥0.00", Money.cny(BigDecimal.ZERO))
    }

    @Test fun signed() {
        assertEquals("+¥10.00", Money.signedCny(BigDecimal("10")))
        assertEquals("−¥10.00", Money.signedCny(BigDecimal("-10")))
    }

    @Test fun compact() {
        assertEquals("¥12.3万", Money.compactCny(BigDecimal("123456")))
        assertEquals("¥1.23亿", Money.compactCny(BigDecimal("123456789")))
        assertEquals("¥9,999", Money.compactCny(BigDecimal("9999")))
    }

    @Test fun nativeAmounts() {
        assertEquals("1,943.96 EUR", Money.amount(BigDecimal("1943.96"), "EUR"))
        assertEquals("0.01234567 BTC", Money.amount(BigDecimal("0.01234567"), "BTC"))
        assertEquals("0.50 BTC", Money.amount(BigDecimal("0.5"), "BTC"))
        assertEquals("3,167,122.00 USDT", Money.amount(BigDecimal("3167122"), "USDT"))
        assertEquals("1,000 JPY", Money.amount(BigDecimal("1000"), "JPY"))
    }

    /** The old app showed large balances as 1.0E7 in the quick-update page. */
    @Test fun inputHasNoExponent() {
        assertEquals("10000000", Money.toInput(BigDecimal("1.0E7")))
        assertEquals("2667.69", Money.toInput(BigDecimal("2667.6900")))
        assertEquals("0", Money.toInput(BigDecimal("0.000")))
    }

    @Test fun parse() {
        assertEquals(BigDecimal("1234.5"), Money.parse(" ¥1,234.5 "))
        assertEquals(BigDecimal("-20"), Money.parse("−20"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("-"))
    }
}
