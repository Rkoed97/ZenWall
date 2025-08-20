package com.example.zenwall.data

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Process-wide repository that preloads the list of installed apps.
 * Preloads only lightweight data (package, label, isSystem). No icons are cached here.
 */
data class InstalledApp(
    val pkg: String,
    val label: String,
    val isSystem: Boolean
)

class InstalledAppsRepository private constructor(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @Volatile private var hasPreloaded = false

    fun ensurePreloaded() {
        if (hasPreloaded) return
        synchronized(this) {
            if (!hasPreloaded) {
                hasPreloaded = true
                scope.launch { refreshInternal() }
            }
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) { refreshInternal() }

    private suspend fun refreshInternal() {
        _isLoading.value = true
        try {
            val pm = appContext.packageManager
            val list = pm.getInstalledApplications(0)
                .map { ai: ApplicationInfo ->
                    InstalledApp(
                        pkg = ai.packageName,
                        label = ai.loadLabel(pm).toString(),
                        isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }
                .sortedBy { it.label.lowercase() }
            _apps.value = list
        } catch (_: Throwable) {
            // Keep old data on failure
        } finally {
            _isLoading.value = false
        }
    }

    fun labelFor(pkg: String): String? {
        return _apps.value.firstOrNull { it.pkg == pkg }?.label
    }

    companion object {
        @Volatile private var INSTANCE: InstalledAppsRepository? = null
        fun get(context: Context): InstalledAppsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: InstalledAppsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
