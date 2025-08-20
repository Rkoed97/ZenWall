package com.example.zenwall.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import com.example.zenwall.data.AppRulesRepo
import com.example.zenwall.ui.theme.ZenWallTheme

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
    var blocked by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        val repo = AppRulesRepo(context)
        blocked = repo.getBlockedPackagesOnce()
    }

    val entries = remember(blocked) {
        blocked.map { pkg -> pkg to runCatching {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        }.getOrElse { pkg } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Overview") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                }
            )
        },
        bottomBar = {
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
                TextButton(onClick = onGoOverview) {
                    Icon(Icons.Filled.History, contentDescription = "Overview")
                    Spacer(Modifier.width(8.dp))
                    Text("Overview")
                }
                TextButton(onClick = onGoManage) {
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
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Blocked apps (${entries.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (entries.isEmpty()) {
                Text("No apps selected.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.first }) { (pkg, label) ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(label)
                            Text(pkg, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
