package com.example.dukanhisabnext.sale

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-scan guard (version 5): a sale belongs to the period of its saleDate, never of the moment
 * it was saved. Every SQL that cuts sales by time must use the shared SALE_PERIOD_SQL and sort by
 * s.saleDate; none may filter or sort sales by createdAt. The Kotlin code that groups sales into
 * days, and the receipt, must use saleDate too.
 */
class SalePeriodGuardTest {

    private val main = File("src/main/java/com/example/dukanhisabnext")
    private fun text(path: String) = File(main, path).readText()

    /** The text of every Room @Query of a DAO file. */
    private fun queries(file: File): List<String> =
        Regex("@Query\\(\\s*(\"\"\"[\\s\\S]*?\"\"\"|\"(?:[^\"\\\\]|\\\\.)*\")").findAll(file.readText())
            .map { it.groupValues[1] }.toList()

    @Test
    fun thePeriodConditionIsTheSaleDate() {
        assertTrue(text("data/dao/SaleDao.kt").contains("internal const val SALE_PERIOD_SQL = \"s.saleDate >= :start AND s.saleDate < :end\""))
    }

    @Test
    fun noSalesQueryFiltersOrSortsByCreatedAt() {
        val daoFiles = File(main, "data/dao").listFiles()!!.filter { it.extension == "kt" }
        var salesPeriodQueries = 0
        for (file in daoFiles) {
            for (q in queries(file)) {
                if (!Regex("\\b(FROM|JOIN)\\s+sales\\b").containsMatchIn(q)) continue
                // A sale saved at a moment (crash recovery) may be looked up by createdAt with "=",
                // but no sales query may cut a period or sort by it.
                assertFalse("${file.name}: sales filtered by createdAt\n$q", Regex("createdAt\\s*(<|>|<=|>=)\\s*:").containsMatchIn(q))
                assertFalse("${file.name}: sales sorted by createdAt\n$q", Regex("ORDER BY\\s+(s\\.)?createdAt").containsMatchIn(q))
                // A khata query joins sales only for the receipt number; its period is the ledger
                // entry's own date (l.entryDate), which already follows the sale date for SALE_DUE.
                val ledgerPeriod = q.contains("l.entryDate >= :start")
                // A sales period is written ONLY through the shared constant: a hand-written
                // ":start" condition on a sales query is refused.
                if (!ledgerPeriod) {
                    assertFalse("${file.name}: a sales period must use SALE_PERIOD_SQL\n$q", q.contains(":start") || q.contains(":end"))
                }
                if (q.contains("\$SALE_PERIOD_SQL")) salesPeriodQueries++
            }
        }
        // Sales list, its page, its summary, the totals (Home and reports), the sales in a range, the
        // sales report and the cost of goods. If this number changes, a period query was added or
        // removed: check it uses SALE_PERIOD_SQL, then update the number.
        assertEquals(7, salesPeriodQueries)
    }

    @Test
    fun theSalesListAndReportsGroupAndShowBySaleDate() {
        val logic = text("ui/sales/SalesListLogic.kt")
        assertTrue(logic.contains("compareByDescending<SaleListRow> { it.saleDate }"))
        assertTrue(logic.contains(".groupBy { DhakaTime.localDate(it.saleDate) }"))
        assertTrue(text("ui/sales/SalesListScreen.kt").contains("saleTimeText(item.row.saleDate)"))
        assertTrue(text("ui/report/ReportScreen.kt").contains("saleTimeText(row.saleDate)"))
        assertFalse(text("ui/report/ReportScreen.kt").contains("saleTimeText(row.createdAt)"))
    }

    @Test
    fun theReceiptShowsTheSaleDate() {
        // Both the screen and the shared text show the date through receiptSaleDate, which reads saleDate.
        val receiptText = text("receipt/ReceiptText.kt")
        assertTrue(receiptText.contains("fun receiptSaleDate"))
        assertTrue(receiptText.contains("receiptDateTime(data.saleDate, labels)"))
        assertTrue(receiptText.contains("labels.date}: \${receiptSaleDate(data, labels)}"))
        assertTrue(text("ui/receipt/ReceiptScreen.kt").contains("receiptSaleDate(data, labels)"))
        assertFalse(text("ui/receipt/ReceiptScreen.kt").contains("receiptDateTime(data.createdAt"))
    }

    @Test
    fun theSaveDatesEverythingOfTheSaleBySaleDate() {
        val repo = text("data/SaleRepository.kt").replace("\r\n", "\n")
        assertTrue(repo.contains("val receiptNo = nextFreeReceiptNo(saleDate)"))
        assertTrue(repo.contains("createdAt = saleDate // a SALE movement is dated by the sale day"))
        assertTrue(repo.contains("entryDate = saleDate,"))
        // The sale's createdAt stays the real moment: the crash recovery finds the sale by it.
        assertTrue(repo.contains("createdAt = now,\n                saleDate = saleDate"))
    }
}
