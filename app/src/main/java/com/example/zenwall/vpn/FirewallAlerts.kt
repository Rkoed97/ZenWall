package com.example.zenwall.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.zenwall.MainActivity
import com.example.zenwall.R

/** The high-priority "firewall is OFF" alert, shared by the VPN service and the watchdog. */
object FirewallAlerts {
    const val CHANNEL_ID_ONGOING = "zenwall_vpn"
    private const val CHANNEL_ID_ALERTS = "zenwall_alerts"
    private const val NOTIF_ID_STOPPED = 43
    private const val TAG = "ZenWallAlerts"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID_ONGOING, "ZenWall", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_ALERTS, context.getString(R.string.vpn_alerts_channel), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun notifyStopped(context: Context, text: String) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(context.getString(R.string.vpn_stopped_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(NOTIF_ID_STOPPED, notification) }
            .onFailure { Log.w(TAG, "Could not post stopped notification", it) }
    }

    fun cancelStopped(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID_STOPPED) }
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
