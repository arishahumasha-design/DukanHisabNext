package com.example.dukanhisabnext.pdf

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.example.dukanhisabnext.R
import java.io.File
import java.io.FileOutputStream

// The Android part of the PDF export: the bundled Bengali font, the text measuring that matches it, and
// the drawing of the instructions PdfDocumentLayout.kt decided. No decisions about WHERE things go live here.

/** Noto Sans Bengali (a variable font) in the two weights a document uses: regular (400) and semibold (600). */
class PdfFonts private constructor(val regular: Typeface, val bold: Typeface) {
    companion object {
        /**
         * Copies res/font/noto_sans_bengali.ttf to a cache file (Typeface.Builder wants a file) and builds both
         * weights from it with font variation settings. If that fails the system fonts are used.
         */
        // A font file is a plain file inside the app, so openRawResource can copy it; lint only expects res/raw here.
        @SuppressLint("ResourceType")
        fun load(context: Context): PdfFonts {
            return try {
                val file = File(context.cacheDir, "pdf-font/noto_sans_bengali.ttf")
                if (!file.exists() || file.length() == 0L) {
                    file.parentFile?.mkdirs()
                    context.resources.openRawResource(R.font.noto_sans_bengali).use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                }
                val regular = Typeface.Builder(file).setFontVariationSettings("'wght' 400").build()
                val bold = Typeface.Builder(file).setFontVariationSettings("'wght' 600").build()
                if (regular != null && bold != null) PdfFonts(regular, bold) else system()
            } catch (e: Exception) {
                system()
            }
        }

        private fun system() = PdfFonts(Typeface.DEFAULT, Typeface.DEFAULT_BOLD)
    }
}

/** Measures text with the same paints the writer draws with, so a layout decision matches what is drawn. */
class PaintMeasure(private val fonts: PdfFonts) : TextMeasure {
    private val paints = HashMap<Pair<Float, Boolean>, TextPaint>()

    fun paint(size: Float, bold: Boolean): TextPaint = paints.getOrPut(size to bold) {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) fonts.bold else fonts.regular
        }
    }

    override fun width(text: String, size: Float, bold: Boolean): Float = paint(size, bold).measureText(text)
}

private val ACCENT_GREEN = Color.rgb(0x1B, 0x7F, 0x4C)

private fun PdfColor.argb(): Int = when (this) {
    PdfColor.TEXT -> Color.rgb(0x1A, 0x1A, 0x1A)
    PdfColor.MUTED -> Color.rgb(0x5F, 0x63, 0x68)
    PdfColor.ACCENT -> ACCENT_GREEN
    PdfColor.RULE -> Color.rgb(0xD0, 0xD3, 0xD6)
}

/** Draws every page of [pages] into a PDF file (white page, dark text). */
fun writePdf(pages: PdfPages, measure: PaintMeasure, out: File) {
    val document = PdfDocument()
    try {
        pages.pages.forEachIndexed { index, ops ->
            val info = PdfDocument.PageInfo.Builder(PdfPage.WIDTH.toInt(), PdfPage.HEIGHT.toInt(), index + 1).create()
            val page = document.startPage(info)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            for (op in ops) {
                when (op) {
                    is DrawOp.Text -> {
                        val paint = measure.paint(op.size, op.bold)
                        paint.color = op.color.argb()
                        val w = paint.measureText(op.text)
                        val x = when (op.align) {
                            PdfAlign.START -> op.x
                            PdfAlign.END -> op.x - w
                            PdfAlign.CENTER -> op.x - w / 2f
                        }
                        canvas.drawText(op.text, x, op.baseline, paint)
                    }
                    is DrawOp.Line -> {
                        linePaint.color = op.color.argb()
                        linePaint.strokeWidth = op.width
                        canvas.drawLine(op.x1, op.y1, op.x2, op.y2, linePaint)
                    }
                }
            }
            document.finishPage(page)
        }
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { document.writeTo(it) }
    } finally {
        document.close()
    }
}
