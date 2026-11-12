package com.example.dukanhisabnext.ui.sales

import com.example.dukanhisabnext.data.dao.SaleListRow
import com.example.dukanhisabnext.data.dao.SalesListSummaryRow
import com.example.dukanhisabnext.data.dao.SalesSearchParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The capped sales list: the SQL search parameters and the page builder ("আরও দেখান"). */
class SalesPageLogicTest {

    // ---- the search parameters (the SQL twin of matchesSalesQuery) ----

    @Test
    fun aBlankSearchSearchesNothing() {
        val none = SalesSearchParams("", "", "", "")
        assertEquals(none, salesSearchParams(""))
        assertEquals(none, salesSearchParams("   "))
    }

    @Test
    fun bengaliDigitsBecomeNormalDigits() {
        val p = salesSearchParams("০১৭১১")
        assertEquals("01711", p.query)
        assertEquals("01711", p.digits)
        assertEquals("", p.digitsWithout88)
    }

    @Test
    fun aReceiptNumberIsSearchedWithoutItsDash() {
        val p = salesSearchParams(" 2611-10 ")
        assertEquals("2611-10", p.query)
        assertEquals("261110", p.compactQuery)
        assertEquals("261110", p.digits)
    }

    @Test
    fun aLeading88FromPlus88IsAlsoTriedWithout() {
        assertEquals("01711", salesSearchParams("+8801711").digitsWithout88)
        assertEquals("8801711", salesSearchParams("+8801711").digits)
        assertEquals("", salesSearchParams("88").digitsWithout88) // nothing left after 88
        assertEquals("", salesSearchParams("01788").digitsWithout88) // 88 not at the start
    }

    @Test
    fun likeWildcardsAreSearchedLiterally() {
        assertEquals("50\\%\\_off\\\\", salesSearchParams("50%_off\\").query)
        assertEquals("a\\_b", likeEscape("a_b"))
        assertEquals("plain", likeEscape("plain"))
    }

    @Test
    fun aDashAloneIsNoReceiptSearch() {
        val p = salesSearchParams("-")
        assertEquals("-", p.query)
        assertEquals("", p.compactQuery)
        assertEquals("", p.digits)
    }

    // ---- the page ----

    private fun row(id: Long, createdAt: Long = 1_762_927_740_000 - id * 60_000, total: Long = 1_000, cancelled: Boolean = false) =
        SaleListRow(id, "261112-${id.toString().padStart(3, '0')}", createdAt, total, 0, null, null, if (cancelled) createdAt + 1 else null)

    @Test
    fun theSummaryComesFromTheWholeWindowNeverFromTheRowsShown() {
        val shown = (1L..3L).map { row(it) }
        val whole = SalesSummary(count = 650, total = 9_999_999, cancelledExcluded = 50)
        val ui = buildSalesPageUi(shown, whole, totalSalesInDb = 800)
        assertEquals(whole, ui.summary) // not the 3 rows' count or total
        assertTrue(ui.hasMore)
        assertNull(ui.empty)
        assertEquals(3, ui.items.count { it is SalesListItem.Entry })
    }

    @Test
    fun noMoreButtonWhenEverySaleIsShown() {
        val rows = (1L..3L).map { row(it, cancelled = it == 2L) }
        val ui = buildSalesPageUi(rows, SalesSummary(count = 2, total = 2_000, cancelledExcluded = 1), totalSalesInDb = 3)
        assertFalse(ui.hasMore)
    }

    @Test
    fun theEmptyMessagesUseTheWholeWindow() {
        assertEquals(EmptyKind.NO_SALES_AT_ALL, buildSalesPageUi(emptyList(), SalesSummary(0, 0, 0), totalSalesInDb = 0).empty)
        assertEquals(EmptyKind.NO_MATCH, buildSalesPageUi(emptyList(), SalesSummary(0, 0, 0), totalSalesInDb = 40).empty)
        // Only cancelled sales match: the list is not empty (they are shown, tagged).
        assertNull(buildSalesPageUi(listOf(row(1, cancelled = true)), SalesSummary(0, 0, 1), totalSalesInDb = 1).empty)
    }

    @Test
    fun theDatabaseSummaryRowBecomesTheScreenSummary() {
        assertEquals(SalesSummary(5, 12_345, 2), SalesListSummaryRow(5, 12_345, 2).toSalesSummary())
    }

    @Test
    fun aPageIsAFewHundredRows() {
        assertTrue(SALES_PAGE_SIZE in 200..500)
    }
}
