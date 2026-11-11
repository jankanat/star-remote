package kz.alnazar.starremote.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress

/** Сведения о домашней Wi-Fi сети телефона. */
object Net {
    /**
     * Сеть Wi-Fi, даже если Android считает основной мобильную связь
     * (например, когда у роутера пропал интернет). К ней привязываются все запросы к Mac.
     */
    fun wifi(ctx: Context): Network? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val active = cm.activeNetwork
        if (active != null && cm.getNetworkCapabilities(active)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            return active
        }
        @Suppress("DEPRECATION")
        return cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    /** Широковещательные адреса подсети Wi-Fi, например 192.168.1.255. */
    fun broadcasts(ctx: Context): List<InetAddress> {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val net = wifi(ctx) ?: return emptyList()
        val lp = cm.getLinkProperties(net) ?: return emptyList()
        return lp.linkAddresses.mapNotNull { la ->
            val a = la.address as? Inet4Address ?: return@mapNotNull null
            val prefix = la.prefixLength
            val ip = a.address.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
            val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            val bc = (ip and mask) or (mask.inv() and 0xFFFFFFFFL)
            InetAddress.getByAddress(byteArrayOf((bc shr 24).toByte(), (bc shr 16).toByte(), (bc shr 8).toByte(), bc.toByte()))
        }
    }

    fun onWifi(ctx: Context): Boolean = wifi(ctx) != null
}
