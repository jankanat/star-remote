package kz.alnazar.starremote.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import kotlin.math.max

/**
 * Стеклянный фон для виджета, нарисованный картинкой: в виджетах Android
 * нельзя рисовать градиенты, поэтому блики «запекаются» в Bitmap.
 */
object WidgetArt {

    private fun hsl(h: Float, s: Float, l: Float, a: Float): Int =
        ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(floatArrayOf(h, s, l.coerceIn(0f, 1f))), (a.coerceIn(0f, 1f) * 255).toInt())

    private fun white(a: Float) = ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), (a.coerceIn(0f, 1f) * 255).toInt())

    /** Фон всего виджета: заливка цвета из настроек, мягкий блик, косой отблеск, светлая кромка. */
    fun panel(wPx: Int, hPx: Int, radiusPx: Float, hue: Float, sat: Float, light: Float, alpha: Float): Bitmap {
        val w = max(wPx, 8); val h = max(hPx, 8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val clip = Path().apply { addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW) }
        c.save(); c.clipPath(clip)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // основная заливка: сверху слева светлее
        p.shader = LinearGradient(0f, 0f, w * .6f, h * 1.4f,
            hsl(hue, sat, light + .12f, alpha), hsl(hue, sat, light, alpha), Shader.TileMode.CLAMP)
        c.drawRect(rect, p)

        // мягкое пятно света в верхнем левом углу
        val r1 = max(w, h) * .75f
        p.shader = RadialGradient(w * .12f, -h * .15f, r1,
            intArrayOf(white(.26f), white(.08f), white(0f)), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(rect, p)

        // косой отблеск, как от лампы на стекле
        val cx = w * .62f
        val bw = w * .16f
        p.shader = LinearGradient(cx - bw, -h * .2f, cx + bw, h * .35f,
            intArrayOf(white(0f), white(.13f), white(0f)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(rect, p)
        // второй, тонкий
        val cx2 = w * .8f
        val bw2 = w * .05f
        p.shader = LinearGradient(cx2 - bw2, -h * .2f, cx2 + bw2, h * .35f,
            intArrayOf(white(0f), white(.10f), white(0f)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(rect, p)

        // отражение снизу
        p.shader = LinearGradient(0f, h * .7f, 0f, h.toFloat(),
            intArrayOf(white(0f), white(.06f)), null, Shader.TileMode.CLAMP)
        c.drawRect(rect, p)
        c.restore()

        // кромка: сверху ярче, книзу почти пропадает
        val sw = max(1.5f, w / 300f)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(white(.45f), white(.12f), white(.2f)),
            floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        val inset = sw / 2
        c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), radiusPx - inset, radiusPx - inset, p)
        return bmp
    }

    /** Фон кнопки: полупрозрачное стекло с бликом сверху. */
    fun chip(wPx: Int, hPx: Int, radiusPx: Float, dark: Boolean): Bitmap {
        val w = max(wPx, 8); val h = max(hPx, 8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val clip = Path().apply { addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW) }
        c.save(); c.clipPath(clip)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val base = if (dark) ColorUtils.setAlphaComponent(0xFF000000.toInt(), 34) else white(.12f)
        p.color = base
        c.drawRect(rect, p)
        // верхний блик
        p.shader = LinearGradient(0f, 0f, 0f, h * .55f, intArrayOf(white(.20f), white(0f)), null, Shader.TileMode.CLAMP)
        c.drawRect(rect, p)
        // пятно света слева
        p.shader = RadialGradient(w * .1f, 0f, max(w, h) * .7f, intArrayOf(white(.14f), white(0f)), null, Shader.TileMode.CLAMP)
        c.drawRect(rect, p)
        c.restore()
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(white(.35f), white(.08f)), null, Shader.TileMode.CLAMP)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.2f, h / 60f)
        val i = p.strokeWidth / 2
        c.drawRoundRect(RectF(i, i, w - i, h - i), radiusPx - i, radiusPx - i, p)
        return bmp
    }
}
