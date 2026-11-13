package com.example.dukanhisabnext.pdf

// What a PDF contains (the content model the builders fill) and how it is laid out on A4 pages
// (a list of drawing instructions per page). Pure Kotlin: PdfWriter.kt draws the instructions.

// =========================================================================================
// The content
// =========================================================================================

/** One label and value of a summary box ("মোট বাকি ........ ৳ ১,২০০"). */
data class PdfLine(
    val label: String,
    val value: String,
    val bold: Boolean = false,
    /** A thin rule above the line (a total). */
    val ruleAbove: Boolean = false,
    /** A small grey line (a breakdown or a note under the line above). */
    val small: Boolean = false
)

/** One row of a table. [bold] rows (totals) get a rule above them. */
data class PdfRow(val cells: List<String>, val bold: Boolean = false)

sealed interface PdfBlock {
    data class Heading(val text: String) : PdfBlock

    /** A summary box: lines with the value on the right. */
    data class Lines(val lines: List<PdfLine>) : PdfBlock

    /** A table with a repeated header row; [footer] rows (totals) stay under the last rows. */
    data class Table(val columns: List<PdfColumn>, val rows: List<PdfRow>, val footer: List<PdfRow> = emptyList()) : PdfBlock

    /** A paragraph of small grey text (for example the "আনুমানিক" note). */
    data class Note(val text: String) : PdfBlock

    data class Gap(val points: Float) : PdfBlock
}

/** A whole document: the shop, the title, the period or date lines, and the blocks. */
data class PdfContent(
    val shopName: String,
    val shopAddress: String?,
    val shopPhone: String?,
    val title: String,
    /** Grey lines under the title: the period, "তৈরির তারিখ: …", a customer's name and mobile. */
    val subtitleLines: List<String>,
    val blocks: List<PdfBlock>
)

// =========================================================================================
// The drawing instructions
// =========================================================================================

sealed interface DrawOp {
    /** [x] is the anchor: the left edge for START, the right edge for END, the middle for CENTER. [baseline] is the text baseline. */
    data class Text(
        val x: Float,
        val baseline: Float,
        val text: String,
        val size: Float,
        val bold: Boolean,
        val color: PdfColor,
        val align: PdfAlign = PdfAlign.START
    ) : DrawOp

    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val width: Float, val color: PdfColor) : DrawOp
}

/** The sizes of a document (points). Bengali needs a line height of about 1.5 x the size so no sign is clipped. */
object PdfSizes {
    const val SHOP = 16f
    const val TITLE = 13f
    const val BODY = 10f
    const val SMALL = 9f
    const val HEADING = 11.5f
    const val LINE_FACTOR = 1.5f
    const val CELL_PAD_V = 4f
    const val CELL_PAD_H = 4f
    const val MAX_CELL_LINES = 2
    const val GAP = 10f

    /** Where the text baseline sits inside a line of height [lineHeight]. */
    fun baselineOffset(lineHeight: Float) = lineHeight * 0.78f
}

private const val FOOTER_HEIGHT = 22f

// =========================================================================================
// The layout
// =========================================================================================

/** Every page of a document as drawing instructions, the footer ("পাতা ১ / ৩") included. */
data class PdfPages(val pages: List<List<DrawOp>>) {
    val count: Int get() = pages.size
}

/**
 * Lays [content] out on A4 pages: the header on every page, the blocks flowing down, tables split over
 * pages with a repeated header row, and the footer on every page. [pageFooter] makes the footer text from
 * the page number and the page count. Nothing here depends on Android: [measure] is the only link to the font.
 */
fun layoutDocument(content: PdfContent, measure: TextMeasure, pageFooter: (page: Int, total: Int) -> String): PdfPages {
    val composer = Composer(content, measure)
    composer.run()
    val total = composer.pages.size
    val withFooters = composer.pages.mapIndexed { index, ops ->
        ops + DrawOp.Text(
            x = PdfPage.WIDTH / 2f,
            baseline = PdfPage.HEIGHT - PdfPage.MARGIN,
            text = pageFooter(index + 1, total),
            size = PdfSizes.SMALL,
            bold = false,
            color = PdfColor.MUTED,
            align = PdfAlign.CENTER
        )
    }
    return PdfPages(withFooters)
}

private class Composer(private val content: PdfContent, private val measure: TextMeasure) {
    val pages = mutableListOf<MutableList<DrawOp>>()
    private var ops = mutableListOf<DrawOp>()
    private var y = 0f

    private val left = PdfPage.MARGIN
    private val right = PdfPage.MARGIN + PdfPage.CONTENT_WIDTH
    private val bottom = PdfPage.HEIGHT - PdfPage.MARGIN - FOOTER_HEIGHT

    private fun width(text: String, size: Float, bold: Boolean) = measure.width(text, size, bold)
    private fun lineHeight(size: Float) = size * PdfSizes.LINE_FACTOR

    /** The header ops and their height, the same for every page. */
    private val header: Pair<List<DrawOp>, Float> = buildHeader()

    fun run() {
        startPage()
        for (block in content.blocks) place(block)
        pages += ops
    }

    private fun startPage() {
        if (pages.isNotEmpty() || ops.isNotEmpty()) pages += ops
        ops = mutableListOf()
        ops += header.first
        y = PdfPage.MARGIN + header.second + PdfSizes.GAP
    }

    private fun room() = bottom - y

    // ---- the header ----

    private fun buildHeader(): Pair<List<DrawOp>, Float> {
        val h = mutableListOf<DrawOp>()
        var top = PdfPage.MARGIN
        fun text(t: String, size: Float, bold: Boolean, color: PdfColor) {
            val lh = lineHeight(size)
            h += DrawOp.Text(left, top + PdfSizes.baselineOffset(lh), t, size, bold, color)
            top += lh
        }
        for (line in wrapText(content.shopName, PdfPage.CONTENT_WIDTH, 2) { width(it, PdfSizes.SHOP, true) }) text(line, PdfSizes.SHOP, true, PdfColor.TEXT)
        content.shopAddress?.takeIf { it.isNotBlank() }?.let { a ->
            for (line in wrapText(a, PdfPage.CONTENT_WIDTH, 2) { width(it, PdfSizes.SMALL, false) }) text(line, PdfSizes.SMALL, false, PdfColor.MUTED)
        }
        content.shopPhone?.takeIf { it.isNotBlank() }?.let { text(it, PdfSizes.SMALL, false, PdfColor.MUTED) }
        top += 6f
        for (line in wrapText(content.title, PdfPage.CONTENT_WIDTH, 2) { width(it, PdfSizes.TITLE, true) }) text(line, PdfSizes.TITLE, true, PdfColor.TEXT)
        for (sub in content.subtitleLines) {
            for (line in wrapText(sub, PdfPage.CONTENT_WIDTH, 2) { width(it, PdfSizes.SMALL, false) }) text(line, PdfSizes.SMALL, false, PdfColor.MUTED)
        }
        top += 5f
        h += DrawOp.Line(left, top, right, top, 1.5f, PdfColor.ACCENT) // the one green accent
        return h to (top - PdfPage.MARGIN)
    }

    // ---- the blocks ----

    private fun place(block: PdfBlock) {
        when (block) {
            is PdfBlock.Gap -> y += block.points
            is PdfBlock.Heading -> heading(block.text)
            is PdfBlock.Lines -> lines(block)
            is PdfBlock.Note -> note(block.text)
            is PdfBlock.Table -> table(block)
        }
    }

    /** A heading is never the last thing on a page: it needs room for itself and a little of what follows. */
    private fun heading(text: String) {
        val lh = lineHeight(PdfSizes.HEADING)
        if (room() < lh + PdfSizes.GAP + 70f) startPage()
        y += PdfSizes.GAP * 0.6f
        ops += DrawOp.Text(left, y + PdfSizes.baselineOffset(lh), text, PdfSizes.HEADING, true, PdfColor.TEXT)
        y += lh + 2f
    }

    private fun note(text: String) {
        val size = PdfSizes.SMALL
        val lh = lineHeight(size)
        for (line in wrapText(text, PdfPage.CONTENT_WIDTH, 6) { width(it, size, false) }) {
            if (room() < lh) startPage()
            ops += DrawOp.Text(left, y + PdfSizes.baselineOffset(lh), line, size, false, PdfColor.MUTED)
            y += lh
        }
        y += 4f
    }

    /** The summary box: label on the left (wrapped to two lines at most), value on the right. */
    private fun lines(block: PdfBlock.Lines) {
        val valueWidth = PdfPage.CONTENT_WIDTH * 0.38f
        val labelWidth = PdfPage.CONTENT_WIDTH - valueWidth - 8f
        class Laid(val line: PdfLine, val labelLines: List<String>, val size: Float, val height: Float)
        val laid = block.lines.map { l ->
            val size = if (l.small) PdfSizes.SMALL else PdfSizes.BODY
            val wrapped = wrapText(l.label, labelWidth, PdfSizes.MAX_CELL_LINES) { width(it, size, l.bold) }
            Laid(l, wrapped, size, wrapped.size * lineHeight(size) + 2 * 2f)
        }
        val pagesOfRows = paginateRows(laid.map { it.height }, 0f, 0f, room(), bottomSpace())
        // An empty first part means "no line fits here": the box starts on the next page.
        var breakBefore = pagesOfRows.first().rowCount == 0 && laid.isNotEmpty()
        var drawn = false
        for (page in pagesOfRows) {
            if (page.rowCount == 0) continue
            if (drawn || breakBefore) startPage()
            breakBefore = false
            drawn = true
            for (i in page.from until page.to) {
                val item = laid[i]
                val lh = lineHeight(item.size)
                if (item.line.ruleAbove) ops += DrawOp.Line(left, y, right, y, 0.8f, PdfColor.TEXT)
                val color = if (item.line.small) PdfColor.MUTED else PdfColor.TEXT
                item.labelLines.forEachIndexed { k, text ->
                    ops += DrawOp.Text(left, y + 2f + k * lh + PdfSizes.baselineOffset(lh), text, item.size, item.line.bold, color)
                }
                if (item.line.value.isNotEmpty()) {
                    ops += DrawOp.Text(right, y + 2f + PdfSizes.baselineOffset(lh), item.line.value, item.size, item.line.bold, color, PdfAlign.END)
                }
                y += item.height
            }
        }
        y += 4f
    }

    /** The space on a fresh page below the header. */
    private fun bottomSpace(): Float = bottom - (PdfPage.MARGIN + header.second + PdfSizes.GAP)

    // ---- tables ----

    private fun table(block: PdfBlock.Table) {
        val widths = columnWidths(block.columns, PdfPage.CONTENT_WIDTH)
        val pad = PdfSizes.CELL_PAD_H

        /** The wrapped lines of every cell of a row and the row height. */
        class LaidRow(val row: PdfRow, val cells: List<List<String>>, val size: Float, val bold: Boolean, val height: Float)

        fun lay(row: PdfRow, size: Float, bold: Boolean): LaidRow {
            val cells = block.columns.indices.map { c ->
                val text = row.cells.getOrElse(c) { "" }
                wrapText(text, widths[c] - 2 * pad, PdfSizes.MAX_CELL_LINES) { width(it, size, bold) }
            }
            val lines = cells.maxOf { it.size }.coerceAtLeast(1)
            return LaidRow(row, cells, size, bold, lines * lineHeight(size) + 2 * PdfSizes.CELL_PAD_V)
        }

        val headerRow = lay(PdfRow(block.columns.map { it.header }), PdfSizes.SMALL + 0.5f, true)
        val laidRows = block.rows.map { lay(it, PdfSizes.BODY, it.bold) }
        val laidFooter = block.footer.map { lay(it, PdfSizes.BODY, true) }
        val footerHeight = laidFooter.sumOf { it.height.toDouble() }.toFloat()

        val pagesOfRows = paginateRows(
            laidRows.map { it.height }, headerRow.height, footerHeight, room(), bottomSpace()
        )

        fun drawRow(r: LaidRow, ruleAbove: Boolean, ruleBelow: Boolean) {
            if (ruleAbove) ops += DrawOp.Line(left, y, right, y, 0.8f, PdfColor.TEXT)
            val lh = lineHeight(r.size)
            var x = left
            for (c in block.columns.indices) {
                val w = widths[c]
                val anchor = when (block.columns[c].align) {
                    PdfAlign.START -> x + pad
                    PdfAlign.END -> x + w - pad
                    PdfAlign.CENTER -> x + w / 2f
                }
                r.cells[c].forEachIndexed { k, line ->
                    if (line.isNotEmpty()) {
                        ops += DrawOp.Text(
                            anchor, y + PdfSizes.CELL_PAD_V + k * lh + PdfSizes.baselineOffset(lh),
                            line, r.size, r.bold, PdfColor.TEXT, block.columns[c].align
                        )
                    }
                }
                x += w
            }
            y += r.height
            if (ruleBelow) ops += DrawOp.Line(left, y, right, y, if (r === headerRow) 0.8f else 0.4f, if (r === headerRow) PdfColor.TEXT else PdfColor.RULE)
        }

        // An empty first part means "no row fits here": the table starts on the next page.
        var breakBefore = block.rows.isNotEmpty() && pagesOfRows.first().let { it.rowCount == 0 && !it.withFooter }
        var drawn = false
        for (page in pagesOfRows) {
            if (page.rowCount == 0 && !page.withFooter) continue
            if (drawn || breakBefore) startPage() // every part after the first goes on a new page
            breakBefore = false
            drawn = true
            drawRow(headerRow, ruleAbove = false, ruleBelow = true) // the header row is repeated on every part
            for (i in page.from until page.to) {
                drawRow(laidRows[i], ruleAbove = laidRows[i].bold, ruleBelow = !laidRows[i].bold)
            }
            if (page.withFooter) {
                laidFooter.forEachIndexed { k, f -> drawRow(f, ruleAbove = k == 0, ruleBelow = false) }
            }
        }
        y += 6f
    }
}
