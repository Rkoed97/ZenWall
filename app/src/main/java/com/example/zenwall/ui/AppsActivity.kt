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
import androidx.compose.material.icons.filled.Settings
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
import android.widget.Toast
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

 data class AppEntry(val pkg: String, val label: String, val isSystem: Boolean)

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

    // Observe preloaded apps from repository and build a filtered list in-memory
    val appsRepo = remember { com.example.zenwall.data.InstalledAppsRepository.get(context) }
    LaunchedEffect(Unit) { appsRepo.ensurePreloaded() }
    val repoApps by appsRepo.apps.collectAsState()
    val loading by appsRepo.isLoading.collectAsState()

    LaunchedEffect(repoApps, showSystem) {
        val base = if (showSystem) repoApps else repoApps.filter { !it.isSystem }
        allApps = base.map { AppEntry(it.pkg, it.label, it.isSystem) }
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
                TextButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    Spacer(Modifier.width(8.dp))
                    Text("Settings")
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
                    // Persist selection, notify user, and return to Home
                    scope.launch { repo.setBlockedPackages(selected) }
                    Toast.makeText(context, "Restart the VPN for changes to take effect", Toast.LENGTH_LONG).show()
                    context.startActivity(Intent(context, com.example.zenwall.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    (context as? android.app.Activity)?.finish()
                }) { Text("Save") }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.pkg }) { app ->
                    AppRow(app = app, checked = selected.contains(app.pkg)) { newChecked ->
                        val newSelected = if (newChecked) selected + app.pkg else selected - app.pkg
                        selected = newSelected
                        // Persist immediately; user must restart VPN to apply changes
                        scope.launch { repo.setBlockedPackages(newSelected) }
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
            // Load icon lazily per item to avoid heavy preloading
            val context = androidx.compose.ui.platform.LocalContext.current
            val pm = context.packageManager
            val drawable = remember(app.pkg) { runCatching { pm.getApplicationIcon(app.pkg) }.getOrNull() }
            if (drawable != null) {
                val bmp = remember(app.pkg) { drawable.toBitmap() }
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(12.dp))
            } else {
                Spacer(Modifier.width(52.dp)) // reserve space when icon missing
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label)
                Text(app.pkg, style = MaterialTheme.typography.bodySmall)
            }
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}
