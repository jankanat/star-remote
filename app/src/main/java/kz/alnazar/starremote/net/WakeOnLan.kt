package kz.alnazar.starremote.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Сигнал пробуждения (magic packet): 6 байт 0xFF и 16 раз MAC-адрес. */
object WakeOnLan {

    fun parseMac(s: String): ByteArray? {
        val hex = s.trim().replace(":", "").replace("-", "").replace(".", "").replace(" ", "")
        if (hex.length != 12 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return ByteArray(6) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    fun isValidMac(s: String) = parseMac(s) != null

    /** Отправляет пакет на все подходящие адреса. Возвращает число отправленных пакетов. */
    suspend fun send(ctx: Context, mac: String, ip: String?): Int = withContext(Dispatchers.IO) {
        val macBytes = parseMac(mac) ?: throw IllegalArgumentException("MAC-адрес указан неверно")
        val payload = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) payload[i] = 0xFF.toByte()
        for (r in 0 until 16) System.arraycopy(macBytes, 0, payload, 6 + r * 6, 6)

        val targets = LinkedHashSet<InetAddress>()
        targets += Net.broadcasts(ctx)
        targets += InetAddress.getByName("255.255.255.255")
        if (!ip.isNullOrBlank()) runCatching { targets += InetAddress.getByName(ip.trim()) }

        var sent = 0
        DatagramSocket().use { sock ->
            sock.broadcast = true
            Net.wifi(ctx)?.let { runCatching { it.bindSocket(sock) } }
            for (t in targets) for (port in intArrayOf(9, 7)) {
                runCatching { sock.send(DatagramPacket(payload, payload.size, t, port)); sent++ }
            }
        }
        sent
    }
}
