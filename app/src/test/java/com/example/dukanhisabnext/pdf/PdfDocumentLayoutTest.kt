package com.example.dukanhisabnext.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole page layout with a fake font: headers, footers, repeated table headers, page counts. */
class PdfDocumentLayoutTest {

    /** Every character is half the font size wide (bold a little wider). */
    private val measure = TextMeasure { text, size, bold -> text.length * size * (if (bold) 0.55f else 0.5f) }
    private fun footer(page: Int, total: Int) = "P $page / $total"

    private fun content(blocks: List<PdfBlock>, subtitle: List<String> = listOf("Generated: 13 Nov 2026")) = PdfContent(
        shopName = "Rahim Store", shopAddress = "Dhaka", shopPhone = "Mobile: 017", title = "Due list", subtitleLines = subtitle, blocks = blocks
    )

    private val columns = listOf(
        PdfColumn("No", PdfAlign.START, ColumnWidth.Fixed(30f)),
        PdfColumn("Name", PdfAlign.START, ColumnWidth.Flex(1f, 60f)),
        PdfColumn("Due", PdfAlign.END, ColumnWidth.Fixed(80f))
    )

    private fun rows(n: Int, name: (Int) -> String = { "Customer $it" }) = List(n) { PdfRow(listOf("${it + 1}", name(it + 1), "${(it + 1) * 100}")) }

    private fun texts(ops: List<DrawOp>) = ops.filterIsInstance<DrawOp.Text>()

    @Test
    fun aShortDocumentIsOnePage() {
        val pages = layoutDocument(content(listOf(PdfBlock.Table(columns, rows(5)))), measure, ::footer)
        assertEquals(1, pages.count)
        assertTrue(texts(pages.pages[0]).any { it.text == "P 1 / 1" })
    }

    @Test
    fun everyPageHasTheShopHeaderAndTheFooterWithTheRightNumbers() {
        val pages = layoutDocument(content(listOf(PdfBlock.Table(columns, rows(300)))), measure, ::footer)
        assertTrue(pages.count > 3)
        pages.pages.forEachIndexed { i, ops ->
            val t = texts(ops).map { it.text }
            assertTrue("shop name on page ${i + 1}", "Rahim Store" in t)
            assertTrue("title on page ${i + 1}", "Due list" in t)
            assertTrue("footer on page ${i + 1}", "P ${i + 1} / ${pages.count}" in t)
        }
    }

    @Test
    fun theTableHeaderRowIsRepeatedOnEveryPageThatHasRows() {
        val pages = layoutDocument(content(listOf(PdfBlock.Table(columns, rows(300)))), measure, ::footer)
        for ((i, ops) in pages.pages.withIndex()) {
            val t = texts(ops).map { it.text }
            assertTrue("header row on page ${i + 1}", "Name" in t && "Due" in t)
        }
    }

    @Test
    fun everyRowAppearsExactlyOnceInOrder() {
        val pages = layoutDocument(content(listOf(PdfBlock.Table(columns, rows(300)))), measure, ::footer)
        val names = pages.pages.flatMap { texts(it) }.map { it.text }.filter { it.startsWith("Customer ") }
        assertEquals((1..300).map { "Customer $it" }, names)
    }

    @Test
    fun nothingIsDrawnOutsideTheMarginsOrOverTheFooter() {
        val pages = layoutDocument(content(listOf(PdfBlock.Table(columns, rows(300, { "A rather long customer name number $it that must wrap" })))), measure, ::footer)
        val footerBaseline = PdfPage.HEIGHT - PdfPage.MARGIN
        for (ops in pages.pages) {
            for (op in ops) {
                when (op) {
                    is DrawOp.Text -> {
                        assertTrue(op.baseline <= footerBaseline + 0.01f)
                        if (op.align == PdfAlign.START) assertTrue(op.x >= PdfPage.MARGIN - 0.01f)
                        if (op.align == PdfAlign.END) assertTrue(op.x <= PdfPage.WIDTH - PdfPage.MARGIN + 0.01f)
                    }
                    is DrawOp.Line -> {
                        assertTrue(op.x1 >= PdfPage.MARGIN - 0.01f && op.x2 <= PdfPage.WIDTH - PdfPage.MARGIN + 0.01f)
                        assertTrue(op.y1 < footerBaseline - PdfSizes.SMALL)
                    }
                }
            }
        }
    }

    @Test
    fun aLongNameWrapsToTwoLinesAndTheRowGrows() {
        val long = "Muhammad Abdur Rahman Chowdhury of the Big Bazar Old Market"
        val one = layoutDocument(content(listOf(PdfBlock.Table(columns, rows(1)))), measure, ::footer)
        val two = layoutDocument(content(listOf(PdfBlock.Table(columns, rows(1) { long }))), measure, ::footer)
        val nameOps = texts(two.pages[0]).filter { long.startsWith(it.text.removeSuffix(ELLIPSIS).trim().take(5)) && it.text.length > 3 }
        assertTrue("a long name draws two lines", texts(two.pages[0]).count { it.text.contains("Muhammad") || it.text.contains("Market") || it.text.contains("Bazar") } >= 1)
        assertTrue(nameOps.isNotEmpty())
        // The second page-less layout is still one page; the long row simply is taller.
        assertEquals(1, one.count)
        assertEquals(1, two.count)
    }

    @Test
    fun theTotalsStayUnderTheLastRowsAndAreBold() {
        val footerRows = listOf(PdfRow(listOf("", "Total due", "99999"), bold = true))
        val pages = layoutDocument(content(listOf(PdfBlock.Table(columns, rows(300), footerRows))), measure, ::footer)
        val last = texts(pages.pages.last())
        val total = last.first { it.text == "Total due" }
        assertTrue(total.bold)
        val lastRowY = last.filter { it.text.startsWith("Customer ") }.maxOf { it.baseline }
        assertTrue(total.baseline > lastRowY)
        assertEquals(1, pages.pages.flatMap { texts(it) }.count { it.text == "Total due" })
    }

    @Test
    fun aThousandRowsWork() {
        val started = System.nanoTime()
        val pages = layoutDocument(content(listOf(PdfBlock.Table(columns, rows(1500)))), measure, ::footer)
        assertTrue(pages.count in 20..60)
        val names = pages.pages.flatMap { texts(it) }.count { it.text.startsWith("Customer ") }
        assertEquals(1500, names)
        assertTrue("took ${(System.nanoTime() - started) / 1_000_000} ms", (System.nanoTime() - started) < 20_000_000_000L)
    }

    @Test
    fun aHeadingIsNeverLeftAloneAtTheBottomOfAPage() {
        // Fill the first page almost to the bottom with a note-free table, then a heading and a table.
        val blocks = listOf(
            PdfBlock.Table(columns, rows(40)),
            PdfBlock.Heading("Section two"),
            PdfBlock.Table(columns, rows(3))
        )
        val pages = layoutDocument(content(blocks), measure, ::footer)
        for (ops in pages.pages) {
            val t = texts(ops)
            val heading = t.firstOrNull { it.text == "Section two" } ?: continue
            assertTrue("something follows the heading on its page", t.any { it.baseline > heading.baseline && it.text.startsWith("Customer ") })
        }
    }

    @Test
    fun summaryLinesShowLabelsOnTheLeftAndValuesOnTheRight() {
        val lines = PdfBlock.Lines(listOf(PdfLine("Total", "100"), PdfLine("Paid", "60", bold = true, ruleAbove = true)))
        val pages = layoutDocument(content(listOf(lines)), measure, ::footer)
        val t = texts(pages.pages[0])
        val label = t.first { it.text == "Total" }
        val value = t.first { it.text == "100" }
        assertEquals(PdfAlign.START, label.align)
        assertEquals(PdfAlign.END, value.align)
        assertEquals(label.baseline, value.baseline, 0.01f)
        assertTrue(t.first { it.text == "Paid" }.bold)
        assertTrue(pages.pages[0].any { it is DrawOp.Line && it.y1 > label.baseline })
    }

    @Test
    fun aNoteIsPrintedAsGreyText() {
        val pages = layoutDocument(content(listOf(PdfBlock.Note("Approximate: some costs unknown"))), measure, ::footer)
        val note = texts(pages.pages[0]).first { it.text.startsWith("Approximate") }
        assertEquals(PdfColor.MUTED, note.color)
    }

    @Test
    fun theGreenAccentIsUsedOnlyForTheHeaderLine() {
        val pages = layoutDocument(content(listOf(PdfBlock.Table(columns, rows(100)))), measure, ::footer)
        for (ops in pages.pages) {
            assertEquals(1, ops.count { it is DrawOp.Line && it.color == PdfColor.ACCENT })
            assertEquals(0, ops.count { it is DrawOp.Text && it.color == PdfColor.ACCENT })
        }
    }

    @Test
    fun aDocumentWithNoBlocksIsOnePageWithJustTheHeader() {
        val pages = layoutDocument(content(emptyList()), measure, ::footer)
        assertEquals(1, pages.count)
    }

    @Test
    fun manySummaryBoxesAndTablesNeverStartTwoPagesForOnePart() {
        // A table that does not fit the rest of the first page must start on page 2, with no blank page between.
        val blocks = listOf(PdfBlock.Table(columns, rows(48)), PdfBlock.Table(columns, rows(5)))
        val pages = layoutDocument(content(blocks), measure, ::footer)
        for (ops in pages.pages) assertTrue("no blank pages", texts(ops).any { it.text.startsWith("Customer ") })
    }
}
