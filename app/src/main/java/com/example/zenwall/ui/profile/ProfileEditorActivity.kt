package com.example.zenwall.ui.profile

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(4.dp))
                ModeSegmentedControl(
                    selected = mode,
                    onSelected = { selectedMode -> mode = selectedMode }
                )
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
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).clickable { onCheckedChange(!checked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bmp = remember(app.pkg) { app.icon.toBitmap() }
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label)
                Text(app.pkg, style = MaterialTheme.typography.bodySmall)
            }
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}


@Composable
private fun ModeSegmentedControl(
    selected: ProfileRepository.ProfileMode,
    onSelected: (ProfileRepository.ProfileMode) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(999.dp)
    var containerWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val containerWidthDp = with(density) { containerWidthPx.toDp() }
    val segmentWidthDp = if (containerWidthPx == 0) 0.dp else containerWidthDp / 2

    val targetOffset = when (selected) {
        ProfileRepository.ProfileMode.WHITELIST -> 0.dp
        ProfileRepository.ProfileMode.BLACKLIST -> segmentWidthDp
    }
    val animatedOffset by animateDpAsState(targetValue = targetOffset, label = "pillOffset")

    val pillColorTarget = when (selected) {
        ProfileRepository.ProfileMode.WHITELIST -> scheme.primary
        ProfileRepository.ProfileMode.BLACKLIST -> scheme.error
    }
    val pillColor by androidx.compose.animation.animateColorAsState(targetValue = pillColorTarget, label = "pillColor")
    val borderColor = scheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .onGloballyPositioned { containerWidthPx = it.size.width }
    ) {
        if (segmentWidthDp > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(segmentWidthDp)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(pillColor)
            )
        }
        Row(Modifier.fillMaxSize()) {
            val activeContentColor = contentColorFor(pillColor)
            val inactiveColor = scheme.onSurface
            val inactiveAlpha = 0.7f

            // Whitelist segment
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelected(ProfileRepository.ProfileMode.WHITELIST) },
                contentAlignment = Alignment.Center
            ) {
                val isActive = selected == ProfileRepository.ProfileMode.WHITELIST
                Text(
                    text = "Whitelist",
                    color = if (isActive) activeContentColor else inactiveColor.copy(alpha = inactiveAlpha),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            // Blacklist segment
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelected(ProfileRepository.ProfileMode.BLACKLIST) },
                contentAlignment = Alignment.Center
            ) {
                val isActive = selected == ProfileRepository.ProfileMode.BLACKLIST
                Text(
                    text = "Blacklist",
                    color = if (isActive) activeContentColor else inactiveColor.copy(alpha = inactiveAlpha),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
