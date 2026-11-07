package de.davscha.characterfun.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.io.File

/** Eine Seite im Bilderbuch: Erzähltext und optional ein Bild (darüber). */
class BookPage(val text: String, val image: String?)

/** Baut aus Titel und Seiten ein PDF (A4 hochkant) und legt es unter Dokumente/CharacterFun ab. */
object PictureBook {
    private const val W = 595
    private const val H = 842
    private const val M = 48f

    fun build(context: Context, title: String, subtitle: String, cover: File?, pages: List<BookPage>): Uri? {
        val doc = PdfDocument()
        var pageNo = 0
        fun newPage(): PdfDocument.Page {
            pageNo++
            return doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        }
        val paper = 0xFFFFF8EC.toInt()
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = 0xFF9A8A70.toInt(); textAlign = Paint.Align.CENTER }
        fun finish(p: PdfDocument.Page, number: Boolean = true) {
            if (number) p.canvas.drawText("– ${pageNo - 1} –", W / 2f, H - 24f, footer)
            doc.finishPage(p)
        }

        // ---- Titelseite ----
        run {
            val p = newPage()
            val c = p.canvas
            c.drawColor(paper)
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); color = 0xFF3A2A1A.toInt() }
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC); color = 0xFF7A6040.toInt() }
            var y = 140f
            cover?.let { f -> image(f)?.let { bmp ->
                val size = 260f
                val r = RectF((W - size) / 2, y, (W + size) / 2, y + size)
                c.drawBitmap(bmp, null, r, Paint(Paint.FILTER_BITMAP_FLAG))
                c.drawRect(r, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = 0xFF3A2A1A.toInt() })
                y += size + 50
            } }
            y += drawCentered(c, title, titlePaint, y) + 16
            drawCentered(c, subtitle, subPaint, y)
            finish(p, number = false)
        }

        // ---- Seiten: Bild oben, Text darunter; langer Text läuft auf die nächste Seite weiter ----
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.SERIF; color = 0xFF2A2018.toInt() }
        val textWidth = (W - 2 * M).toInt()
        for (page in pages) {
            val layout = StaticLayout.Builder.obtain(spans(page.text), 0, spans(page.text).length, textPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(4f, 1f).build()
            var p = newPage()
            p.canvas.drawColor(paper)
            var top = M
            page.image?.let { path -> image(File(path))?.let { bmp ->
                val maxW = W - 2 * M
                val maxH = H * 0.55f
                val scale = minOf(maxW / bmp.width, maxH / bmp.height)
                val w = bmp.width * scale
                val h = bmp.height * scale
                val r = RectF((W - w) / 2, top, (W + w) / 2, top + h)
                p.canvas.drawBitmap(bmp, null, r, Paint(Paint.FILTER_BITMAP_FLAG))
                top += h + 22
            } }
            var line = 0
            while (line < layout.lineCount) {
                val available = H - M - 20 - top
                val startY = layout.getLineTop(line)
                var end = line
                while (end < layout.lineCount && layout.getLineBottom(end) - startY <= available) end++
                if (end == line) end = line + 1
                val c = p.canvas
                c.save()
                c.translate(M, top - startY)
                c.clipRect(0f, startY.toFloat(), textWidth.toFloat(), layout.getLineBottom(end - 1).toFloat())
                layout.draw(c)
                c.restore()
                line = end
                if (line < layout.lineCount) {
                    finish(p)
                    p = newPage()
                    p.canvas.drawColor(paper)
                    top = M
                }
            }
            finish(p)
        }

        // ---- Ende ----
        run {
            val p = newPage()
            p.canvas.drawColor(paper)
            val endPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC); color = 0xFF3A2A1A.toInt() }
            drawCentered(p.canvas, "Ende", endPaint, H / 2f - 20)
            finish(p, number = false)
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Bilderbuch-${title.filter { it.isLetterOrDigit() || it == ' ' }.trim().replace(' ', '-').take(40)}-${System.currentTimeMillis()}.pdf")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/CharacterFun")
        }
        val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
        doc.close()
        return uri
    }

    private fun image(f: File) = runCatching {
        if (!f.exists()) return@runCatching null
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 1 })
    }.getOrNull()

    /** Text mittig, mehrzeilig; gibt die Höhe zurück */
    private fun drawCentered(c: Canvas, text: String, paint: TextPaint, y: Float): Int {
        val l = StaticLayout.Builder.obtain(text, 0, text.length, paint, (W - 2 * M).toInt()).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        c.save()
        c.translate(M, y)
        l.draw(c)
        c.restore()
        return l.height
    }

    /** *Handlungen* kursiv */
    private fun spans(text: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        val parts = text.split('*')
        parts.forEachIndexed { i, part ->
            val start = sb.length
            sb.append(part)
            if (i % 2 == 1 && i < parts.lastIndex) {
                sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, 0)
                sb.setSpan(ForegroundColorSpan(Color.rgb(90, 75, 60)), start, sb.length, 0)
            }
        }
        return sb
    }
}
