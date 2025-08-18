package com.example.zenwall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zenwall.ui.theme.ZenWallTheme
import com.example.zenwall.ui.AppsActivity
import com.example.zenwall.ui.HistoryActivity
import com.example.zenwall.vpn.ZenWallVpnService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var vm: com.example.zenwall.ui.MainViewModel

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        // Regardless of result code, if user granted, VpnService.prepare returns null next time.
        val prep = android.net.VpnService.prepare(this)
        if (prep == null) {
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = androidx.lifecycle.ViewModelProvider(this)[com.example.zenwall.ui.MainViewModel::class.java]
        enableEdgeToEdge()
        setContent {
            ZenWallTheme {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            DrawerModeContent(vm, onClose = { scope.launch { drawerState.close() } })
                        }
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            TopAppBar(
                                title = { Text("ZenWall") },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Filled.Menu, contentDescription = "Open menu")
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
                                    TextButton(onClick = {
                                        val intent = Intent(this@MainActivity, MainActivity::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                        startActivity(intent)
                                    }) {
                                        Icon(Icons.Filled.Home, contentDescription = "Home")
                                        Spacer(Modifier.width(8.dp))
                                        Text("Home")
                                    }
                                    TextButton(onClick = { startActivity(Intent(this@MainActivity, HistoryActivity::class.java)) }) {
                                        Icon(Icons.Filled.History, contentDescription = "Overview")
                                        Spacer(Modifier.width(8.dp))
                                        Text("Overview")
                                    }
                                    TextButton(onClick = { startActivity(Intent(this@MainActivity, AppsActivity::class.java)) }) {
                                        Icon(Icons.Filled.Apps, contentDescription = "Manage Apps")
                                        Spacer(Modifier.width(8.dp))
                                        Text("Manage Apps")
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        // Centered large Start/Stop controls
                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Controls", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val prep = android.net.VpnService.prepare(this@MainActivity)
                                    if (prep != null) {
                                        vpnPermissionLauncher.launch(prep)
                                    } else {
                                        startVpnService()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(0.85f).height(56.dp)
                            ) { Text("Start VPN") }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(this@MainActivity, ZenWallVpnService::class.java).setAction(ZenWallVpnService.ACTION_STOP)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        startForegroundService(intent)
                                    } else {
                                        startService(intent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(0.85f).height(56.dp)
                            ) { Text("Stop VPN") }
                        }
                    }
                }
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, ZenWallVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
private fun DrawerModeContent(vm: com.example.zenwall.ui.MainViewModel, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mode by vm.whitelistMode.collectAsState(initial = false)
    Column(modifier = Modifier.padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close menu")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Mode", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Text(if (mode) "Whitelist (only selected apps can access internet)" else "Blacklist (selected apps are blocked)")
        androidx.compose.material3.Switch(
            checked = mode,
            onCheckedChange = { enabled ->
                vm.setWhitelistMode(enabled)
                // Ask service to rebuild right away
                val intent = Intent(context, ZenWallVpnService::class.java).setAction(ZenWallVpnService.ACTION_REBUILD)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
