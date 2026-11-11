package kz.alnazar.starremote.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

data class Found(val name: String, val host: String, val port: Int)

/**
 * Поиск Mac через Bonjour: служба на Mac объявляет себя как _starremote._tcp,
 * телефон находит её и узнаёт IP и порт без ручного ввода.
 */
object Discovery {
    private const val TYPE = "_starremote._tcp."
    @Volatile var lastError: String? = null

    suspend fun find(ctx: Context, timeoutMs: Long = 6000): Found? {
        val nsd = ctx.getSystemService(NsdManager::class.java) ?: return null
        val wifi = ctx.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("starremote")?.apply { setReferenceCounted(false); runCatching { acquire() } }
        lastError = null
        val result = CompletableDeferred<Found?>()
        var resolving = false

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                lastError = "поиск не запустился, код $errorCode"; result.complete(null)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (resolving || result.isCompleted) return
                resolving = true
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                        lastError = "найден, но адрес не получен, код $errorCode"; resolving = false
                    }
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val addr = si.host?.hostAddress
                        if (addr != null && !addr.contains(':')) result.complete(Found(si.serviceName, addr, si.port))
                        else resolving = false
                    }
                })
            }
        }

        return try {
            nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            withTimeoutOrNull(timeoutMs) { result.await() }
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            null
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
            runCatching { lock?.release() }
        }
    }
}
