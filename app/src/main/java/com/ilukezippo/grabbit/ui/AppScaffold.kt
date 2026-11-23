package com.ilukezippo.grabbit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilukezippo.grabbit.R
import com.ilukezippo.grabbit.download.Downloads

@Composable
fun AppScaffold(vm: GrabViewModel, current: Int, onTab: (Int) -> Unit) {
    val active = Downloads.tasks.collectAsStateWithLifecycle().value.count { it.active }
        Scaffold(
            containerColor = G.bg,
            bottomBar = {
                NavigationBar(containerColor = G.surface, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                    val items = listOf(
                        Triple(R.string.tab_home, Icons.Rounded.Bolt, Icons.Rounded.Bolt),
                        Triple(R.string.tab_downloads, Icons.Rounded.Download, Icons.Rounded.Download),
                        Triple(R.string.tab_tools, Icons.Rounded.AutoAwesome, Icons.Rounded.AutoAwesome),
                        Triple(R.string.tab_settings, Icons.Rounded.Settings, Icons.Rounded.Settings),
                    )
                    items.forEachIndexed { i, (label, icon, _) ->
                        NavigationBarItem(
                            selected = current == i, onClick = { onTab(i) },
                            icon = {
                                if (i == 1 && active > 0) BadgedBox({ androidx.compose.material3.Badge(containerColor = G.pink) { Text("$active", fontSize = 10.sp) } }) { Icon(icon, null) }
                                else Icon(icon, null)
                            },
                            label = { Text(stringResource(label)) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = G.text, indicatorColor = G.violet.copy(alpha = .35f), unselectedIconColor = G.muted, unselectedTextColor = G.muted, selectedTextColor = G.text),
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).statusBarsPadding()) {
                when (current) {
                    0 -> HomeScreen(vm) { onTab(1) }
                    1 -> DownloadsScreen()
                    2 -> ToolsScreen()
                    else -> SettingsScreen()
                }
            }
        }
}
