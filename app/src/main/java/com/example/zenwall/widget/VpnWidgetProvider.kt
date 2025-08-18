package com.example.zenwall.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
            setTextViewText(R.id.widget_status, if (running) context.getString(R.string.widget_status_on) else context.getString(R.string.widget_status_off))
            setImageViewResource(R.id.widget_toggle_icon, R.drawable.ic_power)
            // Set background to match VPN state
            setInt(R.id.widget_root, "setBackgroundResource", if (running) R.drawable.bg_widget_on else R.drawable.bg_widget_off)

            val intent = Intent(context, AuthenticationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                context,
                0,
                intent,
                (PendingIntent.FLAG_IMMUTABLE)
            )
            setOnClickPendingIntent(R.id.widget_root, pi)
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
                        setTextViewText(R.id.widget_status, if (running) context.getString(R.string.widget_status_on) else context.getString(R.string.widget_status_off))
                        // Update background as well on bulk update
                        setInt(R.id.widget_root, "setBackgroundResource", if (running) R.drawable.bg_widget_on else R.drawable.bg_widget_off)
                    }
                    mgr.updateAppWidget(id, views)
                }
            }
        }
    }
}
