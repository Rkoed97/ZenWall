package com.example.zenwall.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.zenwall.data.AppRulesRepo
import com.example.zenwall.ui.theme.ZenWallTheme
import com.example.zenwall.vpn.ZenWallVpnService
import kotlinx.coroutines.launch

class AppsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZenWallTheme {
                AppsScreen(
                    onBack = {
                        startActivity(Intent(this, com.example.zenwall.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                        finish()
                    },
                    onGoHistory = { startActivity(Intent(this, HistoryActivity::class.java)) },
                    onGoHome = {
                        startActivity(Intent(this, com.example.zenwall.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    }
                )
            }
        }
    }
}

 data class AppEntry(val pkg: String, val label: String, val isSystem: Boolean, val icon: Drawable)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsScreen(
    onBack: () -> Unit,
    onGoHistory: () -> Unit,
    onGoHome: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pm = context.packageManager
    var showSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var allApps by remember { mutableStateOf(listOf<AppEntry>()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    // Load installed apps and current selection
    LaunchedEffect(showSystem) {
        val apps = pm.getInstalledApplications(0).map { ai: ApplicationInfo ->
            val label = ai.loadLabel(pm).toString()
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val icon = ai.loadIcon(pm)
            AppEntry(ai.packageName, label, isSystem, icon)
        }.filter { showSystem || !it.isSystem }
            .sortedBy { it.label.lowercase() }
        allApps = apps
    }
    LaunchedEffect(Unit) {
        val repo = AppRulesRepo(context)
        selected = repo.getBlockedPackagesOnce().toSet()
    }

    val filtered = remember(allApps, query) {
        val q = query.text.trim().lowercase()
        if (q.isEmpty()) allApps else allApps.filter { it.label.lowercase().contains(q) || it.pkg.contains(q) }
    }

    // Repo to persist selections
    val repo = remember { AppRulesRepo(context) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Manage Apps") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = onGoHistory) {
                    Icon(Icons.Filled.History, contentDescription = "Overview")
                }
            }
        )
    }, bottomBar = {
        BottomAppBar {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onGoHome) {
                    Icon(Icons.Filled.Home, contentDescription = "Home")
                    Spacer(Modifier.width(8.dp))
                    Text("Home")
                }
                TextButton(onClick = onGoHistory) {
                    Icon(Icons.Filled.History, contentDescription = "Overview")
                    Spacer(Modifier.width(8.dp))
                    Text("Overview")
                }
                TextButton(onClick = { /* already here */ }) {
                    Icon(Icons.Filled.Apps, contentDescription = "Manage Apps")
                    Spacer(Modifier.width(8.dp))
                    Text("Manage Apps")
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            // Controls row for system filter and apply
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text("System apps")
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = showSystem, onCheckedChange = { showSystem = it })
                }
                Button(onClick = {
                    // Persist and rebuild
                    scope.launch { repo.setBlockedPackages(selected) }
                    val intent = Intent(context, ZenWallVpnService::class.java).setAction(ZenWallVpnService.ACTION_REBUILD)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }) { Text("Apply & Rebuild") }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.pkg }) { app ->
                    AppRow(app = app, checked = selected.contains(app.pkg)) { newChecked ->
                        val newSelected = if (newChecked) selected + app.pkg else selected - app.pkg
                        selected = newSelected
                        // Persist immediately and trigger VPN rebuild automatically
                        scope.launch { repo.setBlockedPackages(newSelected) }
                        val intent = Intent(context, ZenWallVpnService::class.java).setAction(ZenWallVpnService.ACTION_REBUILD)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).clickable { onCheckedChange(!checked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bmp = remember(app.pkg) { app.icon.toBitmap() }
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label)
                Text(app.pkg, style = MaterialTheme.typography.bodySmall)
            }
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}
