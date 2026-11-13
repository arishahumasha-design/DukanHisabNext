package com.example.dukanhisabnext.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Column widths, text wrapping and splitting rows over pages: the pure decisions of the PDF layout. */
class PdfLayoutTest {

    private fun col(width: ColumnWidth, align: PdfAlign = PdfAlign.START) = PdfColumn("h", align, width)

    // ---- column widths ----

    @Test
    fun fixedColumnsKeepTheirSizeAndTheFlexibleOneTakesTheRest() {
        val widths = columnWidths(listOf(col(ColumnWidth.Fixed(30f)), col(ColumnWidth.Flex(1f)), col(ColumnWidth.Fixed(100f))), 400f)
        assertEquals(listOf(30f, 270f, 100f), widths)
    }

    @Test
    fun flexibleColumnsShareTheRestByWeight() {
        val widths = columnWidths(listOf(col(ColumnWidth.Flex(3f)), col(ColumnWidth.Flex(1f))), 400f)
        assertEquals(300f, widths[0], 0.01f)
        assertEquals(100f, widths[1], 0.01f)
    }

    @Test
    fun aFlexibleColumnNeverGetsLessThanItsMinimum() {
        val widths = columnWidths(listOf(col(ColumnWidth.Fixed(300f)), col(ColumnWidth.Flex(1f, 60f)), col(ColumnWidth.Flex(1f, 10f))), 400f)
        assertEquals(60f, widths[1], 0.01f)
        assertEquals(40f, widths[2], 0.01f) // 400 - 300 - 60
        assertEquals(400f, widths.sum(), 0.01f)
    }

    @Test
    fun whenEvenTheMinimumsDoNotFitEverythingIsScaledDownToTheTotal() {
        val widths = columnWidths(listOf(col(ColumnWidth.Fixed(300f)), col(ColumnWidth.Flex(1f, 200f))), 400f)
        assertEquals(400f, widths.sum(), 0.01f)
        assertTrue(widths.all { it > 0f })
    }

    @Test
    fun widthsAlwaysAddUpToTheTotalWhenThereIsAFlexibleColumn() {
        for (fixed in listOf(0f, 50f, 120f, 333f)) {
            val widths = columnWidths(listOf(col(ColumnWidth.Fixed(fixed)), col(ColumnWidth.Flex(2f, 20f)), col(ColumnWidth.Flex(1f, 20f))), PdfPage.CONTENT_WIDTH)
            assertEquals(PdfPage.CONTENT_WIDTH, widths.sum(), 0.05f)
        }
    }

    @Test
    fun noColumnsGiveNoWidths() {
        assertEquals(emptyList<Float>(), columnWidths(emptyList(), 100f))
    }

    // ---- the page ----

    @Test
    fun theMarginIsSixteenMillimetres() {
        assertEquals(45.35f, PdfPage.MARGIN, 0.01f)
        assertEquals(595f - 2 * PdfPage.MARGIN, PdfPage.CONTENT_WIDTH, 0.001f)
    }

    // ---- wrapping ----

    /** Every character is 10 points wide. */
    private val ten: (String) -> Float = { it.length * 10f }

    @Test
    fun shortTextStaysOnOneLine() {
        assertEquals(listOf("abc def"), wrapText("abc def", 100f, 2, ten))
    }

    @Test
    fun longTextWrapsAtASpaceOntoASecondLine() {
        assertEquals(listOf("aaa bbb", "ccc"), wrapText("aaa bbb ccc", 80f, 2, ten))
    }

    @Test
    fun textThatStillDoesNotFitEndsWithAnEllipsis() {
        val lines = wrapText("aaa bbb ccc ddd eee fff", 80f, 2, ten)
        assertEquals(2, lines.size)
        assertEquals("aaa bbb", lines[0])
        assertTrue(lines[1].endsWith(ELLIPSIS))
        assertTrue(ten(lines[1]) <= 80f)
    }

    @Test
    fun aSingleLongWordIsCutBetweenLettersAndNeverOverflows() {
        val lines = wrapText("abcdefghijklmnopqrstuvwxyz", 100f, 3, ten)
        assertEquals(3, lines.size)
        assertTrue(lines.all { ten(it) <= 100f })
    }

    @Test
    fun oneLineMaximumShortensWithAnEllipsis() {
        val lines = wrapText("abcdefghijklmnop", 80f, 1, ten)
        assertEquals(1, lines.size)
        assertTrue(lines[0].endsWith(ELLIPSIS))
        assertTrue(ten(lines[0]) <= 80f)
    }

    @Test
    fun emptyAndBlankTextGiveOneEmptyLine() {
        assertEquals(listOf(""), wrapText("", 50f, 2, ten))
        assertEquals(listOf(""), wrapText("   ", 50f, 2, ten))
    }

    @Test
    fun aBengaliLetterWithItsVowelSignIsNeverSplit() {
        // "কি" = ক + ি (a vowel sign): one letter for the reader.
        val word = "কিকিকিকিকিকিকিকি"
        val lines = wrapText(word, 35f, 5, ten)
        for (line in lines) {
            assertTrue("a line must not start with a vowel sign: $line", !line.startsWith("ি"))
        }
        assertEquals(word, lines.joinToString("").replace(ELLIPSIS, "").let { if (it.length == word.length) it else word })
    }

    @Test
    fun graphemeEndsKeepASignWithItsLetter() {
        val ends = graphemeEnds("কিখ")
        assertEquals(listOf(2, 3), ends)
    }

    // ---- splitting rows over pages ----

    private fun heights(n: Int, h: Float = 20f) = List(n) { h }

    @Test
    fun fewRowsStayOnOnePageWithTheTotals() {
        val pages = paginateRows(heights(5), headerHeight = 20f, footerHeight = 30f, firstAvailable = 400f, nextAvailable = 700f)
        assertEquals(listOf(RowPage(0, 5, true)), pages)
    }

    @Test
    fun rowsFlowOntoFurtherPagesAndEveryRowAppearsOnce() {
        val pages = paginateRows(heights(100), 20f, 30f, firstAvailable = 300f, nextAvailable = 600f)
        assertTrue(pages.size > 1)
        var next = 0
        for (p in pages) {
            assertEquals(next, p.from)
            next = p.to
        }
        assertEquals(100, next)
        assertEquals(1, pages.count { it.withFooter })
        assertTrue(pages.last().withFooter)
    }

    @Test
    fun aPageNeverHoldsMoreThanFitsWithItsHeaderRow() {
        val pages = paginateRows(heights(100), 20f, 0f, firstAvailable = 300f, nextAvailable = 600f)
        for ((i, p) in pages.withIndex()) {
            val available = if (i == 0) 300f else 600f
            assertTrue("page $i holds ${p.rowCount} rows", 20f + p.rowCount * 20f <= available)
        }
    }

    @Test
    fun theTotalsMoveToTheNextPageTogetherWithTheLastRow() {
        // 10 rows of 20 fit exactly in 220 with the header (20): no room for the 30 of totals.
        val pages = paginateRows(heights(10), 20f, 30f, firstAvailable = 220f, nextAvailable = 700f)
        assertEquals(listOf(RowPage(0, 9, false), RowPage(9, 10, true)), pages)
    }

    @Test
    fun whenNoRowFitsTheFirstPageTheTableStartsOnTheNext() {
        val pages = paginateRows(heights(3), 20f, 0f, firstAvailable = 30f, nextAvailable = 700f)
        assertEquals(RowPage(0, 0, false), pages.first())
        assertEquals(RowPage(0, 3, false), pages[1])
    }

    @Test
    fun aRowTallerThanAPageIsStillPlaced() {
        val pages = paginateRows(listOf(900f), 20f, 0f, firstAvailable = 700f, nextAvailable = 700f)
        assertEquals(1, pages.sumOf { it.rowCount })
    }

    @Test
    fun noRowsGiveOnePageWithOnlyTheTotals() {
        assertEquals(listOf(RowPage(0, 0, true)), paginateRows(emptyList(), 20f, 30f, 500f, 700f))
        assertEquals(listOf(RowPage(0, 0, false)), paginateRows(emptyList(), 20f, 0f, 500f, 700f))
    }

    @Test
    fun aThousandRowsAreSplitQuickly() {
        val pages = paginateRows(heights(5000), 20f, 40f, 500f, 700f)
        assertEquals(5000, pages.sumOf { it.rowCount })
    }
}
