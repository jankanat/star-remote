package kz.alnazar.starremote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private fun smooth(t: Float) = t * t * (3 - 2 * t)
private fun outCubic(t: Float) = 1 - (1 - t).pow(3)
private fun inOut(t: Float) = if (t < .5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

/**
 * Звёздное небо: дрейф, полёт вперёд и звезда-индикатор.
 * Перенос макета из прототипа на Canvas.
 */
class StarSky {
    // параметры из настроек
    var starCount = 800
    var drift = .7f
    var warpDur = 2.4f
    var flareSize = .8f
    var hue = 251f
    var light = 7.5f
    var reduceMotion = false

    /** Область, куда может встать звезда-индикатор (между статусом и кнопками). */
    var zone: Rect = Rect.Zero

    /** Вызывается в момент вспышки (звук и вибрация). */
    var onFlare: () -> Unit = {}

    private var w = 0f
    private var h = 0f
    private var f = 1f
    private val rnd = Random(System.nanoTime())

    private var n = 0
    private var xs = FloatArray(0); private var ys = FloatArray(0); private var zs = FloatArray(0)
    private var tw = FloatArray(0); private var tws = FloatArray(0); private var sz = FloatArray(0)
    private var warm = BooleanArray(0)

    // полёт
    private enum class Phase { IDLE, ACCEL, CRUISE, DECEL }
    private var phase = Phase.IDLE
    private var pt = 0f
    private var decelFrom = 1f
    private var result: Boolean? = null
    private var intensity = 0f

    // звезда-индикатор
    private class Beacon(val fx: Float, val fy: Float, var t: Float, val seed: Float) {
        var fading = false
        var ft = 0f
    }
    private var beacon: Beacon? = null
    private var flash = -1f

    val hasBeacon get() = beacon != null && beacon?.fading == false
    val warping get() = phase != Phase.IDLE

    fun startWarp() { phase = Phase.ACCEL; pt = 0f; result = null }

    /** Операция закончилась: success → долетели и звезда вспыхнет. */
    fun endWarp(success: Boolean) {
        if (phase == Phase.IDLE) { if (success) placeBeacon(flare = true); return }
        result = success
    }

    fun fade() { beacon?.let { it.fading = true; it.ft = 0f } }

    /** Mac уже включён при открытии приложения: звезда горит сразу, без вспышки. */
    fun showSteady() { if (beacon == null || beacon?.fading == true) placeBeacon(flare = false) }

    private fun resize(nw: Float, nh: Float) {
        if (nw == w && nh == h) return
        w = nw; h = nh; f = max(w, h) * .55f
    }

    private fun ensureCount() {
        if (n == starCount) return
        val nn = starCount
        xs = xs.copyOf(nn); ys = ys.copyOf(nn); zs = zs.copyOf(nn)
        tw = tw.copyOf(nn); tws = tws.copyOf(nn); sz = sz.copyOf(nn); warm = warm.copyOf(nn)
        for (i in n until nn) spawn(i, true)
        n = nn
    }

    private fun spawn(i: Int, anywhere: Boolean) {
        xs[i] = rnd.nextFloat() * 2 - 1
        ys[i] = rnd.nextFloat() * 2 - 1
        zs[i] = if (anywhere) .03f + rnd.nextFloat() * .97f else .92f + rnd.nextFloat() * .08f
        tw[i] = rnd.nextFloat() * 6.283f
        tws[i] = .6f + rnd.nextFloat() * 1.6f
        sz[i] = .35f + rnd.nextFloat() * .9f
        warm[i] = rnd.nextFloat() < .14f
    }

    private fun placeBeacon(flare: Boolean) {
        val z = if (zone.width > 40 && zone.height > 40) zone
        else Rect(w * .16f, h * .15f, w * .84f, h * .45f)
        val cands = ArrayList<Int>()
        for (i in 0 until n) {
            if (zs[i] < .22f || zs[i] > .85f) continue
            val x = w * .5f + xs[i] / zs[i] * f
            val y = h * .5f + ys[i] / zs[i] * f
            if (z.contains(Offset(x, y))) cands += i
        }
        val (fx, fy) = if (cands.isNotEmpty()) {
            val i = cands[rnd.nextInt(cands.size)]
            val p = (w * .5f + xs[i] / zs[i] * f) / w to (h * .5f + ys[i] / zs[i] * f) / h
            spawn(i, false); p
        } else {
            (z.left + rnd.nextFloat() * z.width) / w to (z.top + rnd.nextFloat() * z.height) / h
        }
        beacon = Beacon(fx, fy, if (flare) 0f else 10f, rnd.nextFloat() * 10)
        if (flare) { flash = 0f; onFlare() }
    }

    fun step(dt: Float) {
        // фазы полёта
        val accelD = warpDur * .38f
        val cruiseMin = warpDur * .28f
        val decelD = warpDur * .34f
        pt += dt
        intensity = when (phase) {
            Phase.IDLE -> 0f
            Phase.ACCEL -> {
                if (pt >= accelD) { phase = Phase.CRUISE; pt = 0f; 1f } else smooth(pt / accelD)
            }
            Phase.CRUISE -> {
                // если Mac просыпается долго, полёт становится спокойнее
                val i = if (pt < 1.5f) 1f else max(.55f, 1f - (pt - 1.5f) * .15f)
                if (result != null && pt >= cruiseMin) { phase = Phase.DECEL; pt = 0f; decelFrom = i }
                i
            }
            Phase.DECEL -> {
                if (pt >= decelD) {
                    phase = Phase.IDLE
                    if (result == true) placeBeacon(flare = true)
                    0f
                } else decelFrom * (1 - smooth(pt / decelD))
            }
        }
        if (reduceMotion) intensity *= .12f

        val v = drift * (if (reduceMotion) .006f else .018f) + intensity * 1.9f
        for (i in 0 until n) {
            zs[i] -= v * dt
            tw[i] += tws[i] * dt
            if (zs[i] <= .03f) spawn(i, false)
        }
        beacon?.let { b ->
            b.t += dt
            if (b.fading) { b.ft += dt; if (b.ft >= 1.8f) beacon = null }
        }
        if (flash >= 0f) { flash += dt; if (flash > .9f) flash = -1f }
    }

    fun draw(ds: DrawScope) = with(ds) {
        resize(size.width, size.height)
        ensureCount()
        val dp = density
        val cx = w * .5f; val cy = h * .5f
        val hu = hue.coerceIn(0f, 360f); val l = light / 100f
        drawRect(
            Brush.radialGradient(
                0f to Color.hsl(hu, .62f, l.coerceIn(0f, 1f)),
                .55f to Color.hsl(hu, .68f, (l * .55f).coerceIn(0f, 1f)),
                1f to Color.hsl(hu, .75f, (l * .28f).coerceIn(0f, 1f)),
                center = Offset(cx, h * .32f), radius = hypot(w, h) * .75f,
            )
        )
        val ii = intensity
        if (ii > .01f) {
            drawRect(Brush.radialGradient(listOf(Color(0xFF8CA5FF).copy(alpha = .09f * ii), Color.Transparent),
                center = Offset(cx, cy), radius = max(w, h) * .6f))
        }
        val v = drift * .018f + ii * 1.9f
        val ff = f * (1 + .06f * ii)
        for (i in 0 until n) {
            val z = zs[i]
            val x = cx + xs[i] / z * ff
            val y = cy + ys[i] / z * ff
            if (x < -60 * dp || x > w + 60 * dp || y < -60 * dp || y > h + 60 * dp) { spawn(i, false); continue }
            val k = 1 - z
            val r = sz[i] * (.3f + 1.9f * k * k) * dp
            val al = min(1f, (.18f + .82f * k) * (.82f + .18f * sin(tw[i])))
            val col = if (warm[i]) Color(1f, .91f, .78f, al) else Color(.86f, .9f, 1f, al)
            if (ii > .06f) {
                val zp = min(1.2f, z + v * .055f)
                drawLine(col, Offset(cx + xs[i] / zp * ff, cy + ys[i] / zp * ff), Offset(x, y),
                    strokeWidth = max(.6f * dp, r), cap = StrokeCap.Round)
            } else {
                drawCircle(col, r, Offset(x, y))
                if (k > .75f) drawCircle(col.copy(alpha = al * .12f), r * 3, Offset(x, y))
            }
        }
        beacon?.let { drawBeacon(this, it) }
        if (flash >= 0f) drawRect(Color(1f, .93f, .75f, .075f * (1 - flash / .9f)))
    }

    private fun drawBeacon(ds: DrawScope, b: Beacon) = with(ds) {
        val t = b.t
        val dp = density
        val s = flareSize * dp
        val peak = 92 * s
        val rest = 30 * s
        var r = when {
            t < .55f -> 3 + (peak - 3) * outCubic(t / .55f)
            t < 1.9f -> peak + (rest - peak) * inOut((t - .55f) / 1.35f)
            else -> rest * (1 + .07f * sin((t - 1.9f) * 1.7f + b.seed) + .025f * sin(t * 7.3f))
        }
        var a = 1f
        var spikeA = 1f
        if (b.fading) {
            val ft = min(b.ft / 1.8f, 1f)
            val k = 1 - inOut(ft)
            r *= .15f + .85f * k; a = k; spikeA = max(0f, 1 - ft * 1.8f)
        }
        val c = Offset(b.fx * w, b.fy * h)
        drawCircle(
            Brush.radialGradient(
                0f to Color(1f, .94f, .73f, .5f * a), .18f to Color(1f, .89f, .59f, .22f * a),
                .5f to Color(1f, .8f, .47f, .06f * a), 1f to Color.Transparent,
                center = c, radius = r * 2.6f,
            ), radius = r * 2.6f, center = c, blendMode = BlendMode.Plus,
        )
        val cr = max(2f, r * .32f)
        drawCircle(
            Brush.radialGradient(
                0f to Color(1f, 1f, .97f, a), .45f to Color(1f, .96f, .8f, .85f * a), 1f to Color.Transparent,
                center = c, radius = cr,
            ), radius = cr, center = c, blendMode = BlendMode.Plus,
        )
        val sa = a * spikeA
        if (sa > .01f) {
            val len = r * 2.3f
            fun spike(dx: Float, dy: Float, l: Float, wd: Float, al: Float) {
                val p1 = Offset(c.x - dx * l, c.y - dy * l); val p2 = Offset(c.x + dx * l, c.y + dy * l)
                drawLine(
                    Brush.linearGradient(0f to Color.Transparent, .5f to Color(1f, .97f, .86f, al), 1f to Color.Transparent, start = p1, end = p2),
                    p1, p2, strokeWidth = wd, blendMode = BlendMode.Plus,
                )
            }
            spike(1f, 0f, len, 1.4f * dp, .75f * sa)
            spike(0f, 1f, len, 1.4f * dp, .75f * sa)
            spike(.7071f, .7071f, len * .45f, 1f * dp, .3f * sa)
            spike(.7071f, -.7071f, len * .45f, 1f * dp, .3f * sa)
        }
    }
}

/**
 * Холст со звёздами. Каждый кадр записывается в [backdrop], чтобы стеклянные
 * кнопки могли размывать именно то, что под ними.
 */
@Composable
fun StarCanvas(sky: StarSky, backdrop: GraphicsLayer, tick: MutableLongState, running: Boolean, modifier: Modifier = Modifier) {
    // когда звёзды закрыты экраном настроек, анимация стоит и не тратит батарею
    LaunchedEffect(sky, running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min((now - last) / 1e9f, .05f)
                last = now
                sky.step(dt)
                tick.longValue = now
            }
        }
    }
    Canvas(
        modifier.drawWithContent {
            backdrop.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop)
        }
    ) {
        tick.longValue
        sky.draw(this)
    }
}

@Composable
fun rememberTick(): MutableLongState = remember { mutableLongStateOf(0L) }
