package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.WarrantyUnit
import com.example.dukanhisabnext.ui.sell.CART_FILE_VERSION
import com.example.dukanhisabnext.ui.sell.CartLineInput
import com.example.dukanhisabnext.ui.sell.CartState
import com.example.dukanhisabnext.ui.sell.SavedCart
import com.example.dukanhisabnext.ui.sell.cartFromJson
import com.example.dukanhisabnext.ui.sell.cartToJson
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The cart FILE, versions 1 and 2 (sale date and warranty, part 2). It runs on the phone because
 * org.json is the Android library; on the PC it is only an empty stub.
 */
@RunWith(AndroidJUnit4::class)
class CartFileVersionTest {

    private fun line(id: Long, value: Int? = null, unit: WarrantyUnit? = null) = CartLineInput(
        productId = id, name = "পণ্য $id", unit = ProductUnit.PIECE, quantityText = "1", unitPriceText = "100",
        warrantyValue = value, warrantyUnit = unit
    )

    private fun cartWith(vararg lines: CartLineInput, saleDay: LocalDate? = null, confirmed: LocalDate? = null) =
        CartState(lines = lines.toList(), saleDay = saleDay, oldDateConfirmedDay = confirmed)

    @Test
    fun theFileVersionIs2() {
        assertEquals(2, CART_FILE_VERSION)
    }

    @Test
    fun aVersion2CartKeepsTheDayTheConfirmationAndEveryLinesWarranty() {
        val state = cartWith(
            line(1, 6, WarrantyUnit.MONTH), line(2),
            saleDay = LocalDate.of(2026, 10, 1), confirmed = LocalDate.of(2026, 10, 1)
        )
        val back = cartFromJson(cartToJson(SavedCart(state, customerId = 9, draftId = "d-1")))!!
        assertEquals(state, back.state)
        assertEquals(9L, back.customerId)
        assertEquals("d-1", back.draftId)
        assertEquals(6, back.state.lines[0].warrantyValue)
        assertEquals(WarrantyUnit.MONTH, back.state.lines[0].warrantyUnit)
        assertNull(back.state.lines[1].warrantyValue)
    }

    @Test
    fun todayIsSavedAsNoDay() {
        val json = cartToJson(SavedCart(cartWith(line(1)), null, "d"))
        assertTrue(json, json.contains("\"saleDay\":null"))
        assertNull(cartFromJson(json)!!.state.saleDay)
    }

    /** A cart file as version 1 wrote it: no sale day, no warranty keys. */
    private val version1 = """
        {"version":1,"draftId":"old-cart","customerId":12,"discountOn":true,"discountUnit":"PERCENT",
         "discountText":"5","deliveryOn":false,"deliveryText":"",
         "lines":[{"productId":3,"name":"চাল","unit":"KG","quantityText":"1.5","unitPriceText":"75"}]}
    """.trimIndent()

    @Test
    fun aVersion1CartIsStillReadWithTodayAndNoWarranty() {
        val saved = cartFromJson(version1)!!
        assertEquals("old-cart", saved.draftId)
        assertEquals(12L, saved.customerId)
        assertNull(saved.state.saleDay)
        assertNull(saved.state.oldDateConfirmedDay)
        val only = saved.state.lines.single()
        assertEquals("চাল", only.name)
        assertEquals("1.5", only.quantityText)
        assertNull(only.warrantyValue)
        assertNull(only.warrantyUnit)
        assertTrue(saved.state.discountOn)
    }

    @Test
    fun aFileOfAnotherVersionIsIgnored() {
        assertNull(cartFromJson(version1.replace("\"version\":1", "\"version\":3")))
        assertNull(cartFromJson(version1.replace("\"version\":1", "\"version\":0")))
    }

    @Test
    fun aBrokenWarrantyIsDroppedButTheLineStays() {
        val json = cartToJson(SavedCart(cartWith(line(1, 6, WarrantyUnit.MONTH)), null, "d"))
        val brokenFiles = listOf(
            json.replace("\"warrantyUnit\":\"MONTH\"", "\"warrantyUnit\":\"FORTNIGHT\""), // an unknown unit
            json.replace("\"warrantyValue\":6", "\"warrantyValue\":0"),                    // zero
            json.replace("\"warrantyValue\":6", "\"warrantyValue\":121")                   // over the 120 months limit
        )
        for (edited in brokenFiles) {
            val l = cartFromJson(edited)!!.state.lines.single()
            assertEquals(edited, 1L, l.productId)
            assertNull(edited, l.warrantyValue)
            assertNull(edited, l.warrantyUnit)
        }
    }

    @Test
    fun aDayNumberThatIsNotARealDateMakesTheFileUnreadable() {
        val day = LocalDate.of(2026, 10, 1)
        val json = cartToJson(SavedCart(cartWith(line(1), saleDay = day), null, "d"))
        assertNull(cartFromJson(json.replace("\"saleDay\":${day.toEpochDay()}", "\"saleDay\":\"yesterday\"")))
    }

    @Test
    fun aVersion1FileIgnoresASaleDayKeyItCannotHave() {
        // Version 1 never wrote a sale day: even if one is there, a version 1 cart means today.
        val withDay = version1.replace("\"deliveryText\":\"\",", "\"deliveryText\":\"\",\"saleDay\":20000,")
        assertNull(cartFromJson(withDay)!!.state.saleDay)
    }
}
