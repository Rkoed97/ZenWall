package com.example.zenwall.auth

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.zenwall.vpn.ZenWallVpnService

/**
 * Minimal authentication gate used by external entry points (QS tile, widget, notification actions).
 * After successful authentication, this activity toggles the ZenWall VPN.
 *
 * Note: Keep this activity lightweight and transparent (see manifest theme).
 */
class AuthenticationActivity : FragmentActivity() {
    private val allowedAuthenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // For minimal implementation, always ask for authentication if available.
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(allowedAuthenticators)
        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            showPrompt()
        } else {
            // If auth not available, just proceed (could be made configurable later)
            toggleVpnAndFinish()
        }
    }

    private fun showPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                toggleVpnAndFinish()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                finish()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Do nothing; the prompt remains
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate to toggle ZenWall")
            .setSubtitle("Use biometrics or device credential")
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()

        prompt.authenticate(promptInfo)
    }

    private val consentLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Try again after the consent flow
        val prep = android.net.VpnService.prepare(this)
        if (prep == null) {
            startVpn()
        }
        finish()
    }

    private fun toggleVpnAndFinish() {
        val running = try { ZenWallVpnService.isRunning.value } catch (_: Exception) { false }
        if (running) {
            stopVpn()
            finish()
        } else {
            val prep = android.net.VpnService.prepare(this)
            if (prep != null) {
                consentLauncher.launch(prep)
            } else {
                startVpn()
                finish()
            }
        }
    }

    private fun startVpn() {
        val intent = Intent(this, ZenWallVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun stopVpn() {
        val intent = Intent(this, ZenWallVpnService::class.java).setAction(ZenWallVpnService.ACTION_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }
}
