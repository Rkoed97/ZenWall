package com.example.zenwall.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.zenwall.MainActivity
import com.example.zenwall.R
import com.example.zenwall.data.AppRulesRepo
import com.example.zenwall.data.FirewallRules
import com.example.zenwall.qs.VpnTileService
import com.example.zenwall.widget.VpnWidgetProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * Per-app firewall built on VpnService. Apps routed into the tunnel are blocked: the tunnel is
 * never read, so their packets go nowhere. Other apps bypass the VPN and use the network normally.
 *
 * The service watches [AppRulesRepo.rulesFlow] and package installs/removals. It rebuilds the tunnel
 * live, with no restart needed. It establishes the new interface before closing the old one, so a
 * rule change never opens a window where blocked apps can leak. All state is confined to the
 * main thread; a tunnel build is a short binder call.
 */
class ZenWallVpnService : VpnService() {

    companion object {
        const val ACTION_STOP = "com.example.zenwall.ACTION_STOP"
        private const val TAG = "ZenWallVpn"
        private const val NOTIF_ID = 42
        private const val NOTIF_ID_STOPPED = 43
        private const val CHANNEL_ID = "zenwall_vpn"
        private const val CHANNEL_ID_ALERTS = "zenwall_alerts"
        private const val TUN_ADDRESS_V4 = "10.111.222.1"
        private const val TUN_ADDRESS_V6 = "fd00:7a77:6c6c::1"
        private const val TUN_MTU = 1500
        private const val TUN_DNS_SERVER = "1.1.1.1"
        private val REBUILD_RETRY_DELAYS_MS = longArrayOf(2_000, 5_000, 15_000, 60_000)
        private const val RULES_READ_RETRY_MS = 5_000L

        private val _isRunning = MutableStateFlow(false)
        /** True while a tunnel is established and enforcing rules. Observed by UI, tile and widget. */
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "Unexpected error in VPN service", e) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
    private val rebuildRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var tun: ParcelFileDescriptor? = null
    private var enforceJob: Job? = null
    private var packageReceiverRegistered = false
    private var currentRules: FirewallRules? = null
    private var retryJob: Job? = null
    private var failedAttempts = 0

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // App UIDs are resolved when the tunnel is built. A newly installed (or reinstalled)
            // selected app gets a new UID that the current tunnel doesn't know, so rebuild.
            // Apps outside the rules don't matter: whitelist mode already captures every unlisted app.
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            val pkg = intent.data?.schemeSpecificPart ?: return
            if (currentRules?.packages?.contains(pkg) == true) rebuildRequests.tryEmit(Unit)
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            startForegroundCompat()
        } catch (e: Exception) {
            // Avoid a START_STICKY crash loop if the system refuses the foreground start.
            Log.e(TAG, "Could not enter foreground", e)
            notifyStopped(getString(R.string.vpn_failed_text))
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Defensive: if the system ever binds (e.g. always-on) without a start command, still enforce.
        if (intent?.action == SERVICE_INTERFACE && enforceJob?.isActive != true) startEnforcing()
        return super.onBind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        // Default start, always-on VPN start by the system, or a sticky restart (null intent).
        startEnforcing()
        return START_STICKY
    }

    override fun onRevoke() {
        // Another VPN took over (only one can be active), or the user revoked the permission.
        Log.w(TAG, "VPN revoked by system")
        notifyStopped(getString(R.string.vpn_revoked_text))
        stopVpn()
    }

    override fun onDestroy() {
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    private fun startEnforcing() {
        if (enforceJob?.isActive == true) {
            rebuildRequests.tryEmit(Unit)
            return
        }
        registerPackageReceiver()
        val repo = AppRulesRepo(applicationContext)
        enforceJob = scope.launch {
            val rules = repo.rulesFlow.retryWhen { cause, _ ->
                // Never let a storage error kill the service; the current tunnel stays in place meanwhile.
                Log.e(TAG, "Reading rules failed; retrying", cause)
                delay(RULES_READ_RETRY_MS)
                true
            }
            combine(rules, rebuildRequests.onStart { emit(Unit) }) { r, _ -> r }
                .conflate()
                .collect { r -> applyRules(r) }
        }
    }

    private fun applyRules(rules: FirewallRules) {
        currentRules = rules
        retryJob?.cancel()
        val plan = FirewallPlan.from(rules, packageName, ::isInstalled)
        val newTun = try {
            buildTunnel(plan).establish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish tunnel", e)
            null
        }
        if (newTun == null) {
            handleEstablishFailure()
            return
        }
        failedAttempts = 0
        cancelStoppedNotification()
        val old = tun
        tun = newTun
        closeQuietly(old)
        Log.i(TAG, "Tunnel applied: mode=${plan.mode} packages=${plan.packages.size}")
        _isRunning.value = true
        notifyStateChanged()
    }

    /**
     * establish() returns null when the VPN permission isn't granted. It can also fail transiently.
     * If a tunnel is already enforcing, keep it (fail closed with the old rules) and retry.
     * Only stop when there's nothing working to keep.
     */
    private fun handleEstablishFailure() {
        if (tun == null) {
            notifyStopped(getString(R.string.vpn_failed_text))
            stopVpn()
            return
        }
        val delayMs = REBUILD_RETRY_DELAYS_MS[failedAttempts.coerceAtMost(REBUILD_RETRY_DELAYS_MS.lastIndex)]
        failedAttempts++
        Log.w(TAG, "Rebuild failed (attempt $failedAttempts); keeping previous tunnel, retrying in ${delayMs}ms")
        retryJob = scope.launch {
            delay(delayMs)
            rebuildRequests.emit(Unit)
        }
    }

    private fun buildTunnel(plan: FirewallPlan): Builder {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(TUN_MTU)
            .addAddress(TUN_ADDRESS_V4, 32)
            .addAddress(TUN_ADDRESS_V6, 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            // Captured apps' DNS goes into the (unread) tunnel too, so lookups hang like
            // everything else. That avoids an instant "unknown host" error or a fallback to the
            // underlying network's resolver.
            .addDnsServer(TUN_DNS_SERVER)
            .setConfigureIntent(mainActivityIntent())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        plan.packages.forEach { pkg ->
            try {
                when (plan.mode) {
                    FirewallPlan.Mode.CAPTURE_ONLY -> builder.addAllowedApplication(pkg)
                    FirewallPlan.Mode.CAPTURE_ALL_EXCEPT -> builder.addDisallowedApplication(pkg)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // Uninstalled between the plan and the build; the next package broadcast triggers a rebuild.
                Log.w(TAG, "Package vanished while building tunnel: $pkg")
            }
        }
        return builder
    }

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun stopVpn() {
        teardown()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Idempotent: safe to call from stopVpn and again from onDestroy. */
    private fun teardown() {
        enforceJob?.cancel()
        enforceJob = null
        retryJob?.cancel()
        retryJob = null
        currentRules = null
        if (packageReceiverRegistered) {
            runCatching { unregisterReceiver(packageReceiver) }
            packageReceiverRegistered = false
        }
        val old = tun
        tun = null
        closeQuietly(old)
        if (_isRunning.value) {
            _isRunning.value = false
            notifyStateChanged()
        }
    }

    private fun registerPackageReceiver() {
        if (packageReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        packageReceiverRegistered = true
    }

    private fun closeQuietly(pfd: ParcelFileDescriptor?) {
        try {
            pfd?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing tunnel", e)
        }
    }

    private fun notifyStateChanged() {
        runCatching { VpnWidgetProvider.updateAll(this) }
            .onFailure { Log.w(TAG, "Widget update failed", it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { VpnTileService.requestListeningState(this) }
                .onFailure { Log.w(TAG, "Tile update failed", it) }
        }
    }

    // --- Notifications ---------------------------------------------------------------------

    private fun startForegroundCompat() {
        // specialUse has no runtime cap. dataSync is capped at 6h per 24h on Android 15+,
        // after which the system would kill the service and the firewall would silently stop.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildOngoingNotification(), type)
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "ZenWall", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_ALERTS, getString(R.string.vpn_alerts_channel), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun buildOngoingNotification(): Notification {
        ensureChannels()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle("ZenWall active")
            .setContentText("Per-app internet control enabled")
            .setContentIntent(mainActivityIntent())
            .setOngoing(true)
            .build()
    }

    private fun notifyStopped(text: String) {
        ensureChannels()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(getString(R.string.vpn_stopped_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(mainActivityIntent())
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID_STOPPED, notification) }
            .onFailure { Log.w(TAG, "Could not post stopped notification", it) }
    }

    private fun cancelStoppedNotification() {
        runCatching { getSystemService(NotificationManager::class.java).cancel(NOTIF_ID_STOPPED) }
    }

    private fun mainActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
