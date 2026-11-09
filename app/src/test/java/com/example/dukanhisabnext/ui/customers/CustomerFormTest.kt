package com.example.dukanhisabnext.ui.customers

import com.example.dukanhisabnext.ui.products.FormError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomerFormTest {

    private fun input(
        name: String = "করিম",
        phone: String = "",
        address: String = "",
        note: String = ""
    ) = CustomerFormInput(name, phone, address, note)

    private fun valid(input: CustomerFormInput): ValidatedCustomer =
        (validateCustomerForm(input) as CustomerFormResult.Valid).customer

    private fun errors(input: CustomerFormInput): CustomerFormErrors =
        (validateCustomerForm(input) as CustomerFormResult.Invalid).errors

    // ---- Form rules ----

    @Test
    fun onlyANameIsNeeded() {
        val c = valid(input())
        assertEquals("করিম", c.name)
        assertNull(c.phone)
        assertNull(c.address)
        assertNull(c.note)
    }

    @Test
    fun valuesAreTrimmedAndBlankOptionalsBecomeNull() {
        val c = valid(input(name = "  রহিম  ", address = "  বাজার রোড  ", note = "   "))
        assertEquals("রহিম", c.name)
        assertEquals("বাজার রোড", c.address)
        assertNull(c.note)
    }

    @Test
    fun nameIsRequired() {
        assertEquals(FormError.REQUIRED, errors(input(name = "")).name)
        assertEquals(FormError.REQUIRED, errors(input(name = "   ")).name)
    }

    @Test
    fun aBadMobileIsReportedTogetherWithAMissingName() {
        val e = errors(input(name = "", phone = "123"))
        assertEquals(FormError.REQUIRED, e.name)
        assertEquals(FormError.INVALID_PHONE, e.phone)
    }

    @Test
    fun mobileIsSavedNormalized() {
        assertEquals("01711000000", valid(input(phone = "০১৭১১-০০০০০০")).phone)
        assertEquals("01711000000", valid(input(phone = " 017 1100 0000 ")).phone)
    }

    // ---- normalizeMobile ----

    @Test
    fun acceptedMobileFormats() {
        assertEquals("01711000000", normalizeMobile("01711000000"))
        assertEquals("01711000000", normalizeMobile("০১৭১১০০০০০০"))      // Bengali digits
        assertEquals("01711000000", normalizeMobile("+8801711000000"))   // country code with +
        assertEquals("01711000000", normalizeMobile("8801711000000"))    // country code without +
        assertEquals("01711000000", normalizeMobile("01711-000-000"))    // dashes
        assertEquals("01911000000", normalizeMobile("019 1100 0000"))    // spaces
    }

    @Test
    fun rejectedMobileFormats() {
        assertNull(normalizeMobile("12345"))
        assertNull(normalizeMobile("0171100000"))      // 10 digits: too short
        assertNull(normalizeMobile("017110000000"))    // 12 digits: too long
        assertNull(normalizeMobile("02711000000"))     // does not start with 01
        assertNull(normalizeMobile("11711000000"))
        assertNull(normalizeMobile("abc"))
        assertNull(normalizeMobile("+8801711"))
        assertNull(normalizeMobile("88017110000000"))  // 14 digits
    }

    // ---- balanceStatus ----

    @Test
    fun balanceStatusRules() {
        assertEquals(BalanceStatus.OWES, balanceStatus(1))
        assertEquals(BalanceStatus.OWES, balanceStatus(250_000))
        assertEquals(BalanceStatus.CLEAR, balanceStatus(0))
        assertEquals(BalanceStatus.ADVANCE, balanceStatus(-1))
        assertEquals(BalanceStatus.ADVANCE, balanceStatus(-5_000))
    }
}
