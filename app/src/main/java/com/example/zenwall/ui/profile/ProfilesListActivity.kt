package com.example.zenwall.ui.profile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.zenwall.data.AppRulesRepo
import com.example.zenwall.data.ProfileRepository
import com.example.zenwall.ui.theme.ZenWallTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class ProfilesListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Optional guard: only from Settings
        if (intent?.getBooleanExtra("fromSettings", false) != true) {
            finish()
            return
        }
        setContent { ZenWallTheme { ProfilesListScreen(onBack = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilesListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ProfileRepository(context) }
    val appRulesRepo = remember { AppRulesRepo(context) }
    val profiles by repo.profilesFlow.collectAsState(initial = emptyList())
    val scope = remember { MainScope() }

    LaunchedEffect(Unit) {
        // One-time migration to a default profile if none exists
        val legacyMode = appRulesRepo.getWhitelistModeOnce()
        val legacyApps = appRulesRepo.getBlockedPackagesOnce()
        repo.ensureDefaultProfileIfEmpty(legacyMode, legacyApps)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Profiles") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }, floatingActionButton = {
        FloatingActionButton(onClick = {
            context.startActivity(Intent(context, ProfileEditorActivity::class.java).putExtra("fromSettings", true))
        }) { Icon(Icons.Filled.Add, contentDescription = "New profile") }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (profiles.isEmpty()) {
                Text("No profiles yet. Create one to get started.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(profiles, key = { it.id }) { p ->
                        ElevatedCard(onClick = {
                            context.startActivity(Intent(context, ProfilePreviewActivity::class.java)
                                .putExtra("fromSettings", true)
                                .putExtra("profileId", p.id))
                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                                    val modeText = if (p.mode == ProfileRepository.ProfileMode.WHITELIST) "Whitelist" else "Blacklist"
                                    Text("$modeText • ${p.apps.size} apps", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        context.startActivity(Intent(context, ProfilePreviewActivity::class.java)
                                            .putExtra("fromSettings", true)
                                            .putExtra("profileId", p.id))
                                    }) { Text("Preview") }
                                    TextButton(onClick = { scope.launch { repo.duplicateProfile(p.id) } }) { Text("Duplicate") }
                                    TextButton(onClick = { scope.launch { repo.deleteProfile(p.id) } }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
