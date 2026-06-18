package cc.niaoer.nocall.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNormalizerTest {
    @Test
    fun normalize_stripsPlusSpacesHyphensParens() {
        // Digits-only keeps the country code 86; only non-digits are stripped.
        assertEquals("8613800138000", normalizePhone("+86 138-0013-8000"))
    }

    @Test
    fun normalize_keepsOnlyDigits() {
        assertEquals("0085245995523", normalizePhone("00852-4599 5523"))
    }

    @Test
    fun normalize_emptyForNoDigits() {
        assertEquals("", normalizePhone("  -  "))
    }

    @Test
    fun normalize_pureDigitsUnchanged() {
        assertEquals("13800138000", normalizePhone("13800138000"))
    }
}
