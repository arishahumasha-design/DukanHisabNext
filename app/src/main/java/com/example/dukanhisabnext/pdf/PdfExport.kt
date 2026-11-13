package com.example.dukanhisabnext.pdf

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.core.content.FileProvider
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.CustomerRepository
import com.example.dukanhisabnext.data.LedgerRepository
import com.example.dukanhisabnext.data.ProductRepository
import com.example.dukanhisabnext.data.ShopRepository
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.ledger.HistoryLabels
import com.example.dukanhisabnext.ledger.SummaryLine
import com.example.dukanhisabnext.ledger.historyTitle
import com.example.dukanhisabnext.report.ProfitLineKind
import com.example.dukanhisabnext.ui.report.ReportBody
import com.example.dukanhisabnext.ui.report.ReportTab
import com.example.dukanhisabnext.ui.report.SalesLine
import com.example.dukanhisabnext.util.DhakaTime
import com.example.dukanhisabnext.util.formatDate
import com.example.dukanhisabnext.util.formatQuantity
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// Everything that connects the pure documents to the phone: the words from strings.xml, reading the data
// through the repositories, writing the file into cache/pdf/ (never backed up), and the share sheet.

/** What an export gives back to the screen. */
sealed interface PdfResult {
    data class Done(val file: File) : PdfResult
    data class Empty(val reason: EmptyReason) : PdfResult
    data object Failed : PdfResult
}

/** Deletes every PDF of the pdf folder (called before each export and at app start). */
fun clearPdfFolder(cacheDir: File) {
    File(cacheDir, PDF_FOLDER).listFiles()?.forEach { it.delete() }
}

/** "১৩ নভেম্বর ২০২৬" for the "তৈরির তারিখ" line. */
fun longDateText(date: LocalDate, monthNames: List<String>): String =
    "${toBengaliDigits(date.dayOfMonth.toString())} ${monthNames[date.monthValue - 1]} ${toBengaliDigits(date.year.toString())}"

/**
 * The period line of a report: "এই মাস · ০১/১১/২০২৬ – ৩০/১১/২০২৬" ("আজ · ১৩/১১/২০২৬" for one day). [chipLabel] is the
 * word of the time chip ("এই মাস"), null for a custom range. [range] is the window the screen shows (null: no range yet, empty text).
 */
fun reportPeriodText(chipLabel: String?, range: DhakaTime.TimeRange?, date: (Long) -> String): String {
    if (range == null) return ""
    val first = date(range.startMillis)
    val last = date(range.endMillisExclusive - 1)
    val days = if (first == last) first else "$first – $last"
    return if (chipLabel == null) days else "$chipLabel · $days"
}

/** The words of the four documents, from strings.xml. */
fun pdfLabels(res: Resources): PdfLabels {
    fun s(id: Int) = res.getString(id)
    fun s(id: Int, vararg args: Any) = res.getString(id, *args)
    fun digits(n: Long) = toBengaliDigits(n.toString())
    return PdfLabels(
        shopPlaceholder = s(R.string.pdf_shop_placeholder),
        mobileLabel = s(R.string.pdf_mobile),
        generatedOn = { s(R.string.pdf_generated_on, it) },
        periodLine = { s(R.string.pdf_period_line, it) },
        serial = s(R.string.pdf_col_serial), name = s(R.string.pdf_col_name), phone = s(R.string.pdf_col_phone),
        dueListTitle = s(R.string.pdf_title_due_list), due = s(R.string.pdf_col_due),
        totalDue = s(R.string.pdf_total_due), totalAdvance = s(R.string.pdf_total_advance),
        productListTitle = s(R.string.pdf_title_products), category = s(R.string.pdf_col_category), unit = s(R.string.pdf_col_unit),
        sellPrice = s(R.string.pdf_col_sell), costPrice = s(R.string.pdf_col_cost), stock = s(R.string.pdf_col_stock),
        totalProducts = s(R.string.pdf_total_products),
        unitName = { unit ->
            s(
                when (unit) {
                    com.example.dukanhisabnext.data.entity.ProductUnit.PIECE -> R.string.unit_piece
                    com.example.dukanhisabnext.data.entity.ProductUnit.KG -> R.string.unit_kg
                    com.example.dukanhisabnext.data.entity.ProductUnit.LITRE -> R.string.unit_litre
                    com.example.dukanhisabnext.data.entity.ProductUnit.PACKET -> R.string.unit_packet
                    com.example.dukanhisabnext.data.entity.ProductUnit.DOZEN -> R.string.unit_dozen
                }
            )
        },
        reportTitle = { tab ->
            s(
                when (tab) {
                    ReportTab.SALES -> R.string.pdf_title_report_sales
                    ReportTab.DUE -> R.string.pdf_title_report_due
                    ReportTab.EXPENSES -> R.string.pdf_title_report_expenses
                    ReportTab.PROFIT -> R.string.pdf_title_report_profit
                }
            )
        },
        salesLine = { line ->
            s(
                when (line) {
                    SalesLine.COUNT -> R.string.report_sales_count
                    SalesLine.NET_SALES -> R.string.report_sales_net
                    SalesLine.DELIVERY -> R.string.report_sales_delivery
                    SalesLine.TOTAL -> R.string.report_sales_total
                    SalesLine.PAID -> R.string.report_sales_paid
                    SalesLine.DUE -> R.string.report_sales_due
                }
            )
        },
        countValue = { s(R.string.report_sales_count_value, digits(it)) },
        cancelledNote = { s(R.string.report_cancelled_note, digits(it)) },
        salesListTitle = s(R.string.report_sales_list_title), receiptNo = s(R.string.pdf_col_receipt), date = s(R.string.pdf_col_date),
        customer = s(R.string.pdf_col_customer), total = s(R.string.pdf_col_total), walkIn = s(R.string.walk_in_customer),
        dueNowTitle = s(R.string.report_due_now_title), receivable = s(R.string.khata_sum_receivable),
        advance = s(R.string.khata_sum_advance), net = s(R.string.khata_sum_net),
        owingTitle = s(R.string.report_due_owing_title), periodTransactionsTitle = s(R.string.report_due_period_title),
        summaryLine = { line ->
            s(
                when (line) {
                    SummaryLine.DUE_GIVEN -> R.string.khata_line_due_given
                    SummaryLine.RECEIVED -> R.string.khata_line_received
                    SummaryLine.OPENING_DUE -> R.string.khata_line_opening
                    SummaryLine.ADVANCE_RETURNED -> R.string.khata_line_advance_returned
                    SummaryLine.CORRECTION -> R.string.khata_line_correction
                    SummaryLine.SALE_CANCEL -> R.string.khata_line_sale_cancel
                    SummaryLine.NET -> R.string.khata_line_net
                }
            )
        },
        dueBreakdown = { a, b -> s(R.string.khata_line_due_breakdown, a, b) },
        expenseShop = s(R.string.expense_sum_shop), expensePersonal = s(R.string.expense_sum_personal),
        expenseTotal = s(R.string.expense_sum_total), expenseByCategory = s(R.string.expense_by_category),
        categoryHeader = s(R.string.pdf_col_category), count = s(R.string.pdf_col_count), personalMark = s(R.string.expense_personal_tag),
        expenseCount = { s(R.string.expense_count, digits(it)) },
        profitLine = { kind, loss ->
            s(
                when (kind) {
                    ProfitLineKind.NET_SALES -> R.string.report_profit_net_sales
                    ProfitLineKind.COST_OF_GOODS -> R.string.report_profit_cost
                    ProfitLineKind.GROSS_PROFIT -> if (loss) R.string.report_profit_gross_loss else R.string.report_profit_gross
                    ProfitLineKind.SHOP_EXPENSES -> R.string.report_profit_shop_expenses
                    ProfitLineKind.NET_PROFIT -> if (loss) R.string.report_profit_net_loss else R.string.report_profit_net
                }
            )
        },
        deliveryInfo = { s(R.string.report_profit_delivery_info, it) },
        personalInfo = { s(R.string.report_profit_personal_info, it) },
        approximate = { lines, sales -> s(R.string.report_approximate, digits(lines), sales) },
        statementTitle = s(R.string.pdf_title_statement), customerLine = { s(R.string.pdf_customer_line, it) },
        description = s(R.string.pdf_col_description), amount = s(R.string.pdf_col_amount), balance = s(R.string.pdf_col_balance),
        currentDue = s(R.string.pdf_current_due), currentAdvance = s(R.string.pdf_current_advance), currentClear = s(R.string.pdf_current_clear)
    )
}

/** Bengali digits, taka sign and dates, the same as the screens. */
fun pdfFormat(res: Resources) = PdfFormat(
    money = { res.getString(R.string.price_format, formatTaka(it)) },
    date = ::formatDate,
    number = { toBengaliDigits(it.toString()) },
    quantity = ::formatQuantity,
    taka = res.getString(R.string.unit_taka)
)

private fun historyLabels(res: Resources) = HistoryLabels(
    openingDue = res.getString(R.string.hist_opening_due),
    saleDue = res.getString(R.string.hist_sale_due),
    manualDue = res.getString(R.string.hist_manual_due),
    payment = res.getString(R.string.hist_payment),
    correctionPlus = res.getString(R.string.hist_correction_plus),
    correctionMinus = res.getString(R.string.hist_correction_minus),
    saleCancel = res.getString(R.string.hist_sale_cancel),
    advanceReturned = res.getString(R.string.hist_advance_returned),
    taka = res.getString(R.string.unit_taka)
)

private fun methodName(res: Resources, m: PaymentMethod): String = res.getString(
    when (m) {
        PaymentMethod.CASH -> R.string.receipt_method_cash
        PaymentMethod.BKASH -> R.string.receipt_method_bkash
        PaymentMethod.NAGAD -> R.string.receipt_method_nagad
        PaymentMethod.BANK -> R.string.receipt_method_bank
        PaymentMethod.OTHER -> R.string.receipt_method_other
    }
)

/**
 * Builds a document from the data and writes it to cache/pdf/. Read-only: it never changes the database.
 * All of it runs off the main thread (the layout of 1,000+ rows takes a moment).
 */
class PdfExportService(
    private val context: Context,
    private val shop: ShopRepository,
    private val ledger: LedgerRepository,
    private val customers: CustomerRepository,
    private val products: ProductRepository,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val res: Resources get() = context.resources

    private suspend fun header(labels: PdfLabels): PdfHeader {
        val profile = shop.get()
        return PdfHeader(profile?.name.orEmpty().ifBlank { labels.shopPlaceholder }, profile?.address?.takeIf { it.isNotBlank() }, profile?.phone?.let { toBengaliDigits(it) })
    }

    private fun today(): LocalDate = DhakaTime.localDate(clock())

    private fun generatedOn(): String = longDateText(today(), res.getStringArray(R.array.months_bn).toList())

    private suspend fun finish(build: PdfBuild, kind: PdfKind, customerId: Long? = null): PdfResult = withContext(Dispatchers.Default) {
        when (build) {
            is PdfBuild.Empty -> PdfResult.Empty(build.reason)
            is PdfBuild.Ready -> try {
                clearPdfFolder(context.cacheDir)
                val fonts = PdfFonts.load(context)
                val measure = PaintMeasure(fonts)
                val pages = layoutDocument(build.content, measure) { page, total ->
                    res.getString(R.string.pdf_footer, toBengaliDigits(page.toString()), toBengaliDigits(total.toString()))
                }
                val file = File(File(context.cacheDir, PDF_FOLDER), pdfFileName(kind, today(), customerId))
                writePdf(pages, measure, file)
                PdfResult.Done(file)
            } catch (e: Exception) {
                PdfResult.Failed
            }
        }
    }

    /** The due list: everyone who owes, as of now (the screen's period and search are not applied). */
    suspend fun dueList(): PdfResult {
        val labels = pdfLabels(res)
        val rows = ledger.observeKhataCustomers().first()
        val build = withContext(Dispatchers.Default) { buildDueListPdf(header(labels), rows, labels, pdfFormat(res), generatedOn()) }
        return finish(build, PdfKind.DUE_LIST)
    }

    /** The product list (archived products left out). */
    suspend fun productList(showCost: Boolean): PdfResult {
        val labels = pdfLabels(res)
        val items = products.observeProducts(isActive = true, query = "", categoryId = null).first()
        val categoryNames = products.observeCategories().first().associate { it.id to it.name }
        val build = withContext(Dispatchers.Default) { buildProductListPdf(header(labels), items, categoryNames, showCost, labels, pdfFormat(res), generatedOn()) }
        return finish(build, PdfKind.PRODUCTS)
    }

    /** The shown report tab, from the screen's own result [body]. */
    suspend fun report(tab: ReportTab, periodText: String, body: ReportBody): PdfResult {
        val labels = pdfLabels(res)
        val build = withContext(Dispatchers.Default) { buildReportPdf(header(labels), tab, periodText, body, labels, pdfFormat(res), generatedOn()) }
        val kind = when (tab) {
            ReportTab.SALES -> PdfKind.REPORT_SALES
            ReportTab.DUE -> PdfKind.REPORT_DUE
            ReportTab.EXPENSES -> PdfKind.REPORT_EXPENSES
            ReportTab.PROFIT -> PdfKind.REPORT_PROFIT
        }
        return finish(build, kind)
    }

    /** One customer's statement. */
    suspend fun statement(customerId: Long): PdfResult {
        val customer = customers.getCustomer(customerId) ?: return PdfResult.Failed
        val labels = pdfLabels(res)
        val rows = ledger.observeHistory(customerId).first()
        val balance = ledger.getBalance(customerId)
        val hist = historyLabels(res)
        val build = withContext(Dispatchers.Default) {
            buildStatementPdf(
                header(labels), customer, rows, balance,
                describe = { row -> statementDescription(row, historyTitle(row, hist) { methodName(res, it) }) },
                labels = labels, fmt = pdfFormat(res), generatedOnText = generatedOn()
            )
        }
        return finish(build, PdfKind.STATEMENT, customerId)
    }
}

/** Opens the system share sheet for a PDF, through the FileProvider (no permission needed). */
fun sharePdf(context: Context, file: File, title: String) {
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, title))
    } catch (e: ActivityNotFoundException) {
        // No app can take it: nothing to do.
    }
}
