package com.example.dukanhisabnext.ui.products

import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.receipt.ReceiptLine
import com.example.dukanhisabnext.receipt.receiptQuantity
import com.example.dukanhisabnext.receipt.testLabels
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitWordTest {

    @Test
    fun exactlyOneUsesTheSingularWordAndEverythingElseThePlural() {
        assertEquals(R.string.unit_piece_one, ProductUnit.PIECE.labelResFor(1_000))
        assertEquals(R.string.unit_piece_one, ProductUnit.PIECE.labelResFor(-1_000)) // "-1 pc" (a stock correction)
        assertEquals(R.string.unit_piece, ProductUnit.PIECE.labelResFor(0))
        assertEquals(R.string.unit_piece, ProductUnit.PIECE.labelResFor(2_000))
        assertEquals(R.string.unit_kg, ProductUnit.KG.labelResFor(1_500)) // 1.5 kg
        assertEquals(R.string.unit_litre, ProductUnit.LITRE.labelResFor(1_001))
    }

    private fun xml(path: String): Map<String, String> =
        Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""").findAll(File(path).readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun englishSaysOnePcAndManyPcsAndBengaliIsUnchanged() {
        val en = xml("src/main/res/values-en/strings.xml")
        val bn = xml("src/main/res/values/strings.xml")
        assertEquals("pc", en["unit_piece_one"]); assertEquals("pcs", en["unit_piece"])
        assertEquals("litre", en["unit_litre_one"]); assertEquals("litres", en["unit_litre"])
        assertEquals("packet", en["unit_packet_one"]); assertEquals("packets", en["unit_packet"])
        assertEquals("kg", en["unit_kg_one"]); assertEquals("kg", en["unit_kg"])
        assertEquals("dozen", en["unit_dozen_one"]); assertEquals("dozen", en["unit_dozen"])
        for (u in listOf("piece", "kg", "litre", "packet", "dozen")) assertEquals(bn["unit_$u"], bn["unit_${u}_one"])
        assertEquals("পিস", bn["unit_piece"])
    }

    @Test
    fun theReceiptUsesTheSingularForOne() {
        val labels = testLabels.copy(unitOne = { if (it == ProductUnit.PIECE) "pc" else "one" })
        val one = ReceiptLine("Fan", 1_000, ProductUnit.PIECE, 3_000, 3_000)
        val two = one.copy(quantity = 2_000)
        assertEquals("১ pc", receiptQuantity(one, labels.copy(unit = { "pcs" })))
        assertEquals("২ pcs", receiptQuantity(two, labels.copy(unit = { "pcs" })))
        // no singular given: the same word as before
        assertEquals("১ পিস", receiptQuantity(one, testLabels))
    }

    @Test
    fun theDueBookLineSaysLastEntryNotUntil() {
        assertEquals("Last entry: %1\$s", xml("src/main/res/values-en/strings.xml")["khata_last_entry"])
        assertEquals("সর্বশেষ লেনদেন: %1\$s", xml("src/main/res/values/strings.xml")["khata_last_entry"])
    }
}
