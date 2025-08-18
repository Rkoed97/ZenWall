package com.example.zenwall.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.zenwall.R
import com.example.zenwall.auth.AuthenticationActivity
import com.example.zenwall.vpn.ZenWallVpnService

class VpnWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id) }
    }

    private fun updateOne(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        val running = try { ZenWallVpnService.isRunning.value } catch (_: Exception) { false }
        val views = RemoteViews(context.packageName, R.layout.widget_vpn_small).apply {
            // Icon matches QS tile for consistency
            setImageViewResource(
                R.id.widget_toggle_icon,
                if (running) R.drawable.ic_qs_zenwall_on else R.drawable.ic_qs_zenwall_off
            )
            // Background color reflects VPN status
            setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (running) R.drawable.bg_widget_on else R.drawable.bg_widget_off
            )

            val intent = Intent(context, AuthenticationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                context,
                0,
                intent,
                (PendingIntent.FLAG_IMMUTABLE)
            )
            setOnClickPendingIntent(R.id.widget_root, pi)
            // Optional: accessibility description without visible text
            setContentDescription(
                R.id.widget_toggle_icon,
                if (running) context.getString(R.string.widget_status_on) else context.getString(R.string.widget_status_off)
            )
        }
        mgr.updateAppWidget(widgetId, views)
    }

    companion object {
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, VpnWidgetProvider::class.java))
            if (ids != null && ids.isNotEmpty()) {
                ids.forEach { id ->
                    val running = try { ZenWallVpnService.isRunning.value } catch (_: Exception) { false }
                    val views = RemoteViews(context.packageName, R.layout.widget_vpn_small).apply {
                        setImageViewResource(
                            R.id.widget_toggle_icon,
                            if (running) R.drawable.ic_qs_zenwall_on else R.drawable.ic_qs_zenwall_off
                        )
                        setInt(
                            R.id.widget_root,
                            "setBackgroundResource",
                            if (running) R.drawable.bg_widget_on else R.drawable.bg_widget_off
                        )
                        // Optional a11y mirror of state
                        setContentDescription(
                            R.id.widget_toggle_icon,
                            if (running) context.getString(R.string.widget_status_on) else context.getString(R.string.widget_status_off)
                        )
                    }
                    mgr.updateAppWidget(id, views)
                }
            }
        }
    }
}
