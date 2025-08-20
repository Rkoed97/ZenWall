package com.example.zenwall.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.zenwall.ui.theme.ZenWallTheme

class SettingsActivity : ComponentActivity() {
    private lateinit var vm: com.example.zenwall.ui.MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[com.example.zenwall.ui.MainViewModel::class.java]
        setContent {
            ZenWallTheme {
                SettingsScreen(vm = vm, onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: com.example.zenwall.ui.MainViewModel, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mode by vm.whitelistMode.collectAsState(initial = false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            androidx.compose.material3.BottomAppBar {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.material3.TextButton(onClick = { context.startActivity(android.content.Intent(context, com.example.zenwall.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)) }) {
                        Icon(Icons.Filled.Home, contentDescription = "Home")
                        Spacer(Modifier.width(8.dp))
                        Text("Home")
                    }
                    androidx.compose.material3.TextButton(onClick = { context.startActivity(android.content.Intent(context, HistoryActivity::class.java)) }) {
                        Icon(Icons.Filled.History, contentDescription = "Overview")
                        Spacer(Modifier.width(8.dp))
                        Text("Overview")
                    }
                    androidx.compose.material3.TextButton(onClick = { context.startActivity(android.content.Intent(context, AppsActivity::class.java)) }) {
                        Icon(Icons.Filled.Apps, contentDescription = "Manage Apps")
                        Spacer(Modifier.width(8.dp))
                        Text("Manage Apps")
                    }
                    androidx.compose.material3.TextButton(onClick = { /* already here */ }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        Spacer(Modifier.width(8.dp))
                        Text("Settings")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Mode", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(if (mode) "Whitelist (only selected apps can access internet)" else "Blacklist (selected apps are blocked)")
            Switch(
                checked = mode,
                onCheckedChange = { enabled ->
                    vm.setWhitelistMode(enabled)
                    Toast.makeText(context, "Restart the VPN for changes to take effect", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.padding(top = 8.dp)
            )

            // Profiles management entry
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(onClick = {
                val intent = android.content.Intent(context, com.example.zenwall.ui.profile.ProfilesListActivity::class.java)
                    .putExtra("fromSettings", true)
                context.startActivity(intent)
            }) {
                Text("Profiles")
            }
        }
    }
}
