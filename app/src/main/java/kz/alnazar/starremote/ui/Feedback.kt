package kz.alnazar.starremote.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** Мягкая вибрация. */
object Haptics {
    private fun vibrator(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)

    /** Короткий отклик на нажатие. */
    fun tap(ctx: Context, enabled: Boolean, strong: Boolean) {
        if (!enabled) return
        val v = vibrator(ctx) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                v.vibrate(VibrationEffect.createPredefined(if (strong) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK))
            } else {
                v.vibrate(VibrationEffect.createOneShot(if (strong) 20 else 12, if (strong) 160 else 70))
            }
        }
    }

    /** Чуть заметнее, в момент вспышки звезды. */
    fun flare(ctx: Context, enabled: Boolean, strong: Boolean) {
        if (!enabled) return
        val v = vibrator(ctx) ?: return
        runCatching {
            val amp = if (v.hasAmplitudeControl()) (if (strong) 150 else 80) else VibrationEffect.DEFAULT_AMPLITUDE
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 16, 60, 26), intArrayOf(0, amp, 0, amp / 2), -1))
        }
    }
}

/**
 * Звук вспышки: мягкий аккорд с медленной атакой и долгим затуханием,
 * синтезируется на лету, без файлов.
 */
object Chime {
    private const val SR = 44100
    private const val DUR = 7.5
    @Volatile private var pcm: ShortArray? = null
    private var track: AudioTrack? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun prepare() { scope.launch { synth() } }

    fun play(volume: Int) {
        if (volume <= 0) return
        scope.launch {
            val data = synth()
            runCatching {
                val t = track ?: AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(data.size * 2)
                    .build().also { it.write(data, 0, data.size); track = it }
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
                t.reloadStaticData()
                t.setVolume((volume / 100f).pow(1.5f))
                t.play()
            }
        }
    }

    @Synchronized
    private fun synth(): ShortArray {
        pcm?.let { return it }
        val n = (SR * DUR).toInt()
        val out = FloatArray(n)
        // D4, A4, C#5, E5 — каждая нота двумя чуть расстроенными синусами
        val notes = doubleArrayOf(293.66, 440.0, 554.37, 659.25)
        val gains = doubleArrayOf(.9, .7, .45, .3)
        val cents = doubleArrayOf(-5.0, 5.0)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            var s = 0.0
            for (k in notes.indices) for (c in cents) {
                val f = notes[k] * 2.0.pow(c / 1200.0)
                s += sin(2 * PI * f * t) * gains[k] * .5
            }
            val env = when {
                t < .9 -> t / .9
                t < 1.2 -> 1.0
                else -> exp(-(t - 1.2) / 1.15)
            }
            val shEnv = when {
                t < 1.6 -> .08 * t / 1.6
                t < 1.8 -> .08
                else -> .08 * exp(-(t - 1.8) / .9)
            }
            s = s * env + sin(2 * PI * 1318.5 * t) * shEnv
            out[i] = s.toFloat()
        }
        // мягкий фильтр низких частот
        val a = exp(-2 * PI * 1500 / SR).toFloat()
        var y = 0f
        for (i in 0 until n) { y = (1 - a) * out[i] + a * y; out[i] = y }
        // лёгкое эхо
        val d = (.27 * SR).toInt()
        val line = FloatArray(d)
        var p = 0
        for (i in 0 until n) {
            val delayed = line[p]
            line[p] = out[i] + .32f * delayed
            out[i] = out[i] + .45f * delayed
            p = (p + 1) % d
        }
        // нормировка и плавный хвост без щелчка
        var peak = 1e-6f
        for (v in out) peak = maxOf(peak, abs(v))
        val g = .55f / peak
        val fadeN = (SR * .3).toInt()
        val res = ShortArray(n) { i ->
            val fade = if (i > n - fadeN) (n - i).toFloat() / fadeN else 1f
            (out[i] * g * fade * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        pcm = res
        return res
    }
}
