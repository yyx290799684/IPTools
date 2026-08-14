package com.yangyx.iptools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangyx.iptools.ui.viewmodel.AppScreen
import com.yangyx.iptools.ui.viewmodel.MainViewModel

@Composable
fun AppDrawerContent(
    viewModel: MainViewModel,
    onScreenSelected: (AppScreen) -> Unit
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val activeTaskName by viewModel.activeTaskName.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp)
    ) {
        // Drawer Header
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "IPTools 网路工具箱",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "IPv4 / IPv6 综合网络诊断套件",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                activeTaskName?.let { task ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.height(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "后台运行: $task",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Navigation Items List
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            DrawerSectionHeader("核心网络工具")

            DrawerMenuItem(
                screen = AppScreen.HOME,
                icon = Icons.Default.Home,
                selected = currentScreen == AppScreen.HOME,
                onSelect = onScreenSelected
            )
            DrawerMenuItem(
                screen = AppScreen.PING,
                icon = Icons.Default.NetworkCheck,
                selected = currentScreen == AppScreen.PING,
                onSelect = onScreenSelected
            )
            DrawerMenuItem(
                screen = AppScreen.TRACE,
                icon = Icons.Default.Router,
                selected = currentScreen == AppScreen.TRACE,
                onSelect = onScreenSelected
            )
            DrawerMenuItem(
                screen = AppScreen.PORT_SCAN,
                icon = Icons.Default.Radar,
                selected = currentScreen == AppScreen.PORT_SCAN,
                onSelect = onScreenSelected
            )
            DrawerMenuItem(
                screen = AppScreen.FSCAN,
                icon = Icons.Default.Terminal,
                selected = currentScreen == AppScreen.FSCAN,
                onSelect = onScreenSelected
            )
            DrawerMenuItem(
                screen = AppScreen.IPERF,
                icon = Icons.Default.Speed,
                selected = currentScreen == AppScreen.IPERF,
                onSelect = onScreenSelected
            )
            DrawerMenuItem(
                screen = AppScreen.DNS,
                icon = Icons.Default.Dns,
                selected = currentScreen == AppScreen.DNS,
                onSelect = onScreenSelected
            )
            DrawerMenuItem(
                screen = AppScreen.WHOIS,
                icon = Icons.Default.LocationOn,
                selected = currentScreen == AppScreen.WHOIS,
                onSelect = onScreenSelected
            )
            DrawerMenuItem(
                screen = AppScreen.IP_GEO,
                icon = Icons.Default.LocationOn,
                selected = currentScreen == AppScreen.IP_GEO,
                onSelect = onScreenSelected
            )
            DrawerMenuItem(
                screen = AppScreen.FRP,
                icon = Icons.Default.SwapHoriz,
                selected = currentScreen == AppScreen.FRP,
                onSelect = onScreenSelected
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DrawerSectionHeader("数据与历史")

            DrawerMenuItem(
                screen = AppScreen.FAVORITES,
                icon = Icons.Default.Bookmark,
                selected = currentScreen == AppScreen.FAVORITES,
                onSelect = onScreenSelected
            )
            DrawerMenuItem(
                screen = AppScreen.HISTORY,
                icon = Icons.Default.History,
                selected = currentScreen == AppScreen.HISTORY,
                onSelect = onScreenSelected
            )
        }

        HorizontalDivider()

        // Dark Mode Toggle Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isDarkMode == true) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "暗黑主题模式",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isDarkMode == true,
                onCheckedChange = { isDark ->
                    viewModel.toggleDarkMode(isDark)
                }
            )
        }
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun DrawerMenuItem(
    screen: AppScreen,
    icon: ImageVector,
    selected: Boolean,
    onSelect: (AppScreen) -> Unit
) {
    NavigationDrawerItem(
        label = { Text(screen.title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        icon = { Icon(imageVector = icon, contentDescription = null) },
        selected = selected,
        onClick = { onSelect(screen) },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
