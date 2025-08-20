package com.example.zenwall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
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
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : FragmentActivity() {
    private val allowedAuthenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    private var keepOnSplash: Boolean = true
    private var splashDelay: Long = 750;

    private fun authenticateThen(onSuccess: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(allowedAuthenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            val reason = when (canAuth) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware available"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware unavailable"
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No biometrics/PIN enrolled. Please set up a screen lock or biometrics."
                else -> "Authentication not available"
            }
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Optional feedback
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate to control ZenWall")
            .setSubtitle("Use biometrics or device PIN/Pattern/Password")
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()

        prompt.authenticate(promptInfo)
    }
    private lateinit var vm: com.example.zenwall.ui.MainViewModel

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        // Regardless of result code, if user granted, VpnService.prepare returns null next time.
        val prep = android.net.VpnService.prepare(this)
        if (prep == null) {
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install AndroidX SplashScreen per SPLASHSCREEN.md
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep splash visible for a short time to enhance brand visibility
        splash.setKeepOnScreenCondition { keepOnSplash }
        lifecycleScope.launch {
            // Delay just under a second for a smoother transition without feeling slow
            delay(splashDelay);
            keepOnSplash = false
        }
        vm = androidx.lifecycle.ViewModelProvider(this)[com.example.zenwall.ui.MainViewModel::class.java]
        enableEdgeToEdge()
        setContent {
            ZenWallTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("ZenWall") }
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
                                TextButton(onClick = { startActivity(Intent(this@MainActivity, com.example.zenwall.ui.SettingsActivity::class.java)) }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                    Spacer(Modifier.width(8.dp))
                                    Text("Settings")
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
                        // Show active profile name and mode
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        val profileRepo = remember { com.example.zenwall.data.ProfileRepository(ctx) }
                        val activeProfile by profileRepo.activeProfileFlow.collectAsState(initial = null)
                        val modeText = when (activeProfile?.mode) {
                            com.example.zenwall.data.ProfileRepository.ProfileMode.WHITELIST -> "Whitelist"
                            com.example.zenwall.data.ProfileRepository.ProfileMode.BLACKLIST -> "Blacklist"
                            null -> null
                        }
                        if (activeProfile == null) {
                            Text("No active profile", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("Active profile: ${activeProfile!!.name} (${modeText})", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(16.dp))

                        val running by com.example.zenwall.vpn.ZenWallVpnService.isRunning.collectAsState(initial = false)

                        // Big round toggle button with heartbeat when ON
                        val targetScale = if (running) 1.08f else 1f
                        val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
                        val pulse by infinite.animateFloat(
                            initialValue = 1f,
                            targetValue = targetScale,
                            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                animation = androidx.compose.animation.core.tween(durationMillis = 900),
                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                            ),
                            label = "pulseAnim"
                        )
                        val scale = if (running) pulse else 1f

                        val bgColor = if (running) androidx.compose.ui.graphics.Color(0xFF81C784) else androidx.compose.ui.graphics.Color(0xFF424242)
                        val text = if (running) "Turn Off" else "Turn On"

                        androidx.compose.material3.Surface(
                            modifier = Modifier
                                .size(200.dp)
                                .graphicsLayer(scaleX = scale, scaleY = scale),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = bgColor,
                            tonalElevation = if (running) 6.dp else 2.dp,
                            shadowElevation = if (running) 12.dp else 4.dp,
                            onClick = {
                                authenticateThen {
                                    if (running) {
                                        val intent = Intent(this@MainActivity, ZenWallVpnService::class.java).setAction(ZenWallVpnService.ACTION_STOP)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            startForegroundService(intent)
                                        } else {
                                            startService(intent)
                                        }
                                    } else {
                                        val prep = android.net.VpnService.prepare(this@MainActivity)
                                        if (prep != null) {
                                            vpnPermissionLauncher.launch(prep)
                                        } else {
                                            startVpnService()
                                        }
                                    }
                                }
                            }
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.PowerSettingsNew,
                                    contentDescription = text,
                                    tint = Color.White,
                                    modifier = Modifier.size(120.dp)
                                )
                            }
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

