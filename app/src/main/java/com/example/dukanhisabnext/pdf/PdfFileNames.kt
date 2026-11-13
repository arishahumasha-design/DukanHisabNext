package com.example.dukanhisabnext.pdf

import java.time.LocalDate

/** The four documents; [slug] is the ASCII start of the file name. */
enum class PdfKind(val slug: String) {
    DUE_LIST("due-list"),
    PRODUCTS("products"),
    REPORT_SALES("report-sales"),
    REPORT_DUE("report-due"),
    REPORT_EXPENSES("report-expenses"),
    REPORT_PROFIT("report-profit"),
    STATEMENT("statement")
}

/** The folder inside cache/ where PDFs are written (never backed up). */
const val PDF_FOLDER = "pdf"

/**
 * An ASCII-only file name, safe for every share target: "due-list-2026-11-13.pdf",
 * "statement-42-2026-11-13.pdf" (the customer's number, never the name or phone).
 */
fun pdfFileName(kind: PdfKind, date: LocalDate, customerId: Long? = null): String {
    val id = if (kind == PdfKind.STATEMENT && customerId != null) "-$customerId" else ""
    return "${kind.slug}$id-$date.pdf"
}
