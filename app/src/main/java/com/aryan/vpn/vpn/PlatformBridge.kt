package com.aryan.vpn.vpn

import io.nekohasekai.libbox.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

/** PlatformInterface for the pinned sing-box libbox 1.9.7 engine. */
class PlatformBridge(
    private val svc: AryanVpnService
) : PlatformInterface {

    override fun usePlatformAutoDetectInterfaceControl() = true

    override fun autoDetectInterfaceControl(fd: Int) {
        svc.protect(fd)
    }

    /**
     * TunOptions and its iterators are Go-backed. Reading them on the original
     * Go-to-Java callback stack re-enters Go and can abort affected runtimes.
     * Use a separate Java thread, and keep the callback alive until it finishes
     * so the Go-backed options remain valid throughout VPN establishment.
     */
    override fun openTun(options: TunOptions): Int {
        val task = FutureTask<Int> { svc.openTun(options) }
        Thread(task, "libbox-tun-setup").start()
        var interrupted = false
        try {
            while (true) {
                try {
                    return task.get()
                } catch (_: InterruptedException) {
                    // Do not return while the worker still uses Go-backed options.
                    interrupted = true
                } catch (e: ExecutionException) {
                    throw (e.cause ?: e)
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

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

    // Avoid the Android netlink monitor, which is restricted for normal apps.
    override fun usePlatformDefaultInterfaceMonitor() = true

    // Network-change monitoring is not implemented by this bridge yet.
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

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

/** PlatformInterface for temporary proxy-latency checks. */
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
