package kz.alnazar.starremote.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CustomButton(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val shortcut: String,
)

/** Все настройки приложения. Значения по умолчанию взяты из согласованного макета. */
data class AppSettings(
    // подключение
    val ip: String = "",
    val mac: String = "",
    val agentPort: Int = 47823,
    val token: String = "",
    // кнопки
    val onShortcut: String = "Запустить TorrServer",
    val offShortcut: String = "Отключить Torrserver",
    val custom: List<CustomButton> = emptyList(),
    // пробуждение и таймер
    val wolRetries: Int = 5,
    val wakeWaitSec: Int = 60,
    val autoOffHours: Int = 4,
    val macGuard: Boolean = true,
    // TorrServe
    val torrPort: Int = 8090,
    val torrCheck: Boolean = true,
    val torrInApp: Boolean = false,
    // виджет
    val widgetHue: Float = 236f,
    val widgetSat: Float = 55f,
    val widgetLight: Float = 28f,
    val widgetTransparency: Float = 50f,
    val widgetWide: Boolean = true,
    // космос
    val density: Int = 800,
    val drift: Float = 0.7f,
    val warp: Float = 2.4f,
    val flare: Float = 0.8f,
    val spaceHue: Float = 251f,
    val spaceLight: Float = 7.5f,
    // отклик
    val sound: Boolean = true,
    val volume: Int = 45,
    val haptic: Boolean = true,
    val hapticStrong: Boolean = false,
) {
    val configured: Boolean get() = (ip.isNotBlank() || mac.isNotBlank()) && token.isNotBlank()
}

/**
 * Хранилище настроек. Обычные значения лежат в SharedPreferences как JSON,
 * ключ доступа к службе на Mac лежит отдельно в зашифрованном хранилище.
 */
class SettingsRepo(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secure: SharedPreferences = try {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        ctx.getSharedPreferences("secure_fallback", Context.MODE_PRIVATE)
    }

    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<AppSettings> = _flow.asStateFlow()
    val value: AppSettings get() = _flow.value

    @Synchronized
    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_flow.value)
        if (next == _flow.value) return
        _flow.value = next
        save(next)
    }

    fun resetSpace() = update {
        val d = AppSettings()
        it.copy(density = d.density, drift = d.drift, warp = d.warp, flare = d.flare,
            spaceHue = d.spaceHue, spaceLight = d.spaceLight)
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        val token = secure.getString("token", "") ?: ""
        val raw = prefs.getString("json", null) ?: return d.copy(token = token)
        return try {
            val j = JSONObject(raw)
            val custom = j.optJSONArray("custom")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    CustomButton(o.optString("id"), o.optString("name"), o.optString("shortcut"))
                }
            } ?: d.custom
            AppSettings(
                ip = j.optString("ip", d.ip),
                mac = j.optString("mac", d.mac),
                agentPort = j.optInt("agentPort", d.agentPort),
                token = token,
                onShortcut = j.optString("onShortcut", d.onShortcut),
                offShortcut = j.optString("offShortcut", d.offShortcut),
                custom = custom,
                wolRetries = j.optInt("wolRetries", d.wolRetries),
                wakeWaitSec = j.optInt("wakeWaitSec", d.wakeWaitSec),
                autoOffHours = j.optInt("autoOffHours", d.autoOffHours),
                macGuard = j.optBoolean("macGuard", d.macGuard),
                torrPort = j.optInt("torrPort", d.torrPort),
                torrCheck = j.optBoolean("torrCheck", d.torrCheck),
                torrInApp = j.optBoolean("torrInApp", d.torrInApp),
                widgetHue = j.optF("widgetHue", d.widgetHue),
                widgetSat = j.optF("widgetSat", d.widgetSat),
                widgetLight = j.optF("widgetLight", d.widgetLight),
                widgetTransparency = j.optF("widgetTransparency", d.widgetTransparency),
                widgetWide = j.optBoolean("widgetWide", d.widgetWide),
                density = j.optInt("density", d.density),
                drift = j.optF("drift", d.drift),
                warp = j.optF("warp", d.warp),
                flare = j.optF("flare", d.flare),
                spaceHue = j.optF("spaceHue", d.spaceHue),
                spaceLight = j.optF("spaceLight", d.spaceLight),
                sound = j.optBoolean("sound", d.sound),
                volume = j.optInt("volume", d.volume),
                haptic = j.optBoolean("haptic", d.haptic),
                hapticStrong = j.optBoolean("hapticStrong", d.hapticStrong),
            )
        } catch (e: Exception) {
            d.copy(token = token)
        }
    }

    private fun save(s: AppSettings) {
        val j = JSONObject().apply {
            put("ip", s.ip); put("mac", s.mac); put("agentPort", s.agentPort)
            put("onShortcut", s.onShortcut); put("offShortcut", s.offShortcut)
            put("custom", JSONArray().apply {
                s.custom.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("shortcut", it.shortcut)) }
            })
            put("wolRetries", s.wolRetries); put("wakeWaitSec", s.wakeWaitSec)
            put("autoOffHours", s.autoOffHours); put("macGuard", s.macGuard)
            put("torrPort", s.torrPort); put("torrCheck", s.torrCheck); put("torrInApp", s.torrInApp)
            put("widgetHue", s.widgetHue.toDouble()); put("widgetSat", s.widgetSat.toDouble())
            put("widgetLight", s.widgetLight.toDouble()); put("widgetTransparency", s.widgetTransparency.toDouble())
            put("widgetWide", s.widgetWide)
            put("density", s.density); put("drift", s.drift.toDouble()); put("warp", s.warp.toDouble())
            put("flare", s.flare.toDouble()); put("spaceHue", s.spaceHue.toDouble()); put("spaceLight", s.spaceLight.toDouble())
            put("sound", s.sound); put("volume", s.volume); put("haptic", s.haptic); put("hapticStrong", s.hapticStrong)
        }
        prefs.edit().putString("json", j.toString()).apply()
        secure.edit().putString("token", s.token).apply()
    }

    private fun JSONObject.optF(k: String, def: Float): Float =
        if (has(k)) optDouble(k, def.toDouble()).toFloat() else def
}
