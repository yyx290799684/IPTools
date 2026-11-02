package com.yangyx.iptools.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangyx.iptools.data.tools.DownloadProxyManager
import com.yangyx.iptools.data.tools.IperfPoint
import com.yangyx.iptools.data.tools.ProcessExecMode
import com.yangyx.iptools.ui.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IperfScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val serverHost by viewModel.iperfServer.collectAsStateWithLifecycle()
    val serverPort by viewModel.iperfPort.collectAsStateWithLifecycle()
    val duration by viewModel.iperfDuration.collectAsStateWithLifecycle()
    val protocol by viewModel.iperfProtocol.collectAsStateWithLifecycle()
    val iperfMode by viewModel.iperfMode.collectAsStateWithLifecycle()
    val bandwidth by viewModel.iperfBandwidth.collectAsStateWithLifecycle()
    val parallel by viewModel.iperfParallel.collectAsStateWithLifecycle()
    val isReverse by viewModel.iperfIsReverse.collectAsStateWithLifecycle()

    val binaryStatus by viewModel.iperfBinaryStatusText.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isIperfDownloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.iperfDownloadProgress.collectAsStateWithLifecycle()
    val isVerifying by viewModel.isIperfVerifying.collectAsStateWithLifecycle()
    val verifyStatusText by viewModel.iperfVerifyStatusText.collectAsStateWithLifecycle()
    val sourceChoice by viewModel.iperfSourceChoice.collectAsStateWithLifecycle()
    val customProxy by viewModel.iperfCustomProxy.collectAsStateWithLifecycle()
    val localIps by viewModel.iperfLocalIps.collectAsStateWithLifecycle()
    val rootState by viewModel.rootState.collectAsStateWithLifecycle()
    val iperfExecMode by viewModel.iperfExecMode.collectAsStateWithLifecycle()

    val points by viewModel.iperfPoints.collectAsStateWithLifecycle()
    val isRunning by viewModel.isIperfRunning.collectAsStateWithLifecycle()

    var isBinaryCardExpanded by remember { mutableStateOf(false) }
    val logListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.refreshIperfStatus()
    }

    // Calculate real-time stats for live dashboard
    val intervalPoints = points.filter { !it.isComplete && it.sec > 0 }
    val latestPoint = intervalPoints.lastOrNull()
    val completedPoint = points.lastOrNull { it.isComplete }

    val currentSpeedMbps = latestPoint?.bitrateMbps ?: 0f
    val currentTransferredMB = intervalPoints.sumOf { it.transferMB.toDouble() }.toFloat()
    val currentSec = latestPoint?.sec ?: 0
    val progressFraction = if (duration > 0) (currentSec.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    val logMessages = points.mapNotNull { if (it.logMessage.isNotBlank()) it.logMessage else null }
    val activeClient = points.lastOrNull { it.clientIp.isNotBlank() }?.clientIp ?: ""

    val primaryLocalIp = localIps.firstOrNull()?.split(" ")?.firstOrNull() ?: "192.168.1.100"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "iPerf3 网络性能与带宽测试",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "支持官方原生 CLI 模式 (iperf3 -s / -c) 与 JNI 高兼容引擎",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 1. Native Binary Management Card (Styled after Fscan)
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isBinaryCardExpanded = !isBinaryCardExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "iPerf3 官方 Native 二进制引擎",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "GitHub Release",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { isBinaryCardExpanded = !isBinaryCardExpanded },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isBinaryCardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isBinaryCardExpanded) "收起" else "展开"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "状态: $binaryStatus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isBinaryCardExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "二进制文件: native_bin/iperf3",
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
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("检测 Root", fontSize = 12.sp, maxLines = 1, softWrap = false)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "进程启动执行权限模式:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = iperfExecMode == ProcessExecMode.AUTO,
                                onClick = { viewModel.iperfExecMode.value = ProcessExecMode.AUTO },
                                label = { Text("⚡ 智能自动 (优先非Root/Linker)") }
                            )
                            FilterChip(
                                selected = iperfExecMode == ProcessExecMode.NON_ROOT,
                                onClick = { viewModel.iperfExecMode.value = ProcessExecMode.NON_ROOT },
                                label = { Text("🛡️ 强制非 Root 普通权限") }
                            )
                            FilterChip(
                                selected = iperfExecMode == ProcessExecMode.ROOT,
                                onClick = { viewModel.iperfExecMode.value = ProcessExecMode.ROOT },
                                label = { Text("🔑 仅使用 Root 权限") }
                            )
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
                                    selected = sourceChoice == pair.first,
                                    onClick = { viewModel.iperfSourceChoice.value = pair.first },
                                    label = { Text(pair.second) }
                                )
                            }
                        }

                        if (sourceChoice == "CUSTOM") {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customProxy,
                                onValueChange = { viewModel.iperfCustomProxy.value = it },
                                label = { Text("自定义代理地址 (域名或完整前缀)") },
                                placeholder = { Text("例如: https://ghproxy.net/ 或 gh.dpik.top") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (downloadProgress.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = downloadProgress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (isDownloading) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        if (verifyStatusText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = verifyStatusText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.downloadIperfBinary() },
                                enabled = !isDownloading && !isVerifying,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("更新中...", fontSize = 12.sp)
                                } else {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("下载/更新引擎", fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.verifyIperfBinary() },
                                enabled = !isDownloading && !isVerifying,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isVerifying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("验证中...", fontSize = 12.sp)
                                } else {
                                    Icon(imageVector = Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("验证 (iperf3 -v)", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Configuration & Parameter Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "测试模式与节点参数",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Mode selector: Client Mode vs Server Mode
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = iperfMode == "CLIENT",
                            onClick = { viewModel.iperfMode.value = "CLIENT" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Client 客户端发包")
                        }
                        SegmentedButton(
                            selected = iperfMode == "SERVER",
                            onClick = { viewModel.iperfMode.value = "SERVER" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Server 服务端监听")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (iperfMode == "SERVER") {
                        // Server Mode Configuration & Spacious Command Display
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Wifi,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "本机局域网 IP (供其他设备连接)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.refreshIperfStatus() },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "刷新",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                localIps.forEach { ipStr ->
                                    Text(
                                        text = "• $ipStr",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Command Block 1: Server Command
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "官方服务端指令 (当前设备运行):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "iperf3 -s -p $serverPort -i 1",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    val cmd = "iperf3 -s -p $serverPort -i 1"
                                                    clipboardManager.setText(AnnotatedString(cmd))
                                                    Toast.makeText(context, "已复制服务端指令: $cmd", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "复制服务端指令",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Command Block 2: Client Command to Connect
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "客户端测速指令 (电脑/其他手机执行):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "iperf3 -c $primaryLocalIp -p $serverPort",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    val cmd = "iperf3 -c $primaryLocalIp -p $serverPort"
                                                    clipboardManager.setText(AnnotatedString(cmd))
                                                    Toast.makeText(context, "已复制客户端指令: $cmd", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "复制客户端指令",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = serverPort.toString(),
                            onValueChange = { viewModel.iperfPort.value = it.toIntOrNull() ?: 5201 },
                            label = { Text("服务端监听端口 (默认 5201)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        // Client Mode Configuration
                        OutlinedTextField(
                            value = serverHost,
                            onValueChange = { viewModel.iperfServer.value = it },
                            label = { Text("iPerf3 服务器地址 / IP (例如 192.168.1.100)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = serverPort.toString(),
                                onValueChange = { viewModel.iperfPort.value = it.toIntOrNull() ?: 5201 },
                                label = { Text("端口 (默认 5201)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = duration.toString(),
                                onValueChange = { viewModel.iperfDuration.value = it.toIntOrNull() ?: 5 },
                                label = { Text("时长 -t (秒)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = bandwidth,
                                onValueChange = { viewModel.iperfBandwidth.value = it },
                                label = { Text("带宽限制 -b (0=不限)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = parallel.toString(),
                                onValueChange = { viewModel.iperfParallel.value = it.toIntOrNull() ?: 1 },
                                label = { Text("并行线程 -P (1-10)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isReverse,
                                onCheckedChange = { viewModel.iperfIsReverse.value = it }
                            )
                            Text(
                                text = "反向压测 -R (Server 向 手机发包/测手机下载速率)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = protocol == "TCP",
                                onClick = { viewModel.iperfProtocol.value = "TCP" },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("TCP 传输协议")
                            }
                            SegmentedButton(
                                selected = protocol == "UDP",
                                onClick = { viewModel.iperfProtocol.value = "UDP" },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("UDP 传输协议")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startIperf() },
                            enabled = !isRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isRunning) {
                                    if (iperfMode == "SERVER") "服务端监听运行中..." else "iPerf 客户端压测中..."
                                } else {
                                    if (iperfMode == "SERVER") "启动 iPerf3 Server 官方监听 (-s)" else "启动 iPerf3 客户端测速 (-c)"
                                }
                            )
                        }

                        if (isRunning) {
                            Button(
                                onClick = { viewModel.stopIperf() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("停止")
                            }
                        }
                    }
                }
            }
        }

        // 3. Live Real-Time Dashboard Card (Active during test or after completion)
        if (isRunning || points.isNotEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isRunning) {
                                        if (iperfMode == "SERVER") "服务端实时监听看板" else "实时带宽测速看板"
                                    } else "测速概览看板",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (isRunning) {
                                        if (iperfMode == "SERVER") "🟢 监听中: 0.0.0.0:$serverPort" else "正在发包测速..."
                                    } else "已完成",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isRunning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (activeClient.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "已连接客户端: $activeClient",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Large Speedometer Gauge Metric Display
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format("%.2f", if (completedPoint != null) completedPoint.bitrateMbps else currentSpeedMbps),
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Mbits / sec (实时吞吐速率)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isRunning && iperfMode == "CLIENT") {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3-Tile Secondary Metric Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricTile(
                                label = if (iperfMode == "SERVER") "已运行时间" else "进度时长",
                                value = if (completedPoint != null) "${duration} / ${duration}s" else "${currentSec}s",
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                label = "已传输数据",
                                value = String.format("%.2f MB", if (completedPoint != null) completedPoint.transferMB else currentTransferredMB),
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                label = if (iperfMode == "SERVER") "监听端口" else "并发线程",
                                value = if (iperfMode == "SERVER") ":$serverPort" else "$parallel Stream",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Real-Time Bitrate Chart Card
            item {
                IperfBitrateChart(points = points)
            }
        }

        // 4. Final Report Summary Card
        completedPoint?.let { completed ->
            if (completed.summaryText.isNotBlank()) {
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "iPerf3 最终测试报告",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )

                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("iPerf Summary", completed.summaryText))
                                        Toast.makeText(context, "已复制测试报告到剪贴板", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "复制报告",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = completed.summaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        // 5. Terminal Output & Socket Stream Logs
        if (logMessages.isNotEmpty()) {
            item {
                LaunchedEffect(logMessages.size, isRunning) {
                    if (logMessages.isNotEmpty() && isRunning) {
                        try {
                            logListState.scrollToItem((logMessages.size - 1).coerceAtLeast(0))
                        } catch (_: Exception) {}
                    }
                }

                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "iPerf3 终端控制与输出日志 (${logMessages.size} 行)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(logMessages.joinToString("\n")))
                                    Toast.makeText(context, "已复制日志到剪贴板", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "复制日志",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 360.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1E1E))
                                .padding(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                LazyColumn(
                                    state = logListState,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(
                                        items = logMessages,
                                        key = { index, _ -> index }
                                    ) { _, log ->
                                        val textColor = when {
                                            log.contains("Accepted connection", ignoreCase = true) -> Color(0xFF00E676)
                                            log.contains("Server listening", ignoreCase = true) -> Color(0xFF00E5FF)
                                            log.startsWith("🟢") -> Color(0xFF69F0AE)
                                            log.startsWith("⚠️") || log.contains("error", ignoreCase = true) -> Color(0xFFFF8A80)
                                            log.contains("sec") && log.contains("Mbits/sec") -> Color(0xFF81D4FA)
                                            else -> Color(0xFFCFD8DC)
                                        }

                                        Text(
                                            text = log,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = textColor,
                                            lineHeight = 15.sp,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IperfBitrateChart(points: List<IperfPoint>) {
    val intervalPoints = points.filter { !it.isComplete && it.sec > 0 }
    if (intervalPoints.isEmpty()) return

    val maxRate = (intervalPoints.maxOfOrNull { it.bitrateMbps } ?: 100f).coerceAtLeast(10f) * 1.2f
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "速率变化曲线 (Mbps / 秒)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "峰值: ${String.format("%.1f", intervalPoints.maxOf { it.bitrateMbps })} Mbps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    val width = size.width
                    val height = size.height

                    val paddingLeft = 32.dp.toPx()
                    val paddingBottom = 20.dp.toPx()
                    val chartWidth = width - paddingLeft
                    val chartHeight = height - paddingBottom

                    // Horizontal Grid Lines
                    val gridSteps = 3
                    for (i in 0..gridSteps) {
                        val y = chartHeight * (1f - i.toFloat() / gridSteps)
                        drawLine(
                            color = gridColor,
                            start = Offset(paddingLeft, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                    }

                    val n = intervalPoints.size
                    val stepX = if (n > 1) chartWidth / (n - 1) else chartWidth

                    val linePath = Path()
                    val fillPath = Path()

                    fillPath.moveTo(paddingLeft, chartHeight)

                    intervalPoints.forEachIndexed { index, pt ->
                        val x = paddingLeft + (if (n > 1) index * stepX else chartWidth / 2)
                        val ratio = (pt.bitrateMbps / maxRate).coerceIn(0f, 1f)
                        val y = chartHeight * (1f - ratio)

                        if (index == 0) {
                            linePath.moveTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                        }
                        fillPath.lineTo(x, y)
                    }

                    fillPath.lineTo(paddingLeft + (if (n > 1) (n - 1) * stepX else chartWidth / 2), chartHeight)
                    fillPath.close()

                    // Fill Gradient
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.35f),
                                lineColor.copy(alpha = 0.02f)
                            )
                        )
                    )

                    // Draw Line Path
                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Point Nodes
                    intervalPoints.forEachIndexed { index, pt ->
                        val x = paddingLeft + (if (n > 1) index * stepX else chartWidth / 2)
                        val ratio = (pt.bitrateMbps / maxRate).coerceIn(0f, 1f)
                        val y = chartHeight * (1f - ratio)

                        drawCircle(
                            color = lineColor,
                            radius = 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
