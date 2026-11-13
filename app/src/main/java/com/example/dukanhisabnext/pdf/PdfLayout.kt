package com.example.dukanhisabnext.pdf

import java.text.BreakIterator

// PDF export: the pure part (no Android). Everything that decides WHERE things go on a page lives here
// and in PdfDocumentLayout.kt, as plain functions, so it can be unit-tested on the PC with a fake text
// measurer. The Android part (PdfWriter.kt) only draws what these functions decided. All sizes are in
// points (1/72 inch), the unit of android.graphics.pdf.PdfDocument.

/** A4 portrait in points, and the 16 mm margin. */
object PdfPage {
    const val WIDTH = 595f
    const val HEIGHT = 842f

    /** 16 mm = 16 / 25.4 inch = 45.35 points. */
    const val MARGIN = 16f * 72f / 25.4f

    /** The width of the area the content may use. */
    const val CONTENT_WIDTH = WIDTH - 2 * MARGIN
}

/** How text is placed against its column edge. */
enum class PdfAlign { START, END, CENTER }

/** The few colours of a document: dark text, grey notes, one green accent, thin grey rules. */
enum class PdfColor { TEXT, MUTED, ACCENT, RULE }

/** Measures text the way the real paint will (the tests pass a fake). */
fun interface TextMeasure {
    fun width(text: String, size: Float, bold: Boolean): Float
}

// =========================================================================================
// Columns
// =========================================================================================

/** The width of a table column: a fixed number of points, or a share of what is left. */
sealed interface ColumnWidth {
    data class Fixed(val points: Float) : ColumnWidth

    /** A share ([weight]) of the width the fixed columns leave, but never narrower than [minPoints]. */
    data class Flex(val weight: Float, val minPoints: Float = 40f) : ColumnWidth
}

data class PdfColumn(val header: String, val align: PdfAlign, val width: ColumnWidth)

/**
 * The width of every column, in points. They always add up to [totalWidth]:
 *  1. fixed columns get their size;
 *  2. the flexible columns share the rest by their weights, none under its minimum;
 *  3. if even the minimums do not fit, every column is scaled down by the same factor.
 */
fun columnWidths(columns: List<PdfColumn>, totalWidth: Float): List<Float> {
    if (columns.isEmpty()) return emptyList()
    val fixedSum = columns.sumOf { (it.width as? ColumnWidth.Fixed)?.points?.toDouble() ?: 0.0 }.toFloat()
    val flex = columns.mapNotNull { it.width as? ColumnWidth.Flex }
    val room = (totalWidth - fixedSum).coerceAtLeast(0f)

    // Flexible columns: weights first, then raise the ones under their minimum and take it from the others.
    val shares = MutableList(columns.size) { 0f }
    val flexIdx = columns.indices.filter { columns[it].width is ColumnWidth.Flex }
    if (flexIdx.isNotEmpty()) {
        val locked = mutableSetOf<Int>()
        var remaining = room
        var guard = 0
        while (guard++ <= flexIdx.size) {
            val free = flexIdx.filter { it !in locked }
            if (free.isEmpty()) break
            val weightSum = free.sumOf { (columns[it].width as ColumnWidth.Flex).weight.toDouble() }.toFloat()
            var changed = false
            for (i in free) {
                val f = columns[i].width as ColumnWidth.Flex
                val share = if (weightSum > 0f) remaining * f.weight / weightSum else remaining / free.size
                if (share < f.minPoints) {
                    shares[i] = f.minPoints
                    locked += i
                    remaining -= f.minPoints
                    changed = true
                    break // recompute the others with what is left
                }
            }
            if (!changed) {
                for (i in free) {
                    val f = columns[i].width as ColumnWidth.Flex
                    shares[i] = if (weightSum > 0f) remaining * f.weight / weightSum else remaining / free.size
                }
                break
            }
        }
    }
    val widths = columns.mapIndexed { i, c ->
        when (val w = c.width) {
            is ColumnWidth.Fixed -> w.points
            is ColumnWidth.Flex -> shares[i]
        }
    }
    val sum = widths.sum()
    return if (sum > totalWidth + 0.01f && sum > 0f) widths.map { it * totalWidth / sum } else widths
}

// =========================================================================================
// Wrapping long text into at most two lines
// =========================================================================================

/** "…" is what the last line ends with when the text did not fit. */
const val ELLIPSIS = "…"

/**
 * The lines of [text] inside [maxWidth], at most [maxLines] (a long name wraps onto a second line; if it
 * still does not fit the last line ends with "…"). Lines break at spaces; a single word wider than a line
 * is cut between letters ("grapheme clusters": a Bengali letter with its vowel signs or a conjunct is
 * never split). [measure] gives the width of a piece of text.
 */
fun wrapText(text: String, maxWidth: Float, maxLines: Int, measure: (String) -> Float): List<String> {
    require(maxLines >= 1) { "maxLines must be 1 or more" }
    val clean = text.replace('\n', ' ').trim()
    if (clean.isEmpty()) return listOf("")
    if (measure(clean) <= maxWidth) return listOf(clean)

    val lines = mutableListOf<String>()
    var rest = clean
    while (rest.isNotEmpty() && lines.size < maxLines) {
        val isLast = lines.size == maxLines - 1
        if (measure(rest) <= maxWidth) {
            lines += rest
            rest = ""
            break
        }
        if (isLast) {
            lines += ellipsize(rest, maxWidth, measure)
            rest = ""
            break
        }
        val cut = breakPoint(rest, maxWidth, measure)
        lines += rest.substring(0, cut).trimEnd()
        rest = rest.substring(cut).trimStart()
    }
    return lines
}

/** Where to break [text] so the first part fits [maxWidth]: after the last space that fits, else between letters. At least one letter. */
private fun breakPoint(text: String, maxWidth: Float, measure: (String) -> Float): Int {
    val clusters = graphemeEnds(text)
    var fitEnd = clusters.first()
    var lastSpaceEnd = -1
    for (end in clusters) {
        if (measure(text.substring(0, end).trimEnd()) > maxWidth) break
        fitEnd = end
        if (end > 0 && text[end - 1] == ' ') lastSpaceEnd = end
    }
    if (fitEnd >= text.length) return text.length
    // Prefer a space, unless it would leave the line almost empty.
    return if (lastSpaceEnd > 0) lastSpaceEnd else fitEnd
}

/** The last line, shortened by whole letters until it plus "…" fits. */
private fun ellipsize(text: String, maxWidth: Float, measure: (String) -> Float): String {
    if (measure(text) <= maxWidth) return text
    val ends = graphemeEnds(text)
    var best = 0
    for (end in ends) {
        if (measure(text.substring(0, end).trimEnd() + ELLIPSIS) > maxWidth) break
        best = end
    }
    return if (best == 0) ELLIPSIS else text.substring(0, best).trimEnd() + ELLIPSIS
}

/** The end offset of every user-perceived letter of [text] (a base letter with its signs counts as one). */
fun graphemeEnds(text: String): List<Int> {
    val it = BreakIterator.getCharacterInstance()
    it.setText(text)
    val ends = mutableListOf<Int>()
    var end = it.next()
    while (end != BreakIterator.DONE) {
        ends += end
        end = it.next()
    }
    if (ends.isEmpty() && text.isNotEmpty()) ends += text.length
    return ends
}

// =========================================================================================
// Splitting rows over pages
// =========================================================================================

/** One page's share of a table: rows [from] until [to] (a row is never split), and whether the totals go under them. */
data class RowPage(val from: Int, val to: Int, val withFooter: Boolean) {
    val rowCount: Int get() = to - from
}

/**
 * Splits a table's rows over pages. [rowHeights] is the height of each row. Every page that carries rows
 * also carries the table's header row ([headerHeight], repeated). [firstAvailable] is the space left on
 * the page where the table starts, [nextAvailable] the space of a full page.
 *
 *  - a row that does not fit goes to the next page; a row taller than a whole page is placed anyway;
 *  - [footerHeight] (the totals, 0 for none) goes under the LAST row; if it does not fit, the last row
 *    moves to the next page together with it, so the totals are never alone on a page when a row can go with them;
 *  - the first page may carry no row at all (from == to): the table then starts on the next page.
 *
 * The result always covers every row exactly once, in order.
 */
fun paginateRows(
    rowHeights: List<Float>,
    headerHeight: Float,
    footerHeight: Float,
    firstAvailable: Float,
    nextAvailable: Float
): List<RowPage> {
    if (rowHeights.isEmpty()) return listOf(RowPage(0, 0, footerHeight > 0f))
    val pages = mutableListOf<RowPage>()
    var from = 0
    var used = headerHeight
    var available = firstAvailable
    for (i in rowHeights.indices) {
        val h = rowHeights[i]
        val pageHasRows = i > from
        if (used + h > available && (pageHasRows || available < nextAvailable)) {
            // Close this page. On the first page with no row yet, from == i: an empty first page, the table starts next page.
            pages += RowPage(from, i, withFooter = false)
            from = i
            used = headerHeight
            available = nextAvailable
        }
        used += h
    }
    // The last page: do the totals fit under its rows?
    if (footerHeight > 0f && used + footerHeight > available) {
        val rowsOnLast = rowHeights.size - from
        if (rowsOnLast >= 2) {
            pages += RowPage(from, rowHeights.size - 1, withFooter = false)
            pages += RowPage(rowHeights.size - 1, rowHeights.size, withFooter = true)
        } else {
            pages += RowPage(from, rowHeights.size, withFooter = false)
            pages += RowPage(rowHeights.size, rowHeights.size, withFooter = true) // the totals alone (one tall row)
        }
    } else {
        pages += RowPage(from, rowHeights.size, withFooter = footerHeight > 0f)
    }
    return pages
}
