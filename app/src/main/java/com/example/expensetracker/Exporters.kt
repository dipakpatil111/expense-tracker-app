package com.example.expensetracker

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun exportDir(context: Context): File = File(context.cacheDir, "exports").apply { mkdirs() }

fun shareFile(context: Context, file: File, mime: String, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareTextFile(context: Context, fileName: String, content: String, mime: String) {
    try {
        val f = File(exportDir(context), fileName)
        f.writeText(content)
        shareFile(context, f, mime, "Share $fileName")
    } catch (e: Exception) {
        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun exportCsv(context: Context, label: String, txs: List<Transaction>) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    val sb = StringBuilder("\uFEFFDate,Type,Mode,Category,Description,Amount\n")
    txs.sortedBy { it.timestamp }.forEach {
        sb.append(sdf.format(Date(it.timestamp))).append(',').append(it.type).append(',').append(it.mode).append(',')
            .append(esc(it.txCategory)).append(',').append(esc(it.description)).append(',').append(it.amount).append('\n')
    }
    shareTextFile(context, "transactions_${label.replace(Regex("[^A-Za-z0-9]+"), "_")}.csv", sb.toString(), "text/csv")
}

/** FIX: the old PDF stopped after one page (`if (y > 800f) break`). This one paginates and repeats the header. */
private class StatementWriter(val doc: PdfDocument, val label: String) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(paint)
    private var pageNo = 0
    private var started = false
    private lateinit var page: PdfDocument.Page
    private lateinit var canvas: Canvas
    var y = 0f

    private fun text(s: String, x: Float, size: Float, bold: Boolean, color: Int, right: Boolean = false, maxW: Float? = null) {
        paint.textSize = size; paint.isFakeBoldText = bold; paint.color = color
        paint.textAlign = if (right) Paint.Align.RIGHT else Paint.Align.LEFT
        val out = if (maxW != null) {
            tp.textSize = size; tp.isFakeBoldText = bold
            TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END).toString()
        } else s
        canvas.drawText(out, x, y, paint)
    }

    fun newPage(first: Boolean, credit: Double, debit: Double) {
        if (started) finishPage()
        pageNo++; started = true
        page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        canvas = page.canvas
        y = 48f
        text("Statement - $label", 40f, 16f, true, Color.BLACK); y += 18f
        text("Generated ${SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())}", 40f, 10f, false, Color.DKGRAY); y += 20f
        if (first) {
            text("Credit: Rs ${fmt(credit)}", 40f, 12f, true, Color.rgb(5, 150, 105))
            text("Debit: Rs ${fmt(debit)}", 230f, 12f, true, Color.rgb(220, 38, 38))
            text("Net: Rs ${fmt(credit - debit)}", 420f, 12f, true, Color.BLACK); y += 22f
        }
        paint.color = Color.LTGRAY; canvas.drawLine(40f, y - 12f, 555f, y - 12f, paint)
        text("Date", 40f, 10f, true, Color.BLACK)
        text("Description", 105f, 10f, true, Color.BLACK)
        text("Category", 320f, 10f, true, Color.BLACK)
        text("Mode", 400f, 10f, true, Color.BLACK)
        text("Amount", 555f, 10f, true, Color.BLACK, right = true)
        y += 16f
    }

    fun row(t: Transaction, sdf: SimpleDateFormat, credit: Double, debit: Double) {
        if (y > 800f) newPage(false, credit, debit)
        text(sdf.format(Date(t.timestamp)), 40f, 10f, false, Color.BLACK)
        text(t.description, 105f, 10f, false, Color.BLACK, maxW = 205f)
        text(t.txCategory, 320f, 10f, false, Color.DKGRAY, maxW = 72f)
        text(t.mode, 400f, 10f, false, Color.DKGRAY)
        val sign = if (t.type == "CREDIT") "+" else "-"
        text("$sign Rs ${fmt(t.amount)}", 555f, 10f, true,
            if (t.type == "CREDIT") Color.rgb(5, 150, 105) else Color.rgb(220, 38, 38), right = true)
        y += 18f
    }

    fun finishPage() {
        paint.textAlign = Paint.Align.RIGHT; paint.textSize = 9f; paint.isFakeBoldText = false; paint.color = Color.GRAY
        canvas.drawText("Page $pageNo", 555f, 825f, paint)
        doc.finishPage(page)
    }

    private fun fmt(v: Double): String = NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2; minimumFractionDigits = 0 }.format(v)
}

fun exportPdf(context: Context, label: String, txs: List<Transaction>, totalCredit: Double, totalDebit: Double) {
    val doc = PdfDocument()
    try {
        val w = StatementWriter(doc, label)
        val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        w.newPage(true, totalCredit, totalDebit)
        txs.sortedBy { it.timestamp }.forEach { w.row(it, sdf, totalCredit, totalDebit) }
        w.finishPage()
        val file = File(exportDir(context), "Statement_${label.replace(Regex("[^A-Za-z0-9]+"), "_")}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        shareFile(context, file, "application/pdf", "Share Statement PDF")
    } catch (e: Exception) {
        Toast.makeText(context, "PDF Error: ${e.message}", Toast.LENGTH_LONG).show()
    } finally {
        doc.close()
    }
}
