package com.example.zenwall.ui.profile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.zenwall.data.ProfileRepository
import com.example.zenwall.data.AppRulesRepo
import com.example.zenwall.ui.theme.ZenWallTheme
import kotlinx.coroutines.launch

class ProfilePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra("fromSettings", false) != true) {
            finish(); return
        }
        val id = intent?.getLongExtra("profileId", -1L) ?: -1L
        if (id <= 0) { finish(); return }
        setContent { ZenWallTheme { ProfilePreviewScreen(profileId = id, onBack = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilePreviewScreen(profileId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ProfileRepository(context) }
    val profile by repo.getProfileFlow(profileId).collectAsState(initial = null)
    val activeId by repo.activeProfileIdFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val pm = context.packageManager

    val allEntries = remember(profile) {
        val apps = profile?.apps ?: emptyList()
        apps.map { pkg ->
            Triple(
                pkg,
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrElse { pkg },
                runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
            )
        }
    }

    val filtered = remember(allEntries, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) allEntries else allEntries.filter { (pkg, label, _) ->
            pkg.lowercase().contains(q) || label.lowercase().contains(q)
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(profile?.name ?: "Preview") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }, actions = {
            val ctx = LocalContext.current
            val p = profile
            if (p != null) {
                // Set as Active
                androidx.compose.material3.TextButton(
                    enabled = p.id != (activeId ?: -1L),
                    onClick = {
                        scope.launch {
                            repo.setActiveProfile(p.id)
                            val appRules = AppRulesRepo(ctx)
                            appRules.setWhitelistMode(p.mode == ProfileRepository.ProfileMode.WHITELIST)
                            appRules.setBlockedPackages(p.apps.toSet())
                        }
                    }
                ) { Text("Set as Active") }
                // Edit
                IconButton(onClick = {
                    ctx.startActivity(android.content.Intent(ctx, ProfileEditorActivity::class.java)
                        .putExtra("fromSettings", true)
                        .putExtra("profileId", p.id))
                }) { Icon(Icons.Filled.Edit, contentDescription = "Edit profile") }
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val modeText = when (profile?.mode) {
                ProfileRepository.ProfileMode.WHITELIST -> "Whitelist: only listed apps are allowed"
                ProfileRepository.ProfileMode.BLACKLIST -> "Blacklist: listed apps are blocked"
                else -> ""
            }
            Text(modeText, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.padding(8.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search apps") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.padding(8.dp))

            val listTitle = if (profile?.mode == ProfileRepository.ProfileMode.BLACKLIST) "Blocked apps" else "Allowed apps"
            Text("$listTitle (${filtered.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.padding(8.dp))

            if (filtered.isEmpty()) {
                Text("No apps selected.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.first }) { (pkg, label, icon) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
                            Column {
                                Text(label)
                                Text(pkg, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
