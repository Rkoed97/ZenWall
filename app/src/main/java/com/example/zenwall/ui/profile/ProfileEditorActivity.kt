package com.example.zenwall.ui.profile

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.zenwall.data.ProfileRepository
import com.example.zenwall.ui.theme.ZenWallTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class ProfileEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra("fromSettings", false) != true) { finish(); return }
        val profileId = intent?.getLongExtra("profileId", -1L) ?: -1L
        setContent { ZenWallTheme { ProfileEditorScreen(profileId = profileId.takeIf { it > 0 }, onBack = { finish() }) } }
    }
}

private data class AppUi(val pkg: String, val label: String, val isSystem: Boolean, val icon: android.graphics.drawable.Drawable)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditorScreen(profileId: Long?, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ProfileRepository(context) }
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ProfileRepository.ProfileMode.BLACKLIST) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var allApps by remember { mutableStateOf(listOf<AppUi>()) }
    var query by remember { mutableStateOf("") }
    val scope = remember { MainScope() }

    // Load installed apps
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0).map { ai: ApplicationInfo ->
            AppUi(ai.packageName, ai.loadLabel(pm).toString(), (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0, ai.loadIcon(pm))
        }.filter { !it.isSystem }.sortedBy { it.label.lowercase() }
        allApps = apps
    }

    // If editing, load existing profile
    LaunchedEffect(profileId) {
        if (profileId != null) {
            val p = repo.getProfilesOnce().firstOrNull { it.id == profileId }
            if (p != null) {
                name = p.name
                mode = p.mode
                selected = p.apps.toSet()
            }
        }
    }

    val filtered = remember(allApps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) allApps else allApps.filter { it.label.lowercase().contains(q) || it.pkg.contains(q) }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (profileId == null) "New profile" else "Edit profile") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }, actions = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                scope.launch {
                    if (profileId == null) {
                        repo.createProfile(name.trim(), mode, selected.toList())
                    } else {
                        repo.updateProfile(ProfileRepository.Profile(profileId, name.trim(), mode, selected.toList(), System.currentTimeMillis(), System.currentTimeMillis()))
                    }
                    (context as? android.app.Activity)?.finish()
                }
            }) { Text("Save") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Profile name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.padding(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mode:")
                TextButton(onClick = { mode = ProfileRepository.ProfileMode.WHITELIST }, enabled = mode != ProfileRepository.ProfileMode.WHITELIST) { Text("Whitelist") }
                TextButton(onClick = { mode = ProfileRepository.ProfileMode.BLACKLIST }, enabled = mode != ProfileRepository.ProfileMode.BLACKLIST) { Text("Blacklist") }
            }
            Spacer(Modifier.padding(8.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search apps") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.padding(8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.pkg }) { app ->
                    AppSelectRow(app = app, checked = selected.contains(app.pkg)) { newChecked ->
                        selected = if (newChecked) selected + app.pkg else selected - app.pkg
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSelectRow(app: AppUi, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(modifier = Modifier.weight(1f).clickable { onCheckedChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
            val bmp = remember(app.pkg) { app.icon.toBitmap() }
            Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                Text(app.pkg, style = MaterialTheme.typography.bodySmall)
            }
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}
