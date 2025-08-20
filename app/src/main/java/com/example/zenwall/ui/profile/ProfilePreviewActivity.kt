package com.example.zenwall.ui.profile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.zenwall.data.ProfileRepository
import com.example.zenwall.ui.theme.ZenWallTheme

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
    var query by remember { mutableStateOf("") }

    val filtered = remember(profile, query) {
        val q = query.trim().lowercase()
        val apps = profile?.apps ?: emptyList()
        if (q.isEmpty()) apps else apps.filter { it.lowercase().contains(q) }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(profile?.name ?: "Preview") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }, actions = {
            val ctx = LocalContext.current
            if (profile != null) {
                androidx.compose.material3.TextButton(onClick = {
                    ctx.startActivity(android.content.Intent(ctx, ProfileEditorActivity::class.java)
                        .putExtra("fromSettings", true)
                        .putExtra("profileId", profile!!.id))
                }) { Text("Edit") }
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
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered) { pkg ->
                    Text(pkg, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }
            }
        }
    }
}
