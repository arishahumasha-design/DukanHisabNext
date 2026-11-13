package com.example.dukanhisabnext.pdf

import com.example.dukanhisabnext.data.dao.KhataCustomerRow
import com.example.dukanhisabnext.data.dao.LedgerHistoryRow
import com.example.dukanhisabnext.data.dao.ProductWithStock
import com.example.dukanhisabnext.data.dao.SaleListRow
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.ledger.KhataSort
import com.example.dukanhisabnext.ledger.PeriodSummary
import com.example.dukanhisabnext.ledger.SummaryLine
import com.example.dukanhisabnext.ledger.historyAmountText
import com.example.dukanhisabnext.ledger.historyNote
import com.example.dukanhisabnext.ledger.khataAllSummary
import com.example.dukanhisabnext.ledger.shownSummaryLines
import com.example.dukanhisabnext.ledger.signedAmount
import com.example.dukanhisabnext.ledger.showsDueBreakdown
import com.example.dukanhisabnext.ledger.visibleKhataCustomers
import com.example.dukanhisabnext.report.ProfitReport
import com.example.dukanhisabnext.report.SalesReport
import com.example.dukanhisabnext.report.InfoLineKind
import com.example.dukanhisabnext.ui.report.ResultWord
import com.example.dukanhisabnext.report.approximateNote
import com.example.dukanhisabnext.report.profitInfoLines
import com.example.dukanhisabnext.report.profitLines
import com.example.dukanhisabnext.report.ProfitLineKind
import com.example.dukanhisabnext.ui.customers.BalanceStatus
import com.example.dukanhisabnext.ui.customers.balanceStatus
import com.example.dukanhisabnext.ui.khata.isSignedLine
import com.example.dukanhisabnext.ui.khata.summaryAmount
import com.example.dukanhisabnext.ui.khata.summaryAmountText
import com.example.dukanhisabnext.ui.report.ReportBody
import com.example.dukanhisabnext.ui.report.ReportTab
import com.example.dukanhisabnext.ui.report.SalesLine
import com.example.dukanhisabnext.ui.report.isBoldProfitLine
import com.example.dukanhisabnext.ui.report.isBoldSalesLine
import com.example.dukanhisabnext.ui.report.isMoneyLine
import com.example.dukanhisabnext.ui.report.netResultWord
import com.example.dukanhisabnext.ui.report.profitAmountText
import com.example.dukanhisabnext.ui.report.salesLineAmount
import com.example.dukanhisabnext.ui.report.showsCancelledNote
import kotlin.math.abs

// The four documents as pure functions: data in, a PdfContent (or "nothing to export") out. They reuse
// the rules of the screens (which customers owe, the report lines, the running balance...) and never
// work out money by themselves, except the running balance of a statement, which is the SAME sum as the
// customer's balance (signedAmount of every entry). Every fixed word comes in through [PdfLabels].

/** Why there is nothing to export (the screen shows a short message instead of an empty PDF). */
enum class EmptyReason { NO_DUE, NO_PRODUCTS, NO_REPORT_DATA, PICK_DATES, NO_ENTRIES }

sealed interface PdfBuild {
    data class Ready(val content: PdfContent) : PdfBuild
    data class Empty(val reason: EmptyReason) : PdfBuild
}

/** The shop on top of every page. */
data class PdfHeader(val shopName: String, val address: String?, val phone: String?)

/** How numbers and dates are written (Bengali digits; the tests pass simple ones). */
class PdfFormat(
    /** "৳ ১,২০০" for 120000 paisa. */
    val money: (Long) -> String,
    /** "১৩/১১/২০২৬" for a moment. */
    val date: (Long) -> String,
    /** A whole number in Bengali digits. */
    val number: (Long) -> String,
    /** A quantity in thousandths ("২.৫"). */
    val quantity: (Long) -> String,
    /** The taka sign. */
    val taka: String
)

/** Every fixed word of the four documents (from strings.xml; the tests pass their own). */
data class PdfLabels(
    val shopPlaceholder: String,
    val mobileLabel: String,                 // "মোবাইল"
    val generatedOn: (String) -> String,     // "তৈরির তারিখ: ১৩ নভেম্বর ২০২৬"
    val periodLine: (String) -> String,      // "সময়: …"
    // common columns
    val serial: String, val name: String, val phone: String,
    // due list
    val dueListTitle: String, val due: String, val totalDue: String, val totalAdvance: String,
    // products
    val productListTitle: String, val category: String, val unit: String, val sellPrice: String,
    val costPrice: String, val stock: String, val totalProducts: String, val unitName: (ProductUnit) -> String,
    // reports
    val reportTitle: (ReportTab) -> String,
    val salesLine: (SalesLine) -> String, val countValue: (Long) -> String, val cancelledNote: (Long) -> String,
    val salesListTitle: String, val receiptNo: String, val date: String, val customer: String, val total: String,
    val walkIn: String,
    val dueNowTitle: String, val receivable: String, val advance: String, val net: String,
    val owingTitle: String, val periodTransactionsTitle: String,
    val summaryLine: (SummaryLine) -> String, val dueBreakdown: (String, String) -> String,
    val expenseShop: String, val expensePersonal: String, val expenseTotal: String,
    val expenseByCategory: String, val categoryHeader: String, val count: String, val personalMark: String,
    val expenseCount: (Long) -> String,
    val profitLine: (ProfitLineKind, Boolean) -> String,    // kind, isLoss (for the net / gross lines)
    val deliveryInfo: (String) -> String, val personalInfo: (String) -> String,
    val approximate: (Long, String) -> String,
    // statement
    val statementTitle: String, val customerLine: (String) -> String, val description: String, val amount: String,
    val balance: String, val currentDue: String, val currentAdvance: String, val currentClear: String
)

private fun contentOf(header: PdfHeader, labels: PdfLabels, title: String, subtitle: List<String>, blocks: List<PdfBlock>) = PdfContent(
    shopName = header.shopName.ifBlank { labels.shopPlaceholder },
    shopAddress = header.address,
    shopPhone = header.phone?.takeIf { it.isNotBlank() }?.let { "${labels.mobileLabel}: $it" },
    title = title,
    subtitleLines = subtitle,
    blocks = blocks
)

// =========================================================================================
// 1. The due list (Khata): everyone who owes, biggest first
// =========================================================================================

/**
 * "All customers with a due, as of now": the customers whose balance is above 0, biggest amount first (the
 * same rule as the Khata filter and the report's বাকি tab), then the total due, and one extra line with the total
 * advance when any customer has an advance. No period or search of the screen is applied.
 */
fun buildDueListPdf(header: PdfHeader, allCustomers: List<KhataCustomerRow>, labels: PdfLabels, fmt: PdfFormat, generatedOnText: String): PdfBuild {
    val owing = visibleKhataCustomers(allCustomers, query = "", onlyOwing = true, sort = KhataSort.AMOUNT_HIGH_TO_LOW)
    if (owing.isEmpty()) return PdfBuild.Empty(EmptyReason.NO_DUE)
    val summary = khataAllSummary(allCustomers)
    val columns = listOf(
        PdfColumn(labels.serial, PdfAlign.START, ColumnWidth.Fixed(34f)),
        PdfColumn(labels.name, PdfAlign.START, ColumnWidth.Flex(3f, 80f)),
        PdfColumn(labels.phone, PdfAlign.START, ColumnWidth.Fixed(92f)),
        PdfColumn(labels.due, PdfAlign.END, ColumnWidth.Fixed(92f))
    )
    val rows = owing.mapIndexed { i, r ->
        PdfRow(listOf(fmt.number(i + 1L), r.customer.name, r.customer.phone?.let { toDigits(it, fmt) }.orEmpty(), fmt.money(r.balance)))
    }
    val footer = buildList {
        add(PdfRow(listOf("", labels.totalDue, "", fmt.money(summary.receivable)), bold = true))
        if (summary.advance > 0) add(PdfRow(listOf("", labels.totalAdvance, "", fmt.money(summary.advance)), bold = true))
    }
    return PdfBuild.Ready(
        contentOf(header, labels, labels.dueListTitle, listOf(labels.generatedOn(generatedOnText)), listOf(PdfBlock.Table(columns, rows, footer)))
    )
}

/** A phone number with Bengali digits (digits only are converted; "+" and "-" stay). */
private fun toDigits(text: String, fmt: PdfFormat): String = text.map { c -> if (c in '0'..'9') fmt.number((c - '0').toLong()) else c.toString() }.joinToString("")

// =========================================================================================
// 2. The product list: active products only, optionally with the cost price
// =========================================================================================

/** Archived products are left out. [showCost] adds the cost price column ("ক্রয়মূল্য দেখান"). */
fun buildProductListPdf(
    header: PdfHeader,
    products: List<ProductWithStock>,
    categoryNames: Map<Long, String>,
    showCost: Boolean,
    labels: PdfLabels,
    fmt: PdfFormat,
    generatedOnText: String
): PdfBuild {
    val active = products.filter { it.product.isActive }
    if (active.isEmpty()) return PdfBuild.Empty(EmptyReason.NO_PRODUCTS)
    val columns = buildList {
        add(PdfColumn(labels.serial, PdfAlign.START, ColumnWidth.Fixed(30f)))
        add(PdfColumn(labels.name, PdfAlign.START, ColumnWidth.Flex(3f, 70f)))
        add(PdfColumn(labels.category, PdfAlign.START, ColumnWidth.Flex(1.6f, 50f)))
        add(PdfColumn(labels.unit, PdfAlign.START, ColumnWidth.Fixed(44f)))
        add(PdfColumn(labels.sellPrice, PdfAlign.END, ColumnWidth.Fixed(64f)))
        if (showCost) add(PdfColumn(labels.costPrice, PdfAlign.END, ColumnWidth.Fixed(64f)))
        add(PdfColumn(labels.stock, PdfAlign.END, ColumnWidth.Fixed(52f)))
    }
    val rows = active.mapIndexed { i, item ->
        val p = item.product
        PdfRow(
            buildList {
                add(fmt.number(i + 1L))
                add(p.name)
                add(p.categoryId?.let { categoryNames[it] }.orEmpty())
                add(labels.unitName(p.unit))
                add(fmt.money(p.sellPrice))
                if (showCost) add(p.costPrice?.let { fmt.money(it) }.orEmpty())
                add(fmt.quantity(item.stock))
            }
        )
    }
    val blocks = listOf(
        PdfBlock.Table(columns, rows),
        PdfBlock.Gap(4f),
        PdfBlock.Lines(listOf(PdfLine(labels.totalProducts, fmt.number(active.size.toLong()), bold = true, ruleAbove = true)))
    )
    return PdfBuild.Ready(contentOf(header, labels, labels.productListTitle, listOf(labels.generatedOn(generatedOnText)), blocks))
}

// =========================================================================================
// 3. The reports: the shown tab, with the same numbers and lines as the screen
// =========================================================================================

/**
 * The tab the screen shows, for the period it shows ([periodText], for example "এই মাস (০১/১১/২০২৬ – ৩০/১১/২০২৬)").
 * [body] is the screen's own result (the same repository numbers); every line comes from the same rules:
 * salesLineAmount, profitLines, shownSummaryLines... Cancelled sales are already left out of it.
 */
fun buildReportPdf(
    header: PdfHeader,
    tab: ReportTab,
    periodText: String,
    body: ReportBody,
    labels: PdfLabels,
    fmt: PdfFormat,
    generatedOnText: String
): PdfBuild {
    val subtitle = listOfNotNull(periodText.takeIf { it.isNotBlank() }?.let(labels.periodLine), labels.generatedOn(generatedOnText))
    fun ready(blocks: List<PdfBlock>) = PdfBuild.Ready(contentOf(header, labels, labels.reportTitle(tab), subtitle, blocks))

    return when (body) {
        ReportBody.PickDates -> PdfBuild.Empty(EmptyReason.PICK_DATES)

        is ReportBody.Sales -> {
            if (body.report.count == 0 && body.report.cancelledCount == 0) return PdfBuild.Empty(EmptyReason.NO_REPORT_DATA)
            ready(salesBlocks(body.report, body.rows, labels, fmt))
        }

        is ReportBody.Due -> {
            val blocks = mutableListOf<PdfBlock>()
            blocks += PdfBlock.Heading(labels.dueNowTitle)
            blocks += PdfBlock.Lines(
                listOf(
                    PdfLine(labels.receivable, fmt.money(body.now.receivable)),
                    PdfLine(labels.advance, fmt.money(body.now.advance)),
                    PdfLine(labels.net, fmt.money(abs(body.now.net)), bold = true, ruleAbove = true)
                )
            )
            if (body.owing.isNotEmpty()) {
                blocks += PdfBlock.Heading(labels.owingTitle)
                val columns = listOf(
                    PdfColumn(labels.serial, PdfAlign.START, ColumnWidth.Fixed(34f)),
                    PdfColumn(labels.name, PdfAlign.START, ColumnWidth.Flex(3f, 80f)),
                    PdfColumn(labels.phone, PdfAlign.START, ColumnWidth.Fixed(92f)),
                    PdfColumn(labels.due, PdfAlign.END, ColumnWidth.Fixed(92f))
                )
                val rows = body.owing.mapIndexed { i, r ->
                    PdfRow(listOf(fmt.number(i + 1L), r.customer.name, r.customer.phone?.let { toDigits(it, fmt) }.orEmpty(), fmt.money(r.balance)))
                }
                blocks += PdfBlock.Table(columns, rows, listOf(PdfRow(listOf("", labels.totalDue, "", fmt.money(body.now.receivable)), bold = true)))
            }
            body.period?.let { period ->
                blocks += PdfBlock.Heading(labels.periodTransactionsTitle)
                blocks += PdfBlock.Lines(periodLines(period, labels, fmt))
            }
            if (body.owing.isEmpty() && body.now.advance == 0L && body.period == null) return PdfBuild.Empty(EmptyReason.NO_REPORT_DATA)
            ready(blocks)
        }

        is ReportBody.Expenses -> {
            if (body.count == 0) return PdfBuild.Empty(EmptyReason.NO_REPORT_DATA)
            val s = body.summary
            val blocks = mutableListOf<PdfBlock>()
            blocks += PdfBlock.Lines(
                listOf(
                    PdfLine(labels.expenseShop, fmt.money(s.shop)),
                    PdfLine(labels.expensePersonal, fmt.money(s.personal)),
                    PdfLine(labels.expenseTotal, fmt.money(s.total), bold = true, ruleAbove = true)
                )
            )
            if (s.byCategory.isNotEmpty()) {
                blocks += PdfBlock.Heading(labels.expenseByCategory)
                val columns = listOf(
                    PdfColumn(labels.categoryHeader, PdfAlign.START, ColumnWidth.Flex(3f, 90f)),
                    PdfColumn(labels.count, PdfAlign.END, ColumnWidth.Fixed(70f)),
                    PdfColumn(labels.total, PdfAlign.END, ColumnWidth.Fixed(100f))
                )
                val rows = s.byCategory.map { c ->
                    PdfRow(listOf(if (c.isPersonal) "${c.name} (${labels.personalMark})" else c.name, labels.expenseCount(c.count.toLong()), fmt.money(c.total)))
                }
                blocks += PdfBlock.Table(columns, rows, listOf(PdfRow(listOf(labels.expenseTotal, labels.expenseCount(body.count.toLong()), fmt.money(s.total)), bold = true)))
            }
            ready(blocks)
        }

        is ReportBody.Profit -> {
            val p = body.report
            if (p.netSales == 0L && p.costOfGoods == 0L && p.shopExpenses == 0L && p.deliveryCharge == 0L && p.personalExpenses == 0L) {
                return PdfBuild.Empty(EmptyReason.NO_REPORT_DATA)
            }
            ready(profitBlocks(p, labels, fmt))
        }
    }
}

private fun salesBlocks(report: SalesReport, rows: List<SaleListRow>, labels: PdfLabels, fmt: PdfFormat): List<PdfBlock> {
    val blocks = mutableListOf<PdfBlock>()
    blocks += PdfBlock.Lines(
        buildList {
            for (line in SalesLine.entries) {
                val amount = salesLineAmount(report, line)
                add(
                    PdfLine(
                        label = labels.salesLine(line),
                        value = if (isMoneyLine(line)) fmt.money(amount) else labels.countValue(amount),
                        bold = isBoldSalesLine(line),
                        ruleAbove = line == SalesLine.TOTAL
                    )
                )
            }
            if (showsCancelledNote(report)) add(PdfLine(labels.cancelledNote(report.cancelledCount.toLong()), "", small = true))
        }
    )
    if (rows.isNotEmpty()) {
        blocks += PdfBlock.Heading(labels.salesListTitle)
        val columns = listOf(
            PdfColumn(labels.receiptNo, PdfAlign.START, ColumnWidth.Fixed(88f)),
            PdfColumn(labels.date, PdfAlign.START, ColumnWidth.Fixed(76f)),
            PdfColumn(labels.customer, PdfAlign.START, ColumnWidth.Flex(2f, 60f)),
            PdfColumn(labels.total, PdfAlign.END, ColumnWidth.Fixed(78f)),
            PdfColumn(labels.due, PdfAlign.END, ColumnWidth.Fixed(78f))
        )
        val body = rows.map { r ->
            PdfRow(
                listOf(
                    toDigits(r.receiptNo, fmt),
                    fmt.date(r.saleDate),
                    r.customerName?.trim()?.ifEmpty { null } ?: labels.walkIn,
                    fmt.money(r.total),
                    if (r.dueAmount > 0) fmt.money(r.dueAmount) else ""
                )
            )
        }
        blocks += PdfBlock.Table(columns, body, listOf(PdfRow(listOf("", "", labels.total, fmt.money(report.total), fmt.money(report.due)), bold = true)))
    }
    return blocks
}

/** The lines of the বাকি tab's period box (the same lines as the Khata period summary). */
private fun periodLines(summary: PeriodSummary, labels: PdfLabels, fmt: PdfFormat): List<PdfLine> = buildList {
    for (line in shownSummaryLines(summary)) {
        val amount = summaryAmount(summary, line)
        val bold = line == SummaryLine.NET || line == SummaryLine.DUE_GIVEN || line == SummaryLine.RECEIVED
        add(
            PdfLine(
                label = labels.summaryLine(line),
                value = summaryAmountText(line, amount, fmt.taka, formatAbs(amount, fmt)),
                bold = bold,
                ruleAbove = line == SummaryLine.NET
            )
        )
        if (line == SummaryLine.DUE_GIVEN && showsDueBreakdown(summary)) {
            add(PdfLine(labels.dueBreakdown(fmt.money(summary.dueOnSales), fmt.money(summary.dueByHand)), "", small = true))
        }
    }
}

/** The money without the taka sign, of the absolute amount ("১,২০০"). */
private fun formatAbs(paisa: Long, fmt: PdfFormat): String = fmt.money(abs(paisa)).removePrefix(fmt.taka).trim()

private fun profitBlocks(p: ProfitReport, labels: PdfLabels, fmt: PdfFormat): List<PdfBlock> {
    val blocks = mutableListOf<PdfBlock>()
    blocks += PdfBlock.Lines(
        profitLines(p).map { line ->
            val isNet = line.kind == ProfitLineKind.NET_PROFIT
            val loss = if (isNet) netResultWord(p) == ResultWord.LOSS else line.amount < 0
            PdfLine(
                label = labels.profitLine(line.kind, loss),
                value = profitAmountText(line, fmt.taka, formatAbs(line.amount, fmt)),
                bold = isBoldProfitLine(line),
                ruleAbove = line.kind == ProfitLineKind.GROSS_PROFIT || isNet
            )
        }
    )
    val info = profitInfoLines(p)
    if (info.isNotEmpty()) {
        blocks += PdfBlock.Gap(2f)
        blocks += PdfBlock.Lines(
            info.map { i ->
                val text = when (i.kind) {
                    InfoLineKind.DELIVERY_CHARGE -> labels.deliveryInfo(fmt.money(i.amount))
                    InfoLineKind.PERSONAL_EXPENSES -> labels.personalInfo(fmt.money(i.amount))
                }
                PdfLine(text, "", small = true)
            }
        )
    }
    approximateNote(p)?.let { note ->
        blocks += PdfBlock.Note(labels.approximate(note.missingCostLines.toLong(), fmt.money(note.missingCostSales)))
    }
    return blocks
}

// =========================================================================================
// 4. One customer's statement: every entry in date order with the running balance
// =========================================================================================

/** One line of a statement: the entry, its text, its signed amount and the balance AFTER it. */
data class StatementLine(val entry: LedgerEntry, val description: String, val signed: Long, val runningBalance: Long)

/**
 * The entries oldest first (by entry date, then the time they were written, then id) with the running
 * balance: the sum of [signedAmount] of every entry so far, which is exactly how the customer's balance is
 * worked out (BALANCE_SUM_SQL), so the last running balance equals the current balance.
 */
fun statementLines(rows: List<LedgerHistoryRow>, describe: (LedgerHistoryRow) -> String): List<StatementLine> {
    val ordered = rows.sortedWith(compareBy<LedgerHistoryRow> { it.entry.entryDate }.thenBy { it.entry.createdAt }.thenBy { it.entry.id })
    var balance = 0L
    return ordered.map { row ->
        val signed = signedAmount(row.entry)
        balance += signed
        StatementLine(row.entry, describe(row), signed, balance)
    }
}

/** A balance as text in a column: "৳ ১২০", "০" for zero, "− ৳ ৫০" for an advance. */
fun balanceColumnText(balance: Long, fmt: PdfFormat): String = when {
    balance > 0 -> fmt.money(balance)
    balance < 0 -> "− " + fmt.money(-balance)
    else -> fmt.number(0)
}

fun buildStatementPdf(
    header: PdfHeader,
    customer: Customer,
    rows: List<LedgerHistoryRow>,
    currentBalance: Long,
    describe: (LedgerHistoryRow) -> String,
    labels: PdfLabels,
    fmt: PdfFormat,
    generatedOnText: String
): PdfBuild {
    if (rows.isEmpty()) return PdfBuild.Empty(EmptyReason.NO_ENTRIES)
    val lines = statementLines(rows, describe)
    val columns = listOf(
        PdfColumn(labels.date, PdfAlign.START, ColumnWidth.Fixed(76f)),
        PdfColumn(labels.description, PdfAlign.START, ColumnWidth.Flex(3f, 90f)),
        PdfColumn(labels.amount, PdfAlign.END, ColumnWidth.Fixed(86f)),
        PdfColumn(labels.balance, PdfAlign.END, ColumnWidth.Fixed(86f))
    )
    val body = lines.map { l ->
        PdfRow(
            listOf(
                fmt.date(l.entry.entryDate),
                l.description,
                historyAmountText(l.entry, fmt.money(l.entry.amount).removePrefix(fmt.taka).trim(), fmt.taka),
                balanceColumnText(l.runningBalance, fmt)
            )
        )
    }
    val currentLabel = when (balanceStatus(currentBalance)) {
        BalanceStatus.OWES -> labels.currentDue
        BalanceStatus.ADVANCE -> labels.currentAdvance
        BalanceStatus.CLEAR -> labels.currentClear
    }
    val currentText = if (currentBalance == 0L) "" else fmt.money(abs(currentBalance))
    val footer = listOf(PdfRow(listOf("", currentLabel, "", currentText), bold = true))
    val subtitle = buildList {
        add(labels.customerLine(customer.name))
        customer.phone?.takeIf { it.isNotBlank() }?.let { add("${labels.mobileLabel}: ${toDigits(it, fmt)}") }
        add(labels.generatedOn(generatedOnText))
    }
    return PdfBuild.Ready(contentOf(header, labels, labels.statementTitle, subtitle, listOf(PdfBlock.Table(columns, body, footer))))
}

/** The note of a history row, joined to its title for the description column ("বিক্রির বাকি · রসিদ… (মন্তব্য)"). */
fun statementDescription(row: LedgerHistoryRow, title: String): String {
    val note = historyNote(row)
    return if (note.isNullOrBlank()) title else "$title · $note"
}
