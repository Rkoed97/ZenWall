package com.example.zenwall.qs

import android.content.ComponentName
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.zenwall.R
import com.example.zenwall.auth.AuthenticationActivity
import com.example.zenwall.vpn.ZenWallVpnService

@RequiresApi(24)
class VpnTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val running = try { ZenWallVpnService.isRunning.value } catch (_: Exception) { false }
        qsTile?.apply {
            val labelText = if (running) getString(R.string.widget_status_on) else getString(R.string.widget_status_off)
            label = labelText
            icon = android.graphics.drawable.Icon.createWithResource(
                this@VpnTileService,
                if (running) R.drawable.ic_qs_zenwall_on else R.drawable.ic_qs_zenwall_off
            )
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            contentDescription = if (running) getString(R.string.qs_tile_cd_on) else getString(R.string.qs_tile_cd_off)
            updateTile()
        }
    }

    override fun onClick() {
        // Ensure device is unlocked, then collapse and launch AuthenticationActivity
        unlockAndRun {
            val i = Intent(this, AuthenticationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                val pi = android.app.PendingIntent.getActivity(
                    this,
                    0,
                    i,
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(i)
            }
        }
    }

    companion object {
        fun requestTileUpdate(service: TileService) {
            // Not used; static convenience could be added here if needed
        }

        fun requestListeningState(context: android.content.Context) {
            requestListeningState(
                context,
                ComponentName(context, VpnTileService::class.java)
            )
        }
    }
}
