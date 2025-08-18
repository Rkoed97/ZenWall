package com.example.zenwall.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.zenwall.R

class ZenWallVpnService : VpnService() {

    companion object {
        const val ACTION_REBUILD = "com.example.zenwall.ACTION_REBUILD"
        const val ACTION_STOP = "com.example.zenwall.ACTION_STOP"
        private const val NOTIF_ID = 42
        private const val CHANNEL_ID = "zenwall_vpn"
        // Exposed running state for UI to observe
        val isRunning: kotlinx.coroutines.flow.MutableStateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    private var tun: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        // Don't establish here; wait for onStartCommand to know intent
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REBUILD -> {
                establish()
                return START_STICKY
            }
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> {
                // Default start: establish
                establish()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        // Update running state
        try { isRunning.value = false } catch (_: Exception) {}
        // Ensure foreground is stopped when service is destroyed
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onRevoke() {
        // Called when the VPN permission is revoked by the user/system
        stopVpn()
        super.onRevoke()
    }

    private fun stopVpn() {
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        // Update running state
        try { isRunning.value = false } catch (_: Exception) {}
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        } catch (_: Exception) {}
        // Finally stop the service
        stopSelf()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "ZenWall",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle("ZenWall active")
            .setContentText("Per-app internet control enabled")
            .setOngoing(true)
            .build()
    }

    private fun establish() {
        try { tun?.close() } catch (_: Exception) {}
        tun = null

        val repo = com.example.zenwall.data.AppRulesRepo(this)
        // Blocking calls are fine for small reads here
        val blockedPkgs = kotlinx.coroutines.runBlocking { repo.getBlockedPackagesOnce() }
        val whitelistMode = kotlinx.coroutines.runBlocking { repo.getWhitelistModeOnce() }

        // If blacklist mode and nothing selected, stop service to conserve resources
        if (blockedPkgs.isEmpty() && !whitelistMode) {
            stopVpn(); return
        }

        val builder = Builder()
            .setSession("ZenWall")
            .setMtu(1500)
            .addAddress("10.0.0.2", 32)
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .setBlocking(true)

        try {
            if (whitelistMode) {
                // Whitelist mode: only selected apps should BYPASS the VPN (they are allowed to access internet).
                // Others will be captured by VPN and effectively blocked.
                blockedPkgs.forEach { pkg ->
                    try { builder.addDisallowedApplication(pkg) } catch (_: Exception) {}
                }
            } else {
                // Blacklist mode: selected apps should be captured by the VPN (blocked),
                // so they must be ALLOWED to use the VPN interface, while others bypass.
                blockedPkgs.forEach { pkg ->
                    try { builder.addAllowedApplication(pkg) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        tun = builder.establish()
                try { isRunning.value = tun != null } catch (_: Exception) {}
    }
}
