package kz.alnazar.starremote.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Level(val tag: String) { INFO("INFO"), OK("OK"), WARN("WARN"), ERR("ERR") }

data class LogEntry(val time: String, val level: Level, val msg: String) {
    fun line() = "$time ${level.tag.padEnd(4)} $msg"
}

/**
 * Журнал действий. Хранит последние записи в файле, чтобы их можно было
 * скопировать и прислать для разбора. Ключ доступа сюда никогда не пишется.
 */
class Journal(ctx: Context) {
    private val file = File(ctx.filesDir, "journal.txt")
    private val fmt = SimpleDateFormat("dd.MM HH:mm:ss.SSS", Locale.US)
    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    @Synchronized
    fun add(level: Level, msg: String) {
        val e = LogEntry(fmt.format(Date()), level, msg)
        val next = (_entries.value + e).takeLast(MAX)
        _entries.value = next
        try {
            if (next.size == MAX) file.writeText(next.joinToString("\n") { it.line() } + "\n")
            else file.appendText(e.line() + "\n")
        } catch (_: Exception) {}
    }

    fun info(m: String) = add(Level.INFO, m)
    fun ok(m: String) = add(Level.OK, m)
    fun warn(m: String) = add(Level.WARN, m)
    fun err(m: String) = add(Level.ERR, m)

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        try { file.delete() } catch (_: Exception) {}
    }

    fun export(appVersion: String): String = buildString {
        appendLine("Звёздный пульт $appVersion · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        _entries.value.forEach { appendLine(it.line()) }
    }

    private fun read(): List<LogEntry> = try {
        if (!file.exists()) emptyList() else file.readLines().takeLast(MAX).mapNotNull { parse(it) }
    } catch (_: Exception) { emptyList() }

    private fun parse(line: String): LogEntry? {
        // "dd.MM HH:mm:ss.SSS LVL  сообщение"
        if (line.length < 24) return null
        val time = line.substring(0, 18)
        val rest = line.substring(19)
        val tag = rest.substring(0, 4).trim()
        val level = Level.values().firstOrNull { it.tag == tag } ?: Level.INFO
        return LogEntry(time, level, rest.drop(5))
    }

    private companion object { const val MAX = 500 }
}
