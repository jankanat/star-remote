package kz.alnazar.starremote.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

data class MacStatus(val caffeinate: Boolean, val torrProc: Boolean, val torrserve: Boolean, val guard: Boolean, val uptime: String)
data class RunResult(val ok: Boolean, val code: Int, val sec: Int, val output: String)

class AgentException(message: String) : IOException(message)

/** Клиент службы на Mac (mac/agent.sh). Все запросы идут через домашний Wi-Fi. */
class Agent(private val ctx: Context, private val host: String, private val port: Int, private val token: String) {

    /** Быстрая проверка: проснулся ли Mac. Ключ не нужен. */
    suspend fun ping(timeoutMs: Int = 900): String? = try {
        val j = request("GET", "/ping", timeoutMs, timeoutMs, auth = false)
        if (j.optString("app") == "starremote") j.optString("host", "Mac") else null
    } catch (_: Exception) { null }

    /** Причина, по которой /ping не прошёл (для журнала). */
    suspend fun pingError(timeoutMs: Int = 2000): String? = try {
        request("GET", "/ping", timeoutMs, timeoutMs, auth = false); null
    } catch (e: Exception) { e.message ?: e.javaClass.simpleName }

    suspend fun status(): MacStatus {
        val j = request("GET", "/status", 2500, 4000)
        return MacStatus(j.optBoolean("caffeinate"), j.optBoolean("torrProc"), j.optBoolean("torrserve"), j.optBoolean("guard"), j.optString("uptime"))
    }

    suspend fun run(shortcut: String): RunResult {
        val j = request("POST", "/run?name=" + enc(shortcut), 3000, 130_000)
        return RunResult(j.optBoolean("ok"), j.optInt("code", -1), j.optInt("sec"), j.optString("output"))
    }

    /** Страховка на Mac: через [seconds] секунд он сам запустит шорткат выключения. */
    suspend fun guard(seconds: Int, offShortcut: String) {
        request("POST", "/guard?secs=$seconds&name=" + enc(offShortcut), 3000, 5000)
    }
    suspend fun guardStop() { request("POST", "/guard/stop", 3000, 5000) }
    suspend fun sleep() { request("POST", "/sleep", 3000, 5000) }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private suspend fun request(method: String, path: String, connectMs: Int, readMs: Int, auth: Boolean = true): JSONObject =
        withContext(Dispatchers.IO) {
            val url = URL("http", host, port, path)
            val wifi = Net.wifi(ctx)
            val c = (wifi?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
            try {
                c.requestMethod = method
                c.connectTimeout = connectMs
                c.readTimeout = readMs
                c.useCaches = false
                c.setRequestProperty("Connection", "close")
                if (auth) c.setRequestProperty("X-Token", token)
                if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
                val code = c.responseCode
                val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                when (code) {
                    200 -> JSONObject(body.ifBlank { "{}" })
                    401 -> throw AgentException("Неверный ключ доступа. Проверь его в настройках подключения")
                    404 -> throw AgentException("Служба на Mac устарела, переустанови её")
                    else -> throw AgentException("Mac ответил ошибкой $code")
                }
            } catch (e: SocketTimeoutException) {
                throw AgentException("Mac не ответил вовремя")
            } catch (e: AgentException) {
                throw e
            } catch (e: IOException) {
                throw AgentException("Нет связи с Mac (${e.javaClass.simpleName}: ${e.message ?: "—"})")
            } finally {
                c.disconnect()
            }
        }
}
