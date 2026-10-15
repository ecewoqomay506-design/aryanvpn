package com.aryan.vpn.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import io.nekohasekai.libbox.*
import java.net.NetworkInterface as JavaNetworkInterface

/**
 * PlatformInterface for sing-box libbox 1.9.7 (pinned).
 * Fixed: usePlatformDefaultInterfaceMonitor = true + real Android NetworkCallback
 * to avoid "netlink socket in Android is banned by Google".
 */
class PlatformBridge(
    private val svc: AryanVpnService
) : PlatformInterface {

    private val connectivity: ConnectivityManager =
        svc.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentListener: InterfaceUpdateListener? = null

    override fun usePlatformAutoDetectInterfaceControl() = true

    override fun autoDetectInterfaceControl(fd: Int) {
        svc.protect(fd)
    }

    override fun openTun(options: TunOptions): Int =
        svc.openTun(options)

    override fun useProcFS() = false

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ) = -1

    override fun packageNameByUid(uid: Int) = ""

    override fun uidByPackageName(packageName: String) = -1

    // ─── Critical fix for netlink error ───────────────────────────────────────
    override fun usePlatformDefaultInterfaceMonitor() = true

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        // Avoid duplicate registration
        stopMonitorInternal()

        currentListener = listener

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                notifyDefaultInterface(network)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                notifyDefaultInterface(network)
            }

            override fun onLost(network: Network) {
                // No default network
                try {
                    listener.updateDefaultInterface("", -1)
                } catch (_: Throwable) {
                }
            }
        }

        networkCallback = callback

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivity.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivity.registerNetworkCallback(request, callback)
            }
        } catch (e: Exception) {
            // Fallback: still keep listener, but without live updates
            networkCallback = null
        }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        stopMonitorInternal()
    }

    private fun stopMonitorInternal() {
        networkCallback?.let {
            try {
                connectivity.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        networkCallback = null
        currentListener = null
    }

    private fun notifyDefaultInterface(network: Network) {
        val listener = currentListener ?: return
        try {
            val link = connectivity.getLinkProperties(network) ?: return
            val name = link.interfaceName ?: return

            var index = -1
            try {
                val iface = JavaNetworkInterface.getByName(name)
                if (iface != null) {
                    index = iface.index
                }
            } catch (_: Exception) {
            }

            listener.updateDefaultInterface(name, index)
        } catch (_: Throwable) {
        }
    }
    // ─────────────────────────────────────────────────────────────────────────

    override fun usePlatformInterfaceGetter() = false

    override fun getInterfaces(): NetworkInterfaceIterator =
        error("Platform interface getter is disabled")

    override fun underNetworkExtension() = false

    override fun includeAllNetworks() = false

    override fun clearDNSCache() {}

    override fun readWIFIState(): WIFIState? = null

    override fun writeLog(message: String) {
        // Avoid logging potentially sensitive connection details.
    }
}

/**
 * PlatformInterface for temporary proxy-latency checks.
 * Unchanged – still disables TUN.
 */
class PlatformBridgeNoTun : PlatformInterface {

    override fun usePlatformAutoDetectInterfaceControl() = false

    override fun autoDetectInterfaceControl(fd: Int) {}

    override fun openTun(options: TunOptions): Int =
        error("TUN is unavailable in probe mode")

    override fun useProcFS() = false

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ) = -1

    override fun packageNameByUid(uid: Int) = ""

    override fun uidByPackageName(packageName: String) = -1

    override fun usePlatformDefaultInterfaceMonitor() = false

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

    override fun usePlatformInterfaceGetter() = false

    override fun getInterfaces(): NetworkInterfaceIterator =
        error("Platform interface getter is disabled")

    override fun underNetworkExtension() = false

    override fun includeAllNetworks() = false

    override fun clearDNSCache() {}

    override fun readWIFIState(): WIFIState? = null

    override fun writeLog(message: String) {}
}
