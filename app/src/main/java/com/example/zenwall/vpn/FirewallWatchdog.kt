package com.example.zenwall.vpn

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.zenwall.R

/** What the watchdog should do, given the firewall's state. Kept pure for unit testing. */
enum class WatchdogDecision {
    NOTHING, RESTART, ALERT_AND_GIVE_UP;

    companion object {
        fun decide(isRunning: Boolean, hasVpnConsent: Boolean): WatchdogDecision = when {
            isRunning -> NOTHING
            hasVpnConsent -> RESTART
            else -> ALERT_AND_GIVE_UP
        }
    }
}

/**
 * Safety net for an unexpected process death. On Android 17, neither START_STICKY nor
 * Always-on VPN revives a VPN app whose process died (verified on device). The job exists
 * only while the user wants the firewall on. JobScheduler keeps it across process death and
 * reboot. The service schedules it after a successful start and cancels it on any intentional
 * stop: the user turns it off, another VPN takes over, or permission is lost. So the watchdog
 * never fights a deliberate stop.
 */
object FirewallWatchdog {
    private const val JOB_ID = 0x2E11
    private const val CHECK_INTERVAL_MS = 15 * 60 * 1000L // JobScheduler's minimum period
    private const val TAG = "ZenWallWatchdog"

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, FirewallWatchdogJobService::class.java))
            .setPeriodic(CHECK_INTERVAL_MS)
            .setPersisted(true)
            .build()
        val result = scheduler.schedule(job)
        if (result != JobScheduler.RESULT_SUCCESS) Log.w(TAG, "Watchdog scheduling failed: $result")
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }
}

class FirewallWatchdogJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val hasConsent = VpnService.prepare(this) == null
        when (WatchdogDecision.decide(ZenWallVpnService.isRunning.value, hasConsent)) {
            WatchdogDecision.NOTHING -> Unit
            WatchdogDecision.RESTART -> restart()
            WatchdogDecision.ALERT_AND_GIVE_UP -> {
                FirewallAlerts.notifyStopped(this, getString(R.string.vpn_failed_text))
                FirewallWatchdog.cancel(this)
            }
        }
        return false // all work is done synchronously
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    private fun restart() {
        Log.w("ZenWallWatchdog", "Firewall should be on but isn't; restarting")
        try {
            ContextCompat.startForegroundService(this, Intent(this, ZenWallVpnService::class.java))
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException when battery isn't Unrestricted
            Log.e("ZenWallWatchdog", "Restart not allowed", e)
            FirewallAlerts.notifyStopped(this, getString(R.string.vpn_failed_text))
        }
    }
}
