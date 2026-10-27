package com.jktv.offline.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CastDevice(val name: String, val host: String, val port: Int)

/** Serial resolution also works with older Android NSD implementations. */
class CastDiscovery(context: Context) {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val mutableDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices = mutableDevices.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private var discovery: NsdManager.DiscoveryListener? = null
    private var registration: NsdManager.RegistrationListener? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private val present = mutableSetOf<String>()
    private var resolving = false
    private var generation = 0

    fun search() {
        stopSearch()
        mutableError.value = null
        val epoch = generation
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { main.post {
                if (epoch == generation) { mutableError.value = "TV discovery failed ($code). Retry or enter the TV address."; stopSearch() }
            } }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) { main.post {
                if (epoch == generation && present.add(info.serviceName)) { pending.addLast(info); resolveNext(epoch) }
            } }
            override fun onServiceLost(info: NsdServiceInfo) { main.post {
                if (epoch == generation) {
                    present.remove(info.serviceName)
                    mutableDevices.value = mutableDevices.value.filterNot { it.name == info.serviceName }
                }
            } }
        }
        discovery = listener
        runCatching { manager.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { mutableError.value = it.message; stopSearch() }
    }

    @Suppress("DEPRECATION")
    private fun resolveNext(epoch: Int) {
        if (resolving || pending.isEmpty() || epoch != generation) return
        resolving = true
        val next = pending.removeFirst()
        runCatching { manager.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) { main.post {
                if (epoch == generation) { resolving = false; resolveNext(epoch) }
            } }
            override fun onServiceResolved(info: NsdServiceInfo) { main.post {
                if (epoch == generation) {
                    resolving = false
                    val host = info.host?.hostAddress
                    if (host != null && info.serviceName in present) {
                        mutableDevices.value = mutableDevices.value.filterNot { it.name == info.serviceName } +
                            CastDevice(info.serviceName, host, info.port)
                    }
                    resolveNext(epoch)
                }
            } }
        }) }.onFailure { resolving = false; resolveNext(epoch) }
    }

    fun advertise(port: Int) {
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { mutableError.value = "Discovery unavailable ($code); use the TV address." }
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        registration = listener
        runCatching { manager.registerService(NsdServiceInfo().apply {
            serviceName = "JKTV ${android.os.Build.MODEL}"
            serviceType = TYPE
            this.port = port
            setAttribute("protocol", "1")
        }, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { mutableError.value = it.message }
    }

    fun stopSearch() {
        generation++
        discovery?.let { runCatching { manager.stopServiceDiscovery(it) } }
        discovery = null
        pending.clear(); present.clear(); resolving = false
        mutableDevices.value = emptyList()
    }
    fun close() {
        stopSearch()
        registration?.let { runCatching { manager.unregisterService(it) } }
        registration = null
    }
    companion object { const val TYPE = "_jktv-stage._tcp." }
}
