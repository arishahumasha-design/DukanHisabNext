package com.example.dukanhisabnext.pdf

import com.example.dukanhisabnext.data.dao.KhataCustomerRow
import com.example.dukanhisabnext.data.dao.LedgerHistoryRow
import com.example.dukanhisabnext.data.dao.ProductWithStock
import com.example.dukanhisabnext.data.dao.SaleListRow
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.expense.CategoryTotal
import com.example.dukanhisabnext.expense.ExpenseSummary
import com.example.dukanhisabnext.ledger.KhataAllSummary
import com.example.dukanhisabnext.ledger.PeriodSummary
import com.example.dukanhisabnext.report.ProfitReport
import com.example.dukanhisabnext.report.SalesReport
import com.example.dukanhisabnext.ui.report.ReportBody
import com.example.dukanhisabnext.ui.report.ReportTab
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What goes into each of the four documents (no Android, no font): the rows, the totals, the running balance. */
class PdfBuildersTest {

    private val header = PdfHeader("Rahim Store", "Dhaka", "017")
    private val fmt = PdfFormat(money = { "T ${it / 100}" }, date = { "d$it" }, number = { it.toString() }, quantity = { (it / 1000).toString() }, taka = "T")
    private val labels = PdfLabels(
        shopPlaceholder = "Shop", mobileLabel = "Mobile", generatedOn = { "Generated: $it" }, periodLine = { "Period: $it" },
        serial = "No", name = "Name", phone = "Phone",
        dueListTitle = "Due list", due = "Due", totalDue = "Total due", totalAdvance = "Total advance",
        productListTitle = "Products", category = "Category", unit = "Unit", sellPrice = "Sell", costPrice = "Cost", stock = "Stock",
        totalProducts = "Total products", unitName = { it.name.lowercase() },
        reportTitle = { "Report ${it.name}" },
        salesLine = { it.name }, countValue = { "$it pcs" }, cancelledNote = { "cancelled $it" },
        salesListTitle = "Sales", receiptNo = "Receipt", date = "Date", customer = "Customer", total = "Total", walkIn = "Walk-in",
        dueNowTitle = "Due now", receivable = "Receivable", advance = "Advance", net = "Net",
        owingTitle = "Owing", periodTransactionsTitle = "Period",
        summaryLine = { it.name }, dueBreakdown = { a, b -> "sales $a hand $b" },
        expenseShop = "Shop exp", expensePersonal = "Personal exp", expenseTotal = "Exp total",
        expenseByCategory = "By category", categoryHeader = "Category", count = "Count", personalMark = "personal",
        expenseCount = { "$it x" },
        profitLine = { kind, loss -> "${kind.name}${if (loss) "-loss" else ""}" },
        deliveryInfo = { "delivery $it" }, personalInfo = { "personal $it" },
        approximate = { lines, sales -> "approx $lines $sales" },
        statementTitle = "Statement", customerLine = { "Customer: $it" }, description = "Description", amount = "Amount",
        balance = "Balance", currentDue = "Current due", currentAdvance = "Current advance", currentClear = "Clear"
    )

    private fun customer(id: Long, name: String, phone: String? = "017$id") = Customer(id = id, name = name, phone = phone, createdAt = 1, updatedAt = 1)
    private fun khata(id: Long, name: String, balance: Long) = KhataCustomerRow(customer(id, name), balance, null)

    private fun ready(build: PdfBuild): PdfContent = (build as PdfBuild.Ready).content
    private fun table(c: PdfContent, index: Int = 0) = c.blocks.filterIsInstance<PdfBlock.Table>()[index]

    // =====================================================================================
    // Due list
    // =====================================================================================

    @Test
    fun dueListHasOnlyCustomersWhoOweBiggestFirstWithSerialNumbers() {
        val all = listOf(khata(1, "Karim", 5_000), khata(2, "Rahim", 90_000), khata(3, "Clear", 0), khata(4, "Adv", -2_000), khata(5, "Salma", 30_000))
        val c = ready(buildDueListPdf(header, all, labels, fmt, "13 Nov 2026"))
        val rows = table(c).rows
        assertEquals(listOf("Rahim", "Salma", "Karim"), rows.map { it.cells[1] })
        assertEquals(listOf("1", "2", "3"), rows.map { it.cells[0] })
        assertEquals(listOf("T 900", "T 300", "T 50"), rows.map { it.cells[3] })
        assertEquals("0171", rows.last().cells[2].take(4))
    }

    @Test
    fun dueListTotalIsTheSumOfTheDuesAndAdvanceHasItsOwnLine() {
        val all = listOf(khata(1, "A", 10_000), khata(2, "B", 20_000), khata(3, "C", -4_000))
        val t = table(ready(buildDueListPdf(header, all, labels, fmt, "x")))
        assertEquals(2, t.footer.size)
        assertEquals("Total due", t.footer[0].cells[1])
        assertEquals("T 300", t.footer[0].cells[3])
        assertTrue(t.footer.all { it.bold })
        assertEquals("Total advance", t.footer[1].cells[1])
        assertEquals("T 40", t.footer[1].cells[3])
    }

    @Test
    fun dueListHasNoAdvanceLineWhenNobodyHasAnAdvance() {
        val t = table(ready(buildDueListPdf(header, listOf(khata(1, "A", 10_000)), labels, fmt, "x")))
        assertEquals(1, t.footer.size)
    }

    @Test
    fun dueListShowsTheDateAndNoPeriod() {
        val c = ready(buildDueListPdf(header, listOf(khata(1, "A", 100)), labels, fmt, "13 Nov 2026"))
        assertEquals(listOf("Generated: 13 Nov 2026"), c.subtitleLines)
        assertEquals("Due list", c.title)
        assertEquals("Mobile: 017", c.shopPhone)
        assertEquals("Rahim Store", c.shopName)
    }

    @Test
    fun dueListWithNobodyOwingIsEmptyNotABlankPdf() {
        val none = buildDueListPdf(header, listOf(khata(1, "A", 0), khata(2, "B", -5)), labels, fmt, "x")
        assertEquals(PdfBuild.Empty(EmptyReason.NO_DUE), none)
        assertEquals(PdfBuild.Empty(EmptyReason.NO_DUE), buildDueListPdf(header, emptyList(), labels, fmt, "x"))
    }

    @Test
    fun aCustomerWithoutAPhoneGetsAnEmptyCell() {
        val c = ready(buildDueListPdf(header, listOf(KhataCustomerRow(customer(1, "A", phone = null), 100, null)), labels, fmt, "x"))
        assertEquals("", table(c).rows[0].cells[2])
    }

    @Test
    fun dueListOfAThousandCustomersLaysOutOverManyPages() {
        val all = (1..1200L).map { khata(it, "Customer $it", it * 100) }
        val c = ready(buildDueListPdf(header, all, labels, fmt, "x"))
        assertEquals(1200, table(c).rows.size)
        val measure = TextMeasure { t, s, b -> t.length * s * (if (b) 0.55f else 0.5f) }
        assertTrue(layoutDocument(c, measure) { p, n -> "$p/$n" }.count > 20)
    }

    // =====================================================================================
    // Product list
    // =====================================================================================

    private fun product(id: Long, name: String, cost: Long? = 5_000, active: Boolean = true, category: Long? = 1) = ProductWithStock(
        Product(id = id, name = name, categoryId = category, unit = ProductUnit.KG, sellPrice = 8_000, costPrice = cost, isActive = active, createdAt = 1, updatedAt = 1),
        stock = 2_500
    )

    @Test
    fun productListLeavesOutArchivedProducts() {
        val items = listOf(product(1, "Rice"), product(2, "Old", active = false), product(3, "Oil"))
        val t = table(ready(buildProductListPdf(header, items, mapOf(1L to "Grocery"), false, labels, fmt, "x")))
        assertEquals(listOf("Rice", "Oil"), t.rows.map { it.cells[1] })
    }

    @Test
    fun productListColumnsWithoutTheCostPrice() {
        val t = table(ready(buildProductListPdf(header, listOf(product(1, "Rice")), mapOf(1L to "Grocery"), false, labels, fmt, "x")))
        assertEquals(listOf("No", "Name", "Category", "Unit", "Sell", "Stock"), t.columns.map { it.header })
        assertEquals(listOf("1", "Rice", "Grocery", "kg", "T 80", "2"), t.rows[0].cells)
    }

    @Test
    fun productListWithTheCostPriceHasOneMoreColumn() {
        val items = listOf(product(1, "Rice"), product(2, "Unknown cost", cost = null))
        val t = table(ready(buildProductListPdf(header, items, mapOf(1L to "Grocery"), true, labels, fmt, "x")))
        assertEquals(listOf("No", "Name", "Category", "Unit", "Sell", "Cost", "Stock"), t.columns.map { it.header })
        assertEquals("T 50", t.rows[0].cells[5])
        assertEquals("", t.rows[1].cells[5])
        assertEquals(t.columns.size, t.rows[0].cells.size)
    }

    @Test
    fun productListWithoutCostNeverMentionsTheCost() {
        val c = ready(buildProductListPdf(header, listOf(product(1, "Rice")), emptyMap(), false, labels, fmt, "x"))
        val all = c.blocks.filterIsInstance<PdfBlock.Table>().flatMap { t -> t.columns.map { it.header } + t.rows.flatMap { it.cells } }
        assertFalse(all.any { it == "Cost" || it == "T 50" })
    }

    @Test
    fun productListEndsWithTheNumberOfProducts() {
        val c = ready(buildProductListPdf(header, listOf(product(1, "A"), product(2, "B"), product(3, "C", active = false)), emptyMap(), false, labels, fmt, "x"))
        val line = (c.blocks.last() as PdfBlock.Lines).lines.single()
        assertEquals("Total products", line.label)
        assertEquals("2", line.value)
    }

    @Test
    fun productListWithoutActiveProductsIsEmpty() {
        assertEquals(PdfBuild.Empty(EmptyReason.NO_PRODUCTS), buildProductListPdf(header, listOf(product(1, "Old", active = false)), emptyMap(), false, labels, fmt, "x"))
        assertEquals(PdfBuild.Empty(EmptyReason.NO_PRODUCTS), buildProductListPdf(header, emptyList(), emptyMap(), false, labels, fmt, "x"))
    }

    // =====================================================================================
    // Reports
    // =====================================================================================

    private val salesReport = SalesReport(count = 3, subtotal = 100_000, discount = 10_000, deliveryCharge = 5_000, total = 95_000, paid = 60_000, due = 35_000, cancelledCount = 2)

    private fun saleRow(id: Long, name: String?, total: Long, due: Long) = SaleListRow(
        id = id, receiptNo = "R$id", createdAt = 1, total = total, dueAmount = due, customerName = name, customerPhone = null
    )

    @Test
    fun salesReportHasThePeriodTheSameLinesAndTheCancelledNote() {
        val body = ReportBody.Sales(salesReport, listOf(saleRow(1, "Karim", 50_000, 0), saleRow(2, null, 45_000, 35_000)))
        val c = ready(buildReportPdf(header, ReportTab.SALES, "This month", body, labels, fmt, "x"))
        assertEquals("Report SALES", c.title)
        assertEquals("Period: This month", c.subtitleLines.first())
        val lines = (c.blocks.first() as PdfBlock.Lines).lines
        assertEquals(listOf("COUNT", "NET_SALES", "DELIVERY", "TOTAL", "PAID", "DUE"), lines.take(6).map { it.label })
        assertEquals("3 pcs", lines[0].value)
        assertEquals("T 900", lines[1].value)
        assertEquals("cancelled 2", lines.last().label)
        assertTrue(lines.last().small)
        val sales = table(c)
        assertEquals(listOf("Karim", "Walk-in"), sales.rows.map { it.cells[2] })
        assertEquals("", sales.rows[0].cells[4])
        assertEquals("T 350", sales.rows[1].cells[4])
        assertEquals("T 950", sales.footer.single().cells[3])
    }

    @Test
    fun salesReportWithNoSalesAtAllIsEmpty() {
        val none = SalesReport(0, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(PdfBuild.Empty(EmptyReason.NO_REPORT_DATA), buildReportPdf(header, ReportTab.SALES, "p", ReportBody.Sales(none, emptyList()), labels, fmt, "x"))
    }

    @Test
    fun salesReportThatOnlyHasCancelledSalesStillShowsTheNote() {
        val onlyCancelled = SalesReport(0, 0, 0, 0, 0, 0, 0, 3)
        val c = ready(buildReportPdf(header, ReportTab.SALES, "p", ReportBody.Sales(onlyCancelled, emptyList()), labels, fmt, "x"))
        assertTrue((c.blocks.first() as PdfBlock.Lines).lines.any { it.label == "cancelled 3" })
    }

    @Test
    fun pickDatesGivesNoPdf() {
        assertEquals(PdfBuild.Empty(EmptyReason.PICK_DATES), buildReportPdf(header, ReportTab.SALES, "", ReportBody.PickDates, labels, fmt, "x"))
    }

    @Test
    fun dueReportShowsTheShopTotalsTheOwingCustomersAndThePeriod() {
        val owing = listOf(khata(1, "Rahim", 90_000), khata(2, "Karim", 5_000))
        val period = PeriodSummary(dueOnSales = 30_000, dueByHand = 10_000, openingDue = 0, received = 20_000, advanceReturned = 0, correctionNet = 0, saleCancelled = 0, net = 20_000)
        val body = ReportBody.Due(KhataAllSummary(95_000, 3_000), owing, period)
        val c = ready(buildReportPdf(header, ReportTab.DUE, "Today", body, labels, fmt, "x"))
        val now = c.blocks.filterIsInstance<PdfBlock.Lines>().first().lines
        assertEquals(listOf("T 950", "T 30", "T 920"), now.map { it.value })
        val owingTable = table(c)
        assertEquals(listOf("Rahim", "Karim"), owingTable.rows.map { it.cells[1] })
        assertEquals("T 950", owingTable.footer.single().cells[3])
        val periodLines = c.blocks.filterIsInstance<PdfBlock.Lines>().last().lines
        assertTrue(periodLines.any { it.label == "DUE_GIVEN" && it.value.contains("400") })
        assertTrue(periodLines.any { it.label == "sales T 300 hand T 100" && it.small })
    }

    @Test
    fun dueReportWithoutARangeLeavesThePeriodBoxOutAndHasNoPeriodLine() {
        val body = ReportBody.Due(KhataAllSummary(5_000, 0), listOf(khata(1, "Karim", 5_000)), null)
        val c = ready(buildReportPdf(header, ReportTab.DUE, "", body, labels, fmt, "x"))
        assertEquals(listOf("Generated: x"), c.subtitleLines)
        assertFalse(c.blocks.any { it is PdfBlock.Heading && it.text == "Period" })
    }

    @Test
    fun expenseReportHasTheSplitTheCategoriesAndTheTotals() {
        val summary = ExpenseSummary(shop = 70_000, personal = 30_000, byCategory = listOf(
            CategoryTotal(1, "Rent", false, 60_000, 1), CategoryTotal(2, "Family", true, 30_000, 2), CategoryTotal(3, "Tea", false, 10_000, 3)
        ))
        val c = ready(buildReportPdf(header, ReportTab.EXPENSES, "Today", ReportBody.Expenses(summary, 6), labels, fmt, "x"))
        val lines = (c.blocks.first() as PdfBlock.Lines).lines
        assertEquals(listOf("T 700", "T 300", "T 1000"), lines.map { it.value })
        val t = table(c)
        assertEquals(listOf("Rent", "Family (personal)", "Tea"), t.rows.map { it.cells[0] })
        assertEquals("6 x", t.footer.single().cells[1])
        assertEquals("T 1000", t.footer.single().cells[2])
    }

    @Test
    fun expenseReportWithNoExpensesIsEmpty() {
        assertEquals(
            PdfBuild.Empty(EmptyReason.NO_REPORT_DATA),
            buildReportPdf(header, ReportTab.EXPENSES, "p", ReportBody.Expenses(ExpenseSummary(0, 0, emptyList()), 0), labels, fmt, "x")
        )
    }

    @Test
    fun profitReportHasTheSameLinesAsTheScreenAndTheApproximateNote() {
        val p = ProfitReport(netSales = 100_000, costOfGoods = 60_000, shopExpenses = 10_000, deliveryCharge = 5_000, personalExpenses = 2_000, missingCostLines = 2, missingCostSales = 8_000)
        val c = ready(buildReportPdf(header, ReportTab.PROFIT, "Month", ReportBody.Profit(p), labels, fmt, "x"))
        val lines = (c.blocks.first() as PdfBlock.Lines).lines
        assertEquals(listOf("NET_SALES", "COST_OF_GOODS", "GROSS_PROFIT", "SHOP_EXPENSES", "NET_PROFIT"), lines.map { it.label })
        assertEquals(listOf(false, false, true, false, true), lines.map { it.bold })
        assertTrue(lines[1].value.startsWith("−")) // the cost is a minus line
        assertTrue(c.blocks.any { it is PdfBlock.Note && it.text == "approx 2 T 80" })
        val info = c.blocks.filterIsInstance<PdfBlock.Lines>().last().lines.map { it.label }
        assertEquals(listOf("delivery T 50", "personal T 20"), info)
    }

    @Test
    fun profitReportOfALossSaysLoss() {
        val p = ProfitReport(netSales = 10_000, costOfGoods = 6_000, shopExpenses = 9_000, deliveryCharge = 0, personalExpenses = 0, missingCostLines = 0, missingCostSales = 0)
        val c = ready(buildReportPdf(header, ReportTab.PROFIT, "p", ReportBody.Profit(p), labels, fmt, "x"))
        val lines = (c.blocks.first() as PdfBlock.Lines).lines
        assertEquals("NET_PROFIT-loss", lines.last().label)
        assertFalse(c.blocks.any { it is PdfBlock.Note })
    }

    @Test
    fun profitReportWithNothingInThePeriodIsEmpty() {
        val p = ProfitReport(0, 0, 0, 0, 0, 0, 0)
        assertEquals(PdfBuild.Empty(EmptyReason.NO_REPORT_DATA), buildReportPdf(header, ReportTab.PROFIT, "p", ReportBody.Profit(p), labels, fmt, "x"))
    }

    // =====================================================================================
    // Customer statement
    // =====================================================================================

    private fun entry(id: Long, type: LedgerEntryType, amount: Long, date: Long, created: Long = date, note: String? = null) =
        LedgerEntry(id = id, customerId = 7, type = type, amount = amount, note = note, entryDate = date, createdAt = created)

    private fun row(e: LedgerEntry) = LedgerHistoryRow(e, receiptNo = null, saleCancelled = false)
    private val describe: (LedgerHistoryRow) -> String = { it.entry.type.name }

    @Test
    fun statementIsInDateOrderWithTheRunningBalance() {
        // Given newest first (the screen's order): the PDF puts them oldest first.
        val rows = listOf(
            row(entry(3, LedgerEntryType.PAYMENT_RECEIVED, 4_000, date = 300)),
            row(entry(2, LedgerEntryType.MANUAL_DUE, 2_000, date = 200)),
            row(entry(1, LedgerEntryType.OPENING_DUE, 10_000, date = 100))
        )
        val lines = statementLines(rows, describe)
        assertEquals(listOf(1L, 2L, 3L), lines.map { it.entry.id })
        assertEquals(listOf(10_000L, 2_000L, -4_000L), lines.map { it.signed })
        assertEquals(listOf(10_000L, 12_000L, 8_000L), lines.map { it.runningBalance })
    }

    @Test
    fun entriesOnTheSameDayKeepTheOrderTheyWereWritten() {
        val rows = listOf(
            row(entry(2, LedgerEntryType.PAYMENT_RECEIVED, 500, date = 100, created = 20)),
            row(entry(1, LedgerEntryType.MANUAL_DUE, 500, date = 100, created = 10))
        )
        assertEquals(listOf(1L, 2L), statementLines(rows, describe).map { it.entry.id })
    }

    @Test
    fun theLastRunningBalanceEqualsTheCustomersBalance() {
        val entries = listOf(
            entry(1, LedgerEntryType.OPENING_DUE, 10_000, 1), entry(2, LedgerEntryType.SALE_DUE, 7_000, 2),
            entry(3, LedgerEntryType.PAYMENT_RECEIVED, 20_000, 3), entry(4, LedgerEntryType.CORRECTION_MINUS, 500, 4),
            entry(5, LedgerEntryType.ADVANCE_RETURNED, 2_000, 5)
        )
        val lines = statementLines(entries.map(::row), describe)
        assertEquals(com.example.dukanhisabnext.ledger.balanceOf(entries), lines.last().runningBalance)
    }

    @Test
    fun statementShowsNameMobileSignedAmountsRunningBalanceAndTheCurrentBalance() {
        val rows = listOf(row(entry(2, LedgerEntryType.PAYMENT_RECEIVED, 4_000, 200)), row(entry(1, LedgerEntryType.OPENING_DUE, 10_000, 100)))
        val c = ready(buildStatementPdf(header, customer(7, "Karim", "01711"), rows, 6_000, describe, labels, fmt, "13 Nov 2026"))
        assertEquals("Statement", c.title)
        assertEquals(listOf("Customer: Karim", "Mobile: 01711", "Generated: 13 Nov 2026"), c.subtitleLines)
        val t = table(c)
        assertEquals(listOf("OPENING_DUE", "PAYMENT_RECEIVED"), t.rows.map { it.cells[1] })
        assertEquals(listOf("+ T 100", "− T 40"), t.rows.map { it.cells[2] })
        assertEquals(listOf("T 100", "T 60"), t.rows.map { it.cells[3] })
        val footer = t.footer.single()
        assertTrue(footer.bold)
        assertEquals("Current due", footer.cells[1])
        assertEquals("T 60", footer.cells[3])
    }

    @Test
    fun statementOfAnAdvanceAndOfAClearBalance() {
        val rows = listOf(row(entry(1, LedgerEntryType.PAYMENT_RECEIVED, 4_000, 100)))
        val adv = table(ready(buildStatementPdf(header, customer(7, "K"), rows, -4_000, describe, labels, fmt, "x"))).footer.single()
        assertEquals("Current advance", adv.cells[1])
        assertEquals("T 40", adv.cells[3])
        assertEquals("− T 40", table(ready(buildStatementPdf(header, customer(7, "K"), rows, -4_000, describe, labels, fmt, "x"))).rows[0].cells[3])
        val clear = table(ready(buildStatementPdf(header, customer(7, "K"), rows, 0, describe, labels, fmt, "x"))).footer.single()
        assertEquals("Clear", clear.cells[1])
        assertEquals("", clear.cells[3])
    }

    @Test
    fun balanceColumnTextForDueAdvanceAndZero() {
        assertEquals("T 5", balanceColumnText(500, fmt))
        assertEquals("− T 5", balanceColumnText(-500, fmt))
        assertEquals("0", balanceColumnText(0, fmt))
    }

    @Test
    fun statementWithoutEntriesIsEmpty() {
        assertEquals(PdfBuild.Empty(EmptyReason.NO_ENTRIES), buildStatementPdf(header, customer(7, "K"), emptyList(), 0, describe, labels, fmt, "x"))
    }

    @Test
    fun statementDescriptionJoinsTheTitleAndTheNote() {
        val withNote = row(entry(1, LedgerEntryType.MANUAL_DUE, 100, 1, note = "for rice"))
        val without = row(entry(2, LedgerEntryType.MANUAL_DUE, 100, 2))
        assertEquals("Gave due · for rice", statementDescription(withNote, "Gave due"))
        assertEquals("Gave due", statementDescription(without, "Gave due"))
        // A correction already carries its note in the title: no second copy.
        val correction = row(entry(3, LedgerEntryType.CORRECTION_PLUS, 100, 3, note = "typo"))
        assertEquals("Correction · typo", statementDescription(correction, "Correction · typo"))
    }

    @Test
    fun aStatementOfAThousandEntriesLaysOutAndEndsWithTheCurrentBalance() {
        val rows = (1..1000L).map { row(entry(it, LedgerEntryType.MANUAL_DUE, 100, date = it)) }
        val c = ready(buildStatementPdf(header, customer(7, "K"), rows, 100_000, describe, labels, fmt, "x"))
        val measure = TextMeasure { t, s, b -> t.length * s * (if (b) 0.55f else 0.5f) }
        val pages = layoutDocument(c, measure) { p, n -> "$p/$n" }
        assertTrue(pages.count > 15)
        assertTrue(pages.pages.last().filterIsInstance<DrawOp.Text>().any { it.text == "Current due" })
    }

    // =====================================================================================
    // Period text and file names
    // =====================================================================================

    @Test
    fun reportPeriodTextShowsTheChipWordAndTheDays() {
        val day = 24L * 3600 * 1000
        val range = com.example.dukanhisabnext.util.DhakaTime.TimeRange(0, 3 * day)
        assertEquals("Month · d0 – d${3 * day - 1}", reportPeriodText("Month", range, { "d$it" }))
        assertEquals("d0 – d${3 * day - 1}", reportPeriodText(null, range, { "d$it" }))
        assertEquals("Today · same", reportPeriodText("Today", range, { "same" }))
        assertEquals("", reportPeriodText("Month", null, { "d$it" }))
    }

    @Test
    fun fileNamesAreAsciiAndKnowTheirDate() {
        val day = LocalDate.of(2026, 11, 13)
        assertEquals("due-list-2026-11-13.pdf", pdfFileName(PdfKind.DUE_LIST, day))
        assertEquals("products-2026-11-13.pdf", pdfFileName(PdfKind.PRODUCTS, day))
        assertEquals("report-sales-2026-11-13.pdf", pdfFileName(PdfKind.REPORT_SALES, day))
        assertEquals("report-due-2026-11-13.pdf", pdfFileName(PdfKind.REPORT_DUE, day))
        assertEquals("report-expenses-2026-11-13.pdf", pdfFileName(PdfKind.REPORT_EXPENSES, day))
        assertEquals("report-profit-2026-11-13.pdf", pdfFileName(PdfKind.REPORT_PROFIT, day))
        assertEquals("statement-42-2026-11-13.pdf", pdfFileName(PdfKind.STATEMENT, day, customerId = 42))
        for (kind in PdfKind.entries) {
            val name = pdfFileName(kind, day, 5)
            assertTrue(name, name.all { it.code in 0x21..0x7E } && name.endsWith(".pdf"))
        }
    }

    @Test
    fun onlyAStatementCarriesACustomerNumber() {
        assertEquals("due-list-2026-01-05.pdf", pdfFileName(PdfKind.DUE_LIST, LocalDate.of(2026, 1, 5), customerId = 9))
    }
}
