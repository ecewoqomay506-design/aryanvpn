package com.aryan.vpn.vpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.aryan.vpn.AryanApp
import com.aryan.vpn.MainActivity
import com.aryan.vpn.R
import com.aryan.vpn.core.ConfigBuilder
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.TunOptions
import kotlinx.coroutines.*

/**
 * Real tunnel built on sing-box libbox, modelled after SagerNet/sing-box-for-android (GPLv3).
 * IMPORTANT: libbox's PlatformInterface changes between releases. PlatformBridge must implement
 * exactly the interface in the libbox.aar you compiled; the compiler will flag any mismatch.
 */
class AryanVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var box: BoxService? = null
    private var tunFd: ParcelFileDescriptor? = null
    private val lock = Any()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { scope.launch { stopEngine(); stopSelf() } }
            else -> { startForeground(1, notification()); scope.launch { startEngine() } }
        }
        return START_NOT_STICKY
    }

    private fun startEngine() = synchronized(lock) {
        if (box != null) return  // prevent duplicate engine instances
        val app = application as AryanApp
        val profile = app.profiles.selected() ?: run { fail(IllegalStateException("No profile selected")); return }
        VpnState.state.value = if (VpnState.reconnectAttempts.value > 0) ConnState.RECONNECTING else ConnState.CONNECTING
        try {
            val cfg = ConfigBuilder.build(profile, app.dns.servers())
            Libbox.checkConfig(cfg)                      // validate before connecting
            val svc = Libbox.newService(cfg, PlatformBridge(this))
            svc.start()
            box = svc
            VpnState.engineResult.value = "Started"
            // Don't claim Connected until a real request through the tunnel succeeds.
            val rtt = runBlocking { app.latency.tunnelProbe() }
            if (rtt == null) throw IllegalStateException("Tunnel verification timeout")
            VpnState.state.value = ConnState.CONNECTED
            VpnState.connectedSince.value = System.currentTimeMillis()
            VpnState.lastError.value = null
            VpnState.reconnectAttempts.value = 0
        } catch (e: Throwable) { closeQuietly(); fail(e) }
    }

    private fun fail(e: Throwable) {
        VpnState.engineResult.value = "Failed"
        VpnState.lastError.value = ErrorExplainer.explain(e)
        VpnState.state.value = ConnState.ERROR
        VpnState.connectedSince.value = null
        (application as AryanApp).reconnect.onFailure()
    }

    fun stopEngine() = synchronized(lock) {
        VpnState.state.value = ConnState.DISCONNECTING
        closeQuietly()
        VpnState.connectedSince.value = null
        VpnState.state.value = ConnState.DISCONNECTED
    }

    private fun closeQuietly() {
        runCatching { box?.close() }; box = null
        runCatching { tunFd?.close() }; tunFd = null
    }

    /** Called by libbox when the tun inbound starts. */
    fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) error("VPN permission not granted")
        val b = Builder().setSession(getString(R.string.app_name)).setMtu(options.mtu)
        val v4 = options.inet4Address; while (v4.hasNext()) { val a = v4.next(); b.addAddress(a.address(), a.prefix()) }
        val v6 = options.inet6Address; while (v6.hasNext()) { val a = v6.next(); b.addAddress(a.address(), a.prefix()) }
        if (options.autoRoute) {
            b.addRoute("0.0.0.0", 0); b.addRoute("::", 0)
            b.addDnsServer(options.dnsServerAddress)
        }
        val fd = b.establish() ?: error("VPN interface establish() returned null")
        tunFd = fd; return fd.fd
    }

    override fun onRevoke() { VpnState.userStopped = true; scope.launch { stopEngine(); stopSelf() } }
    override fun onDestroy() { closeQuietly(); scope.cancel(); super.onDestroy() }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("vpn", "VPN", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "vpn").setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.st_connected)).setSmallIcon(R.drawable.ic_fg)
            .setContentIntent(pi).setOngoing(true).build()
    }

    companion object { const val ACTION_STOP = "com.aryan.vpn.STOP" }
}
