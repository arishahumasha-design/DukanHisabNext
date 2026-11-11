package com.example.dukanhisabnext.ui.shop

import com.example.dukanhisabnext.ui.products.FormError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopFormTest {

    private fun input(
        name: String = "করিম স্টোর",
        owner: String = "",
        phone: String = "",
        address: String = ""
    ) = ShopFormInput(name, owner, phone, address)

    private fun valid(input: ShopFormInput) = (validateShopForm(input) as ShopFormResult.Valid).shop
    private fun errors(input: ShopFormInput) = (validateShopForm(input) as ShopFormResult.Invalid).errors

    @Test
    fun onlyTheShopNameIsRequired() {
        val shop = valid(input())
        assertEquals("করিম স্টোর", shop.name)
        assertNull(shop.ownerName)
        assertNull(shop.phone)
        assertNull(shop.address)
    }

    @Test
    fun theShopNameCannotBeEmpty() {
        assertEquals(FormError.REQUIRED, errors(input(name = "")).name)
        assertEquals(FormError.REQUIRED, errors(input(name = "   ")).name)
    }

    @Test
    fun valuesAreTrimmedAndBlankOptionalsBecomeNull() {
        val shop = valid(input(name = "  করিম স্টোর  ", owner = "  করিম  ", address = "  বাজার রোড  "))
        assertEquals("করিম স্টোর", shop.name)
        assertEquals("করিম", shop.ownerName)
        assertEquals("বাজার রোড", shop.address)

        val blank = valid(input(owner = "   ", address = "  "))
        assertNull(blank.ownerName)
        assertNull(blank.address)
    }

    @Test
    fun theMobileIsOptional() {
        assertNull(valid(input(phone = "")).phone)
        assertNull(valid(input(phone = "   ")).phone)
    }

    @Test
    fun aValidMobileIsSavedAsElevenDigits() {
        assertEquals("01711000000", valid(input(phone = "01711000000")).phone)
        assertEquals("01711000000", valid(input(phone = "+8801711000000")).phone)
        assertEquals("01711000000", valid(input(phone = "০১৭১১-০০০০০০")).phone)
        assertEquals("01711000000", valid(input(phone = "01711 000 000")).phone)
    }

    @Test
    fun aWrongMobileIsAnErrorWithTheNormalMessage() {
        for (phone in listOf("123", "0171100000", "02711000000", "abc", "+8801711")) {
            assertEquals("for '$phone'", FormError.INVALID_PHONE, errors(input(phone = phone)).phone)
        }
    }

    @Test
    fun bothErrorsAreReportedTogether() {
        val e = errors(input(name = "", phone = "123"))
        assertEquals(FormError.REQUIRED, e.name)
        assertEquals(FormError.INVALID_PHONE, e.phone)
        assertTrue(e.hasErrors)
    }
}
