package com.example.zenwall.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Refresh the preloaded apps list when packages are added/removed/changed
        val repo = com.example.zenwall.data.InstalledAppsRepository.get(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repo.refresh()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }
}
