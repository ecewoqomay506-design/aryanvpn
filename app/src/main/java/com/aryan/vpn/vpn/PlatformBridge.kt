package com.aryan.vpn.vpn

import io.nekohasekai.libbox.*

/**
 * Minimal libbox PlatformInterface (targets libbox 1.9.x). For other versions, mirror
 * PlatformInterfaceWrapper.kt from the matching sing-box-for-android tag.
 */
class PlatformBridge(private val svc: AryanVpnService) : PlatformInterface {
    override fun usePlatformAutoDetectInterfaceControl() = true
    override fun autoDetectInterfaceControl(fd: Int) { svc.protect(fd) }
    override fun openTun(options: TunOptions): Int = svc.openTun(options)
    override fun useProcFS() = false
    override fun findConnectionOwner(ipProtocol: Int, sourceAddress: String, sourcePort: Int, destinationAddress: String, destinationPort: Int) = -1
    override fun packageNameByUid(uid: Int) = ""
    override fun uidByPackageName(packageName: String) = -1
    override fun usePlatformDefaultInterfaceMonitor() = false
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}
    override fun usePlatformInterfaceGetter() = false
    override fun getInterfaces(): NetworkInterfaceIterator = error("unused")
    override fun underNetworkExtension() = false
    override fun includeAllNetworks() = false
    override fun clearDNSCache() {}
    override fun readWIFIState(): WIFIState? = null
    override fun writeLog(message: String) { /* intentionally dropped: may contain endpoints */ }
}

/** For the temporary proxy-latency instance: no tun, no socket protection needed. */
class PlatformBridgeNoTun : PlatformInterface {
    override fun usePlatformAutoDetectInterfaceControl() = false
    override fun autoDetectInterfaceControl(fd: Int) {}
    override fun openTun(options: TunOptions): Int = error("no tun in probe mode")
    override fun useProcFS() = false
    override fun findConnectionOwner(ipProtocol: Int, sourceAddress: String, sourcePort: Int, destinationAddress: String, destinationPort: Int) = -1
    override fun packageNameByUid(uid: Int) = ""
    override fun uidByPackageName(packageName: String) = -1
    override fun usePlatformDefaultInterfaceMonitor() = true
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}
    override fun usePlatformInterfaceGetter() = false
    override fun getInterfaces(): NetworkInterfaceIterator = error("unused")
    override fun underNetworkExtension() = false
    override fun includeAllNetworks() = false
    override fun clearDNSCache() {}
    override fun readWIFIState(): WIFIState? = null
    override fun writeLog(message: String) {}
}
