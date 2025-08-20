package com.example.zenwall.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
            com.example.zenwall.ui.common.ZenBottomBar(
                selected = com.example.zenwall.ui.common.BottomTab.Settings,
                onHome = { context.startActivity(android.content.Intent(context, com.example.zenwall.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)) },
                onOverview = { context.startActivity(android.content.Intent(context, HistoryActivity::class.java)) },
                onApps = { context.startActivity(android.content.Intent(context, AppsActivity::class.java)) },
                onSettings = { /* already here */ }
            )
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
            Spacer(Modifier.height(8.dp))
            ModeSegmentedControlBoolean(
                selected = mode,
                onSelected = { enabled ->
                    vm.setWhitelistMode(enabled)
                    Toast.makeText(context, "Restart the VPN for changes to take effect", Toast.LENGTH_LONG).show()
                }
            )

            // Profiles management entry
            Spacer(Modifier.height(24.dp))
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "profilesCardScale")
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale),
                onClick = {
                    val intent = android.content.Intent(context, com.example.zenwall.ui.profile.ProfilesListActivity::class.java)
                        .putExtra("fromSettings", true)
                    context.startActivity(intent)
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Profiles", style = MaterialTheme.typography.titleMedium)
                            Text("Create and manage filtering profiles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


@Composable
private fun ModeSegmentedControlBoolean(
    selected: Boolean,
    onSelected: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(999.dp)
    var containerWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val containerWidthDp = with(density) { containerWidthPx.toDp() }
    val segmentWidthDp = if (containerWidthPx == 0) 0.dp else containerWidthDp / 2

    val targetOffset = if (selected) 0.dp else segmentWidthDp
    val animatedOffset by animateDpAsState(targetValue = targetOffset, label = "pillOffsetSettings")

    val pillColorTarget = if (selected) scheme.primary else scheme.error
    val pillColor by animateColorAsState(targetValue = pillColorTarget, label = "pillColorSettings")
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
                    .clickable { onSelected(true) },
                contentAlignment = Alignment.Center
            ) {
                val isActive = selected
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
                    .clickable { onSelected(false) },
                contentAlignment = Alignment.Center
            ) {
                val isActive = !selected
                Text(
                    text = "Blacklist",
                    color = if (isActive) activeContentColor else inactiveColor.copy(alpha = inactiveAlpha),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
