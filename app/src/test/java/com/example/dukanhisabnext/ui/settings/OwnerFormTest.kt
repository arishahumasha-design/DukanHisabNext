package com.example.dukanhisabnext.ui.settings

import com.example.dukanhisabnext.ui.products.FormError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnerFormTest {

    private fun valid(name: String, phone: String) =
        (validateOwnerForm(OwnerFormInput(name, phone)) as OwnerFormResult.Valid).owner

    private fun errors(name: String, phone: String) =
        (validateOwnerForm(OwnerFormInput(name, phone)) as OwnerFormResult.Invalid).errors

    @Test
    fun bothBoxesMayBeEmpty() {
        val owner = valid("", "")
        assertNull(owner.ownerName)
        assertNull(owner.phone)
    }

    @Test
    fun theNameIsTrimmedAndBlankMeansNull() {
        assertEquals("করিম", valid("  করিম  ", "").ownerName)
        assertNull(valid("   ", "").ownerName)
    }

    @Test
    fun aValidMobileIsSavedAsElevenDigits() {
        assertEquals("01711000000", valid("", "01711000000").phone)
        assertEquals("01711000000", valid("", "+8801711000000").phone)
        assertEquals("01711000000", valid("", "০১৭১১-০০০০০০").phone)
        assertEquals("01711000000", valid("", "01711 000 000").phone)
    }

    @Test
    fun aBlankMobileMeansNoMobile() {
        assertNull(valid("করিম", "   ").phone)
    }

    @Test
    fun aWrongMobileIsAnErrorWithTheNormalMessage() {
        for (phone in listOf("123", "0171100000", "02711000000", "abc", "+8801711")) {
            assertEquals("for '$phone'", FormError.INVALID_PHONE, errors("করিম", phone).phone)
        }
    }

    @Test
    fun aWrongMobileIsAnErrorEvenWithAValidName() {
        val e = errors("করিম মিয়া", "123")
        assertEquals(FormError.INVALID_PHONE, e.phone)
        assertEquals(true, e.hasErrors)
    }
}
