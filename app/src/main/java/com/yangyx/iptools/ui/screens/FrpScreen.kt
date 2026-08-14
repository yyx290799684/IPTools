package com.yangyx.iptools.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangyx.iptools.data.tools.FrpClientConfig
import com.yangyx.iptools.data.tools.FrpProxyConfig
import com.yangyx.iptools.data.tools.FrpProxyType
import com.yangyx.iptools.data.tools.FrpServerConfig
import com.yangyx.iptools.ui.viewmodel.MainViewModel

import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedCard
import com.yangyx.iptools.data.tools.DownloadProxyManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrpScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: FRP Client (frpc), 1: FRP Server (frps)

    val clientStatus by viewModel.frpClientStatus.collectAsStateWithLifecycle()
    val serverStatus by viewModel.frpServerStatus.collectAsStateWithLifecycle()
    val clientLogs by viewModel.frpClientLogs.collectAsStateWithLifecycle()
    val serverLogs by viewModel.frpServerLogs.collectAsStateWithLifecycle()

    val clientConfig by viewModel.frpClientConfig.collectAsStateWithLifecycle()
    val serverConfig by viewModel.frpServerConfig.collectAsStateWithLifecycle()

    val frpStatus by viewModel.frpStatusText.collectAsStateWithLifecycle()
    val isFrpUpdating by viewModel.isFrpUpdating.collectAsStateWithLifecycle()
    val frpUpdateMsg by viewModel.frpUpdateProgressMessage.collectAsStateWithLifecycle()
    val frpSourceChoice by viewModel.frpSourceChoice.collectAsStateWithLifecycle()
    val frpCustomProxy by viewModel.frpCustomProxy.collectAsStateWithLifecycle()
    val rootState by viewModel.rootState.collectAsStateWithLifecycle()

    var showAddProxyDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // FRP Binary Status & Updater Card
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FRP 官方 Native 二进制 (frpc & frps)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "GitHub 官方 Release",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "二进制文件: native_bin/frpc & native_bin/frps\n状态: $frpStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (rootState.isRooted) Color(0xFF1B5E20) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "⚡ Root 提权: ${rootState.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (rootState.isRooted) Color(0xFF81C784) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.refreshRootState() },
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("检测 Root", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "选择更新下载源 / 加速代理节点:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DownloadProxyManager.PROXY_NODES.forEach { pair ->
                        FilterChip(
                            selected = frpSourceChoice == pair.first,
                            onClick = { viewModel.frpSourceChoice.value = pair.first },
                            label = { Text(pair.second) }
                        )
                    }
                }

                if (frpSourceChoice == "CUSTOM") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = frpCustomProxy,
                        onValueChange = { viewModel.frpCustomProxy.value = it },
                        label = { Text("自定义代理地址 (域名或完整前缀)") },
                        placeholder = { Text("例如: https://ghproxy.net/ 或 gh.dpik.top") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (frpUpdateMsg.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = frpUpdateMsg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { viewModel.updateFrpBinaryOnline() },
                    enabled = !isFrpUpdating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isFrpUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在从网络节点拉取最新 FRP...")
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("下载 / 更新最新 FRP 二进制 (frpc & frps)")
                    }
                }
            }
        }
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("FRP 客户端 (frpc)", fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Router, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("FRP 服务端 (frps)", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == 0) {
                FrpClientContent(
                    config = clientConfig,
                    status = clientStatus,
                    logs = clientLogs,
                    serverStatus = serverStatus,
                    serverConfig = serverConfig,
                    onConfigChange = { viewModel.updateFrpClientConfig(it) },
                    onStart = { viewModel.startFrpClient() },
                    onStop = { viewModel.stopFrpClient() },
                    onAddProxyClick = { showAddProxyDialog = true },
                    onToggleProxy = { id -> viewModel.toggleFrpProxyRule(id) },
                    onDeleteProxy = { id -> viewModel.removeFrpProxyRule(id) }
                )
            } else {
                FrpServerContent(
                    config = serverConfig,
                    status = serverStatus,
                    logs = serverLogs,
                    onConfigChange = { viewModel.updateFrpServerConfig(it) },
                    onStart = { viewModel.startFrpServer() },
                    onStop = { viewModel.stopFrpServer() }
                )
            }
        }
    }

    if (showAddProxyDialog) {
        AddProxyRuleDialog(
            onDismiss = { showAddProxyDialog = false },
            onConfirm = { rule ->
                viewModel.addFrpProxyRule(rule)
                showAddProxyDialog = false
            }
        )
    }
}

@Composable
private fun FrpClientContent(
    config: FrpClientConfig,
    status: com.yangyx.iptools.data.tools.FrpStatus,
    logs: List<String>,
    serverStatus: com.yangyx.iptools.data.tools.FrpStatus,
    serverConfig: FrpServerConfig,
    onConfigChange: (FrpClientConfig) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAddProxyClick: () -> Unit,
    onToggleProxy: (String) -> Unit,
    onDeleteProxy: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Local frps status hint
        if (serverStatus.isRunning) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "💡 本机 FRP 服务端已在运行中 (监听端口 :${serverConfig.bindPort})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "要测试本机穿透，客户端地址请填 127.0.0.1，端口填 ${serverConfig.bindPort}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        TextButton(
                            onClick = {
                                onConfigChange(
                                    config.copy(
                                        serverAddr = "127.0.0.1",
                                        serverPort = serverConfig.bindPort,
                                        authToken = serverConfig.authToken
                                    )
                                )
                            },
                            enabled = !status.isRunning
                        ) {
                            Text("一键填入")
                        }
                    }
                }
            }
        }

        // Status & Connection Config Card
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (status.isRunning)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "FRP 客户端配置",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "映射本机或局域网 (LAN) 主机端口到远程服务端",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (status.isRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                        ) {
                            Text(
                                text = if (status.isRunning) "已建立连接" else "未连接",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = config.serverAddr,
                        onValueChange = { onConfigChange(config.copy(serverAddr = it)) },
                        label = { Text("服务端地址 (Server Address)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !status.isRunning
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                onConfigChange(
                                    config.copy(
                                        serverAddr = "127.0.0.1",
                                        serverPort = if (serverStatus.isRunning) serverConfig.bindPort else config.serverPort,
                                        authToken = if (serverStatus.isRunning) serverConfig.authToken else config.authToken
                                    )
                                )
                            },
                            enabled = !status.isRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("快捷: 本机 (127.0.0.1)", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onConfigChange(config.copy(serverAddr = "")) },
                            enabled = !status.isRunning && config.serverAddr.isNotEmpty()
                        ) {
                            Text("清空地址", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = if (config.serverPort == 0) "" else config.serverPort.toString(),
                            onValueChange = {
                                val p = it.toIntOrNull() ?: 0
                                onConfigChange(config.copy(serverPort = p))
                            },
                            label = { Text("服务端端口") },
                            placeholder = { Text("例如 7000") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !status.isRunning
                        )

                        OutlinedTextField(
                            value = config.authToken,
                            onValueChange = { onConfigChange(config.copy(authToken = it)) },
                            label = { Text("认证 Token") },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !status.isRunning
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (status.isRunning) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "流量统计: RX ${formatBytes(status.rxBytes)} | TX ${formatBytes(status.txBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = onStop,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("停止 frpc")
                            }
                        }
                    } else {
                        Button(
                            onClick = onStart,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("启动 FRP 客户端 (Connect frps)")
                        }
                    }
                }
            }
        }

        // Proxy Rules Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "穿透代理映射规则 (${config.proxies.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onAddProxyClick,
                    enabled = !status.isRunning
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加规则")
                }
            }
        }

        // Preset Quick Templates
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        val preset = FrpProxyConfig(
                            name = "web_${(100..999).random()}",
                            type = FrpProxyType.TCP,
                            localIp = "127.0.0.1",
                            localPort = 8080,
                            remotePort = (6000..7000).random()
                        )
                        onConfigChange(config.copy(proxies = config.proxies + preset))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !status.isRunning
                ) {
                    Text("预设: 本机 HTTP", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = {
                        val preset = FrpProxyConfig(
                            name = "nas_${(100..999).random()}",
                            type = FrpProxyType.TCP,
                            localIp = "192.168.1.100",
                            localPort = 80,
                            remotePort = (7001..8000).random()
                        )
                        onConfigChange(config.copy(proxies = config.proxies + preset))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !status.isRunning
                ) {
                    Text("预设: 局域网 NAS", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = {
                        val preset = FrpProxyConfig(
                            name = "socks5_${(100..999).random()}",
                            type = FrpProxyType.SOCKS5,
                            localIp = "127.0.0.1",
                            localPort = 1080,
                            remotePort = 6003
                        )
                        onConfigChange(config.copy(proxies = config.proxies + preset))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !status.isRunning
                ) {
                    Text("预设: SOCKS5", fontSize = 11.sp)
                }
            }
        }

        items(config.proxies) { proxy ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (proxy.isEnabled)
                        MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = when (proxy.type) {
                                    FrpProxyType.TCP -> MaterialTheme.colorScheme.primaryContainer
                                    FrpProxyType.UDP -> MaterialTheme.colorScheme.secondaryContainer
                                    FrpProxyType.HTTP -> MaterialTheme.colorScheme.tertiaryContainer
                                    FrpProxyType.HTTPS -> MaterialTheme.colorScheme.surfaceVariant
                                    FrpProxyType.SOCKS5 -> MaterialTheme.colorScheme.errorContainer
                                }
                            ) {
                                Text(
                                    text = proxy.type.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = proxy.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "本地/局域网: ${proxy.localIp}:${proxy.localPort}  ==>  远程服务端端口: :${proxy.remotePort}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = proxy.isEnabled,
                        onCheckedChange = { onToggleProxy(proxy.id) },
                        enabled = !status.isRunning
                    )

                    IconButton(
                        onClick = { onDeleteProxy(proxy.id) },
                        enabled = !status.isRunning
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Active Tunnels Card
        if (status.activeTunnels.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "活跃的穿透隧道 (${status.activeTunnels.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        status.activeTunnels.forEach { tunnel ->
                            Text(
                                text = "• $tunnel",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Terminal Log Terminal
        item {
            LogTerminalCard(logs = logs, title = "frpc 实时日志终端")
        }
    }
}

@Composable
private fun FrpServerContent(
    config: FrpServerConfig,
    status: com.yangyx.iptools.data.tools.FrpStatus,
    logs: List<String>,
    onConfigChange: (FrpServerConfig) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status & Config Card
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (status.isRunning)
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "FRP 服务端配置",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "在本机启动 FRP 中转服务 (frps) 接受穿透节点",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (status.isRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                        ) {
                            Text(
                                text = if (status.isRunning) "服务端运行中" else "未启动",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = if (config.bindPort == 0) "" else config.bindPort.toString(),
                            onValueChange = {
                                val p = it.toIntOrNull() ?: 0
                                onConfigChange(config.copy(bindPort = p))
                            },
                            label = { Text("绑定端口 (Bind Port)") },
                            placeholder = { Text("例如 7000") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !status.isRunning
                        )

                        OutlinedTextField(
                            value = config.authToken,
                            onValueChange = { onConfigChange(config.copy(authToken = it)) },
                            label = { Text("认证密钥 (Token)") },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !status.isRunning
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = if (config.dashboardPort == 0) "" else config.dashboardPort.toString(),
                            onValueChange = {
                                val p = it.toIntOrNull() ?: 0
                                onConfigChange(config.copy(dashboardPort = p))
                            },
                            label = { Text("仪表盘端口") },
                            placeholder = { Text("例如 7500") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !status.isRunning
                        )

                        OutlinedTextField(
                            value = if (config.maxPoolCount == 0) "" else config.maxPoolCount.toString(),
                            onValueChange = {
                                val m = it.toIntOrNull() ?: 0
                                onConfigChange(config.copy(maxPoolCount = m))
                            },
                            label = { Text("最大池大小") },
                            placeholder = { Text("例如 50") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !status.isRunning
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (status.isRunning) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "连接客户端: ${status.activeConnections} | 映射隧道: ${status.activeTunnels.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "流量: RX ${formatBytes(status.rxBytes)} | TX ${formatBytes(status.txBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Button(
                                onClick = onStop,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("停止 frps")
                            }
                        }
                    } else {
                        Button(
                            onClick = onStart,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("启动 FRP 服务端 (Listen Port :${config.bindPort})")
                        }
                    }
                }
            }
        }

        // Active Tunnels on Server
        if (status.activeTunnels.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "服务端建立的穿透端口",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        status.activeTunnels.forEach { tunnel ->
                            Text(
                                text = "• $tunnel",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Terminal Log Terminal
        item {
            LogTerminalCard(logs = logs, title = "frps 实时日志终端")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProxyRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (FrpProxyConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(FrpProxyType.TCP) }
    var localIp by remember { mutableStateOf("") }
    var localPortText by remember { mutableStateOf("") }
    var remotePortText by remember { mutableStateOf("") }

    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加穿透代理规则", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称 (Rule Name)") },
                    placeholder = { Text("例如 web_rule") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = type.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("代理协议类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        FrpProxyType.values().forEach { pType ->
                            DropdownMenuItem(
                                text = { Text(pType.label) },
                                onClick = {
                                    type = pType
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = localIp,
                    onValueChange = { localIp = it },
                    label = { Text("目标 IP") },
                    placeholder = { Text("例如 127.0.0.1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = localPortText,
                        onValueChange = { localPortText = it },
                        label = { Text("目标端口") },
                        placeholder = { Text("例如 8080") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = remotePortText,
                        onValueChange = { remotePortText = it },
                        label = { Text("远程映射端口") },
                        placeholder = { Text("例如 6001") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lPort = localPortText.toIntOrNull() ?: 0
                    val rPort = remotePortText.toIntOrNull() ?: 0
                    onConfirm(
                        FrpProxyConfig(
                            name = name,
                            type = type,
                            localIp = localIp,
                            localPort = lPort,
                            remotePort = rPort
                        )
                    )
                }
            ) {
                Text("确定添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun LogTerminalCard(logs: List<String>, title: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "共 ${logs.size} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = {
                            if (logs.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(logs.joinToString("\n")))
                                Toast.makeText(context, "已复制 $title 日志", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制日志",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        color = Color(0xFF1E1E1E),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(10.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "暂无日志，启动服务后显示...",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        logs.forEach { log ->
                            Text(
                                text = log,
                                color = if (log.contains("❌") || log.contains("失败")) Color(0xFFFF6B6B)
                                else if (log.contains("✅") || log.contains("成功")) Color(0xFF6BCB77)
                                else Color(0xFFDCDCDC),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024f
    return if (kb > 1024) {
        String.format("%.2f MB", kb / 1024f)
    } else {
        String.format("%.1f KB", kb)
    }
}
