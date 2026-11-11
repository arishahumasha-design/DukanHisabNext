package com.example.dukanhisabnext.receipt

import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.ProductUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared receipt text: every scenario from the plan. */
class ReceiptTextTest {

    private fun text(data: ReceiptData) = buildReceiptText(data, testLabels)

    private val line = "--------------------"

    // The two items used by most tests, as they appear in the text.
    private val itemLines = listOf(
        "১. চাল",
        "   ২.৫ কেজি × ৳ ৪০ = ৳ ১০০",
        "২. সাবান",
        "   ৩ পিস × ৳ ৫০ = ৳ ১৫০"
    )

    // ---- The simplest receipt ----

    @Test
    fun aPlainSaleWithoutCustomerDiscountDeliveryDueOrChange() {
        val expected = listOf(
            "বিক্রয় রসিদ",          // no shop name: the neutral placeholder
            line,
            "রসিদ নং: ২৬১১১০-০০১",
            "তারিখ: ১০/১১/২০২৬ রাত ১১:৪৫",
            line,
            *itemLines.toTypedArray(),
            line,
            "সাব-টোটাল: ৳ ২৫০",
            "মোট প্রদেয়: ৳ ২৫০",
            "পরিশোধ (নগদ): ৳ ২৫০"
        ).joinToString("\n")

        assertEquals(expected, text(receipt()))
    }

    // ---- Everything at once ----

    @Test
    fun aFullReceiptWithShopCustomerDiscountDeliveryDueAndNote() {
        val data = receipt(
            shopName = "করিম স্টোর", shopAddress = "বাজার রোড, ঢাকা", shopPhone = "01711000000",
            customerName = "রহিম", customerPhone = "01811000000",
            discount = 2_500, deliveryCharge = 3_000, total = 25_500,
            paid = 20_000, method = PaymentMethod.BKASH, saleDue = 5_500,
            customerTotalDue = 15_500, note = "পরে দেবে"
        )
        val expected = listOf(
            "করিম স্টোর",
            "বাজার রোড, ঢাকা",
            "মোবাইল: ০১৭১১০০০০০০",
            line,
            "রসিদ নং: ২৬১১১০-০০১",
            "তারিখ: ১০/১১/২০২৬ রাত ১১:৪৫",
            "কাস্টমার: রহিম",
            "মোবাইল: ০১৮১১০০০০০০",
            line,
            *itemLines.toTypedArray(),
            line,
            "সাব-টোটাল: ৳ ২৫০",
            "ছাড়: -৳ ২৫",
            "ডেলিভারি চার্জ: ৳ ৩০",
            "মোট প্রদেয়: ৳ ২৫৫",
            "পরিশোধ (বিকাশ): ৳ ২০০",
            "এই বিক্রির বাকি: ৳ ৫৫",
            "আপনার মোট বাকি: ৳ ১৫৫",
            "মন্তব্য: পরে দেবে"
        ).joinToString("\n")

        assertEquals(expected, text(data))
    }

    @Test
    fun theLinesComeInTheAgreedOrder() {
        val t = text(
            receipt(
                shopName = "করিম স্টোর", shopAddress = "ঠিকানা", shopPhone = "01711000000",
                customerName = "রহিম", customerPhone = "01811000000",
                discount = 1, deliveryCharge = 1, total = 25_000, paid = 24_000,
                saleDue = 1_000, change = 0, customerTotalDue = 5_000, note = "নোট"
            )
        )
        val markers = listOf(
            "করিম স্টোর", "ঠিকানা", "মোবাইল: ০১৭১১", "রসিদ নং", "তারিখ", "কাস্টমার: রহিম", "১. চাল",
            "সাব-টোটাল", "ছাড়", "ডেলিভারি চার্জ", "মোট প্রদেয়", "পরিশোধ", "এই বিক্রির বাকি",
            "আপনার মোট বাকি", "মন্তব্য"
        )
        val positions = markers.map { t.indexOf(it) }
        assertTrue("every line is present: $positions", positions.all { it >= 0 })
        assertEquals("in order", positions.sorted(), positions)
    }

    // ---- With and without a customer ----

    @Test
    fun withoutACustomerThereAreNoCustomerLines() {
        val t = text(receipt())
        assertFalse(t.contains("কাস্টমার"))
        assertFalse(t.contains("আপনার মোট বাকি"))
    }

    @Test
    fun aCustomerWithoutAMobileShowsOnlyTheName() {
        val t = text(receipt(customerName = "রহিম", customerPhone = null))
        assertTrue(t.contains("কাস্টমার: রহিম"))
        assertEquals(0, t.lines().count { it.startsWith("মোবাইল") }) // no shop mobile, no customer mobile
    }

    // ---- Discount and delivery: only if above 0 ----

    @Test
    fun noDiscountAndNoDeliveryLines() {
        val t = text(receipt())
        assertFalse(t.contains("ছাড়"))
        assertFalse(t.contains("ডেলিভারি চার্জ"))
    }

    @Test
    fun onlyADiscount() {
        val t = text(receipt(discount = 2_500, total = 22_500, paid = 22_500))
        assertTrue(t.contains("ছাড়: -৳ ২৫"))
        assertFalse(t.contains("ডেলিভারি চার্জ"))
    }

    @Test
    fun onlyADeliveryCharge() {
        val t = text(receipt(deliveryCharge = 3_000, total = 28_000, paid = 28_000))
        assertTrue(t.contains("ডেলিভারি চার্জ: ৳ ৩০"))
        assertFalse(t.contains("ছাড়"))
    }

    // ---- Due and no due ----

    @Test
    fun noDueMeansNoDueLines() {
        val t = text(receipt())
        assertFalse(t.contains("এই বিক্রির বাকি"))
    }

    @Test
    fun aDueShowsTheSaleDueAndTheCustomersTotalDue() {
        val t = text(
            receipt(customerName = "রহিম", paid = 20_000, saleDue = 5_000, customerTotalDue = 12_000)
        )
        assertTrue(t.contains("এই বিক্রির বাকি: ৳ ৫০"))
        assertTrue(t.contains("আপনার মোট বাকি: ৳ ১২০"))
    }

    @Test
    fun aCustomerWhoOwesNothingHasNoTotalDueLine() {
        val t = text(receipt(customerName = "রহিম", customerTotalDue = null))
        assertFalse(t.contains("আপনার মোট বাকি"))
    }

    @Test
    fun nothingPaidNamesNoPaymentMethod() {
        val t = text(receipt(customerName = "রহিম", paid = 0, saleDue = 25_000, method = PaymentMethod.CASH))
        assertTrue(t.contains("\nপরিশোধ: ৳ ০"))
        assertFalse(t.contains("পরিশোধ (")) // the method has no meaning when nothing was paid
    }

    @Test
    fun theMethodNamesForEveryPaymentMethodThatCanBePaid() {
        assertTrue(text(receipt(method = PaymentMethod.CASH)).contains("পরিশোধ (নগদ)"))
        assertTrue(text(receipt(method = PaymentMethod.BKASH)).contains("পরিশোধ (বিকাশ)"))
        assertTrue(text(receipt(method = PaymentMethod.NAGAD)).contains("পরিশোধ (নগদ (মোবাইল))"))
    }

    // ---- Change (ফেরত) ----

    @Test
    fun changeRightAfterASaveIsShownAfterThePaidLine() {
        val t = text(receipt(change = 25_000)) // the customer gave ৳500 for a ৳250 sale
        val lines = t.lines()
        assertEquals("পরিশোধ (নগদ): ৳ ২৫০", lines[lines.size - 2])
        assertEquals("ফেরত: ৳ ২৫০", lines.last())
    }

    @Test
    fun noChangeMeansNoChangeLine() {
        assertFalse(text(receipt(change = 0)).contains("ফেরত"))
    }

    // ---- Bengali digits and Bangladeshi grouping ----

    @Test
    fun bigAmountsUseBangladeshiGroupingWithBengaliDigits() {
        // ৳12,34,567.50: lakh grouping, paisa shown only when not zero.
        val big = ReceiptLine("টিভি", 1_000, ProductUnit.PIECE, 123_456_750, 123_456_750)
        val t = text(receipt(lines = listOf(big), subtotal = 123_456_750, total = 123_456_750, paid = 123_456_750))
        assertTrue(t.contains("৳ ১২,৩৪,৫৬৭.৫০"))
        assertTrue(t.contains("মোট প্রদেয়: ৳ ১২,৩৪,৫৬৭.৫০"))
    }

    @Test
    fun smallAmountsHaveNoGroupingAndWholeTakaHaveNoDecimals() {
        val t = text(receipt(discount = 5, total = 24_995, paid = 24_995))
        assertTrue(t.contains("ছাড়: -৳ ০.০৫"))
        assertTrue(t.contains("সাব-টোটাল: ৳ ২৫০"))
    }

    @Test
    fun thereAreNoLatinDigitsAnywhereInTheText() {
        val data = receipt(
            shopName = "করিম স্টোর", shopAddress = "বাজার রোড", shopPhone = "01711000000",
            customerName = "রহিম", customerPhone = "01811000000",
            discount = 2_500, deliveryCharge = 3_000, total = 25_500, paid = 20_000,
            saleDue = 5_500, change = 0, customerTotalDue = 15_500, note = "পরে দেবে"
        )
        val t = text(data)
        assertFalse("found a Latin digit in: $t", t.any { it in '0'..'9' })
    }

    // ---- Quantity with decimals and units ----

    @Test
    fun quarterKiloShowsAsZeroPointTwoFive() {
        // 0.250 kg is stored as 250 thousandths; trailing zeros are dropped.
        val rice = ReceiptLine("চাল", 250, ProductUnit.KG, 4_000, 1_000)
        val t = text(receipt(lines = listOf(rice), subtotal = 1_000, total = 1_000, paid = 1_000))
        assertTrue(t.contains("   ০.২৫ কেজি × ৳ ৪০ = ৳ ১০"))
    }

    @Test
    fun otherDecimalQuantitiesAndUnits() {
        fun lineText(quantity: Long, unit: ProductUnit?) =
            receiptQuantity(ReceiptLine("x", quantity, unit, 0, 0), testLabels)

        assertEquals("১.০০৫ কেজি", lineText(1_005, ProductUnit.KG))
        assertEquals("১২ ডজন", lineText(12_000, ProductUnit.DOZEN))
        assertEquals("০.৫ লিটার", lineText(500, ProductUnit.LITRE))
        assertEquals("২ প্যাকেট", lineText(2_000, ProductUnit.PACKET))
        assertEquals("২", lineText(2_000, null)) // unit unknown: just the number
    }

    // ---- Long product names ----

    @Test
    fun aLongProductNameIsKeptWholeOnItsOwnLine() {
        val longName = "মিনিকেট চাল ".repeat(15).trim() // 15 repeats, about 180 characters
        val item = ReceiptLine(longName, 1_000, ProductUnit.KG, 8_000, 8_000)
        val t = text(receipt(lines = listOf(item), subtotal = 8_000, total = 8_000, paid = 8_000))

        val lines = t.lines()
        val nameIndex = lines.indexOf("১. $longName")
        assertTrue("the whole name is on one line", nameIndex >= 0)
        assertEquals("   ১ কেজি × ৳ ৮০ = ৳ ৮০", lines[nameIndex + 1]) // the numbers sit on the next line
    }

    @Test
    fun manyItemsAreNumberedInOrder() {
        val items = (1..12).map { ReceiptLine("পণ্য $it", 1_000, ProductUnit.PIECE, 1_000, 1_000) }
        val t = text(receipt(lines = items, subtotal = 12_000, total = 12_000, paid = 12_000))
        assertTrue(t.contains("১. পণ্য 1"))
        assertTrue(t.contains("১২. পণ্য 12"))
    }

    // ---- The shop header ----

    @Test
    fun shopAddressAndMobileAreOptional() {
        val onlyName = text(receipt(shopName = "করিম স্টোর"))
        assertTrue(onlyName.startsWith("করিম স্টোর\n$line"))

        val withAddress = text(receipt(shopName = "করিম স্টোর", shopAddress = "বাজার রোড"))
        assertTrue(withAddress.startsWith("করিম স্টোর\nবাজার রোড\n$line"))

        val withMobile = text(receipt(shopName = "করিম স্টোর", shopPhone = "01711000000"))
        assertTrue(withMobile.startsWith("করিম স্টোর\nমোবাইল: ০১৭১১০০০০০০\n$line"))
    }

    @Test
    fun anEmptyShopNameShowsThePlaceholderAndNeverCrashes() {
        val t = text(receipt(shopName = null, shopAddress = null, shopPhone = null))
        assertTrue(t.startsWith("বিক্রয় রসিদ\n"))
    }

    // ---- Date and time (Asia/Dhaka) ----

    @Test
    fun theDateAndTimeAreInDhakaTime() {
        // 17:45 UTC on 10 Nov = 23:45 on 10 Nov in Dhaka.
        assertEquals("১০/১১/২০২৬ রাত ১১:৪৫", receiptDateTime(SALE_TIME, testLabels))
    }

    @Test
    fun aSaleJustAfterMidnightInDhakaIsTheNextDay() {
        // 18:30 UTC on 10 Nov = 00:30 on 11 Nov in Dhaka.
        val millis = java.time.Instant.parse("2026-11-10T18:30:00Z").toEpochMilli()
        assertEquals("১১/১১/২০২৬ রাত ১২:৩০", receiptDateTime(millis, testLabels))
    }

    @Test
    fun morningNoonAfternoonAndEvening() {
        fun at(utc: String) = receiptDateTime(java.time.Instant.parse(utc).toEpochMilli(), testLabels)
        assertEquals("১০/১১/২০২৬ সকাল ৯:০৭", at("2026-11-10T03:07:00Z"))      // 09:07
        assertEquals("১০/১১/২০২৬ দুপুর ১২:০৫", at("2026-11-10T06:05:00Z"))     // 12:05
        assertEquals("১০/১১/২০২৬ বিকাল ৩:৩০", at("2026-11-10T09:30:00Z"))     // 15:30
        assertEquals("১০/১১/২০২৬ সন্ধ্যা ৬:১০", at("2026-11-10T12:10:00Z"))    // 18:10
    }

    // ---- The text itself ----

    @Test
    fun theTextHasNoTrailingNewlineOrBlankEnding() {
        val t = text(receipt())
        assertEquals(t.trimEnd(), t)
        assertFalse(t.endsWith("\n"))
    }

    @Test
    fun noVatNoPrintNoAdvertising() {
        val t = text(receipt())
        assertFalse(t.contains("VAT", ignoreCase = true))
        assertFalse(t.contains("ভ্যাট"))
        assertFalse(t.contains("Powered", ignoreCase = true))
    }
}
