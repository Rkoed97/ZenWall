package com.example.zenwall.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Centralized bottom navigation bar for the app.
 * Icon-only (no text labels), with accessibility-friendly contentDescriptions.
 */
enum class BottomTab { Home, Overview, Apps, Settings }

@Composable
fun ZenBottomBar(
    selected: BottomTab,
    onHome: () -> Unit,
    onOverview: () -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant

    BottomAppBar {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onHome) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = "Home",
                    tint = if (selected == BottomTab.Home) selectedColor else unselectedColor
                )
            }
            IconButton(onClick = onOverview) {
                Icon(
                    imageVector = Icons.Filled.Dashboard,
                    contentDescription = "Overview",
                    tint = if (selected == BottomTab.Overview) selectedColor else unselectedColor
                )
            }
            IconButton(onClick = onApps) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = "Manage Apps",
                    tint = if (selected == BottomTab.Apps) selectedColor else unselectedColor
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = if (selected == BottomTab.Settings) selectedColor else unselectedColor
                )
            }
        }
    }
}
