package cc.niaoer.lowcall.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneDisplayFormatterTest {

    @Test
    fun `formats domestic Chinese mobile`() {
        assertEquals(PhoneDisplay("138 0013 8000"), formatPhoneForDisplay("13800138000"))
    }

    @Test
    fun `formats Chinese mobile with country code`() {
        assertEquals(PhoneDisplay("+86 138 0013 8000"), formatPhoneForDisplay("+8613800138000"))
    }

    @Test
    fun `formats two digit area code landline`() {
        assertEquals(PhoneDisplay("021 6961 9151"), formatPhoneForDisplay("02169619151"))
    }

    @Test
    fun `formats three digit area code landline`() {
        assertEquals(PhoneDisplay("0731 5832 9153"), formatPhoneForDisplay("073158329153"))
    }

    @Test
    fun `formats international number with country code`() {
        assertEquals(PhoneDisplay("+1 202 555 0123"), formatPhoneForDisplay("+12025550123"))
    }

    @Test
    fun `separates extension introduced by x`() {
        assertEquals(
            PhoneDisplay("138 0013 8000", extension = "123"),
            formatPhoneForDisplay("13800138000x123")
        )
    }

    @Test
    fun `separates extension introduced by semicolon`() {
        assertEquals(
            PhoneDisplay("021 6961 9151", extension = "456"),
            formatPhoneForDisplay("02169619151;456")
        )
    }

    @Test
    fun `separates extension introduced by comma`() {
        assertEquals(
            PhoneDisplay("021 6961 9151", extension = "78"),
            formatPhoneForDisplay("02169619151,78")
        )
    }

    @Test
    fun `separates extension introduced by Chinese label`() {
        assertEquals(
            PhoneDisplay("021 6961 9151", extension = "90"),
            formatPhoneForDisplay("021-69619151转90")
        )
    }

    @Test
    fun `keeps blank and non numeric input readable`() {
        assertEquals(PhoneDisplay(""), formatPhoneForDisplay("  "))
        assertEquals(PhoneDisplay("abc"), formatPhoneForDisplay("abc"))
    }

    @Test
    fun `keeps service number unchanged`() {
        assertEquals(PhoneDisplay("12345"), formatPhoneForDisplay("12345"))
    }

    @Test
    fun `groups unknown international number without claiming country`() {
        assertEquals(PhoneDisplay("+999 123 456 789"), formatPhoneForDisplay("+999123456789"))
    }

    @Test
    fun `rejects repeated plus as a formatted international number`() {
        assertEquals(PhoneDisplay("++8613800138000"), formatPhoneForDisplay("++8613800138000"))
    }

    @Test
    fun `removes duplicated province city display without changing distinct names`() {
        assertEquals("上海", formatLocationForDisplay("上海上海"))
        assertEquals("重庆万州", formatLocationForDisplay("重庆万州"))
    }
}
