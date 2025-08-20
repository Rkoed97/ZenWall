package com.example.zenwall.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.zenwall.data.AppRulesRepo
import com.example.zenwall.ui.theme.ZenWallTheme
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZenWallTheme {
                HistoryScreen(
                    onBack = {
                        startActivity(Intent(this, com.example.zenwall.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                        finish()
                    },
                    onEdit = { startActivity(Intent(this, AppsActivity::class.java)) },
                    onGoOverview = { /* already here */ },
                    onGoManage = { startActivity(Intent(this, AppsActivity::class.java)) },
                    onGoHome = {
                        startActivity(Intent(this, com.example.zenwall.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onGoOverview: () -> Unit,
    onGoManage: () -> Unit,
    onGoHome: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pm = context.packageManager
    val profileRepo = remember { com.example.zenwall.data.ProfileRepository(context) }
    val appRules = remember { AppRulesRepo(context) }
    val activeProfile by profileRepo.activeProfileFlow.collectAsState(initial = null)
    val workingBlocked by appRules.blockedPackagesFlow.collectAsState(initial = emptySet())
    val workingWhitelist by appRules.whitelistModeFlow.collectAsState(initial = false)

    val hasUnsaved = remember(activeProfile, workingBlocked, workingWhitelist) {
        val p = activeProfile
        if (p == null) false else {
            val profileWhitelist = p.mode == com.example.zenwall.data.ProfileRepository.ProfileMode.WHITELIST
            val appsEqual = workingBlocked == p.apps.toSet()
            (profileWhitelist != workingWhitelist) || !appsEqual
        }
    }

    // Navigate to ProfilePreview when no unsaved changes
    LaunchedEffect(activeProfile?.id, hasUnsaved) {
        val p = activeProfile
        if (p != null && !hasUnsaved) {
            context.startActivity(
                Intent(context, com.example.zenwall.ui.profile.ProfilePreviewActivity::class.java)
                    .putExtra("fromSettings", true)
                    .putExtra("profileId", p.id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            (context as? android.app.Activity)?.finish()
        }
    }

    val scope = rememberCoroutineScope()

    val added = remember(activeProfile, workingBlocked) {
        activeProfile?.let { workingBlocked - it.apps.toSet() } ?: emptySet()
    }
    val removed = remember(activeProfile, workingBlocked) {
        activeProfile?.let { it.apps.toSet() - workingBlocked } ?: emptySet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (hasUnsaved) "Review changes" else "Active Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasUnsaved) {
                        TextButton(onClick = {
                            val p = activeProfile ?: return@TextButton
                            scope.launch {
                                // Discard: restore AppRules from active profile
                                appRules.setWhitelistMode(p.mode == com.example.zenwall.data.ProfileRepository.ProfileMode.WHITELIST)
                                appRules.setBlockedPackages(p.apps.toSet())
                            }
                        }) { Text("Discard") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val p = activeProfile ?: return@Button
                            scope.launch {
                                // Save: update profile to match working state
                                val updated = p.copy(
                                    mode = if (workingWhitelist) com.example.zenwall.data.ProfileRepository.ProfileMode.WHITELIST else com.example.zenwall.data.ProfileRepository.ProfileMode.BLACKLIST,
                                    apps = workingBlocked.toList()
                                )
                                profileRepo.updateProfile(updated)
                                // After save, navigation effect will trigger
                            }
                        }) { Text("Save") }
                    } else {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                    }
                }
            )
        },
        bottomBar = {
            com.example.zenwall.ui.common.ZenBottomBar(
                selected = com.example.zenwall.ui.common.BottomTab.Overview,
                onHome = onGoHome,
                onOverview = onGoOverview,
                onApps = onGoManage,
                onSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val p = activeProfile
            if (p == null) {
                Text("No active profile", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            val profileWhitelist = p.mode == com.example.zenwall.data.ProfileRepository.ProfileMode.WHITELIST
            val modeChanged = profileWhitelist != workingWhitelist
            val modeText = when {
                profileWhitelist && !workingWhitelist -> "Mode: Whitelist → Blacklist"
                !profileWhitelist && workingWhitelist -> "Mode: Blacklist → Whitelist"
                else -> "Mode: ${if (profileWhitelist) "Whitelist" else "Blacklist"}"
            }
            Text("Active profile: ${p.name}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(modeText, color = if (modeChanged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            // Show changes summary
            if (hasUnsaved) {
                Text("Changes", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val listTitle = if (workingWhitelist) "Allowed apps" else "Blocked apps"
                Text("$listTitle: +${added.size}  −${removed.size}")
                Spacer(Modifier.height(8.dp))

                val entries = remember(added, removed) {
                    val addedTriples = added.map { pkg ->
                        Triple(
                            pkg,
                            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrElse { pkg },
                            runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
                        )
                    }
                    val removedTriples = removed.map { pkg ->
                        Triple(
                            pkg,
                            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrElse { pkg },
                            runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
                        )
                    }
                    addedTriples to removedTriples
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    if (entries.first.isNotEmpty()) {
                        item { Text("Added", color = MaterialTheme.colorScheme.primary) }
                        items(entries.first, key = { it.first }) { (pkg, label, icon) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                icon?.let {
                                    Image(
                                        bitmap = it.toBitmap().asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("+ $label", color = MaterialTheme.colorScheme.primary)
                                    Text(pkg, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (entries.second.isNotEmpty()) {
                        item { Text("Removed", color = MaterialTheme.colorScheme.error) }
                        items(entries.second, key = { it.first }) { (pkg, label, icon) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                icon?.let {
                                    Image(
                                        bitmap = it.toBitmap().asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("− $label", color = MaterialTheme.colorScheme.error)
                                    Text(pkg, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            } else {
                Text("No pending changes.")
            }
        }
    }
}
