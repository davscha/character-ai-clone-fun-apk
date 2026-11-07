package de.davscha.characterfun.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import de.davscha.characterfun.data.Character
import de.davscha.characterfun.data.ChatMessage
import de.davscha.characterfun.data.Mood
import java.io.File
import kotlin.math.ceil

/** Malt einen Chat-Ausschnitt als Comic-Bild (zum Teilen). */
object ComicRenderer {
    private const val W = 1080
    private const val PAD = 48
    private const val AVATAR = 112
    private const val INNER = 30
    private const val GAP = 44
    private const val OUTLINE = 5f

    fun render(c: Character, avatarFile: File?, messages: List<ChatMessage>, youLabel: String, mood: String?): Bitmap {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f; color = Color.BLACK }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF5A4630.toInt() }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 64f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC); color = Color.BLACK }
        val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; color = 0xFF8A7A60.toInt(); textAlign = Paint.Align.RIGHT }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = OUTLINE; color = Color.BLACK; strokeJoin = Paint.Join.ROUND }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        val bubbleMax = W - PAD * 2 - AVATAR - 40
        val layouts = messages.map { m ->
            val sp = spans(Mood.strip(m.text).ifBlank { "…" })
            StaticLayout.Builder.obtain(sp, 0, sp.length, textPaint, bubbleMax - INNER * 2)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(8f, 1f)
                .build()
        }
        val headerH = 190
        val footerH = 90
        val itemHeights = layouts.map { 44 + it.height + INNER * 2 + GAP }
        val height = headerH + itemHeights.sum() + footerH

        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(0xFFFFF1CF.toInt())
        // Rasterpunkte wie im Comicheft
        fill.color = 0x14000000
        for (y in 0 until height step 28) for (x in (if (y / 28 % 2 == 0) 0 else 14) until W step 28) cv.drawCircle(x.toFloat(), y.toFloat(), 3.5f, fill)

        // Titel-Kasten
        val title = RectF(PAD.toFloat(), 36f, (W - PAD).toFloat(), (headerH - 30).toFloat())
        fill.color = 0xFFFFD84D.toInt()
        cv.drawRect(title, fill)
        cv.drawRect(title, outline)
        val titleText = "${c.emoji} ${c.name}${mood?.let { "  $it" } ?: ""}"
        cv.drawText(fit(titleText, titlePaint, title.width() - 60), title.left + 30, title.centerY() + 22, titlePaint)

        val avatarBmp = avatarFile?.takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
        var y = headerH.toFloat()
        messages.forEachIndexed { i, m ->
            val layout = layouts[i]
            val lineW = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
            val bw = (ceil(lineW).toInt() + INNER * 2).coerceAtLeast(120)
            val bh = layout.height + INNER * 2
            val nameY = y + 32
            val top = y + 44
            if (m.fromUser) {
                val right = (W - PAD).toFloat()
                namePaint.textAlign = Paint.Align.RIGHT
                cv.drawText(youLabel, right - 10, nameY, namePaint)
                val r = RectF(right - bw, top, right, top + bh)
                bubble(cv, r, tailLeft = false, color = 0xFFCDE8FF.toInt(), fill, outline)
                cv.save(); cv.translate(r.left + INNER, r.top + INNER); layout.draw(cv); cv.restore()
            } else {
                // Avatar: Bild rund ausgeschnitten, sonst Emoji im Kreis
                val ax = PAD.toFloat()
                val ay = top
                val circle = RectF(ax, ay, ax + AVATAR, ay + AVATAR)
                if (avatarBmp != null) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG)
                    val shader = BitmapShader(avatarBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    val scale = AVATAR / minOf(avatarBmp.width, avatarBmp.height).toFloat()
                    val m2 = android.graphics.Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(ax - (avatarBmp.width * scale - AVATAR) / 2, ay - (avatarBmp.height * scale - AVATAR) / 2)
                    }
                    shader.setLocalMatrix(m2)
                    p.shader = shader
                    cv.drawOval(circle, p)
                } else {
                    fill.color = Color.WHITE
                    cv.drawOval(circle, fill)
                    val ep = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 64f; textAlign = Paint.Align.CENTER }
                    cv.drawText(c.emoji.ifBlank { "🙂" }, circle.centerX(), circle.centerY() + 22, ep)
                }
                cv.drawOval(circle, outline)
                val left = ax + AVATAR + 40
                namePaint.textAlign = Paint.Align.LEFT
                cv.drawText(c.name, left + 10, nameY, namePaint)
                val r = RectF(left, top, left + bw, top + bh)
                bubble(cv, r, tailLeft = true, color = Color.WHITE, fill, outline)
                cv.save(); cv.translate(r.left + INNER, r.top + INNER); layout.draw(cv); cv.restore()
            }
            y += itemHeights[i]
        }
        cv.drawText("Character Fun", (W - PAD).toFloat(), (height - 36).toFloat(), footPaint)
        return bmp
    }

    /** Sprechblase mit Zipfel */
    private fun bubble(cv: Canvas, r: RectF, tailLeft: Boolean, color: Int, fill: Paint, outline: Paint) {
        val path = Path().apply {
            addRoundRect(r, 34f, 34f, Path.Direction.CW)
            if (tailLeft) {
                moveTo(r.left + 4, r.top + 30); lineTo(r.left - 30, r.top + 22); lineTo(r.left + 4, r.top + 64); close()
            } else {
                moveTo(r.right - 4, r.top + 30); lineTo(r.right + 30, r.top + 22); lineTo(r.right - 4, r.top + 64); close()
            }
        }
        path.fillType = Path.FillType.WINDING
        fill.color = color
        cv.drawPath(path, fill)
        cv.drawPath(path, outline)
        // Naht zwischen Zipfel und Blase übermalen
        val seam = Paint(fill).apply { this.color = color }
        if (tailLeft) cv.drawRect(r.left - 4, r.top + 35, r.left + 8, r.top + 58, seam)
        else cv.drawRect(r.right - 8, r.top + 35, r.right + 4, r.top + 58, seam)
    }

    /** *Handlungen* kursiv und grau, wie im Chat */
    private fun spans(text: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        val parts = text.split('*')
        parts.forEachIndexed { i, part ->
            val start = sb.length
            sb.append(part)
            if (i % 2 == 1 && i < parts.lastIndex) {
                sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, 0)
                sb.setSpan(ForegroundColorSpan(0xFF6B6B6B.toInt()), start, sb.length, 0)
            }
        }
        return sb
    }

    private fun fit(text: String, paint: Paint, maxW: Float): String {
        if (paint.measureText(text) <= maxW) return text
        var t = text
        while (t.length > 1 && paint.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    /** Speichert das Bild unter Bilder/CharacterFun und gibt die Adresse zum Teilen zurück */
    fun saveToGallery(context: Context, bmp: Bitmap, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CharacterFun")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return uri
    }
}
