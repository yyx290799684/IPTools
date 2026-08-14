package com.yangyx.iptools.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangyx.iptools.data.tools.DownloadProxyManager
import com.yangyx.iptools.ui.viewmodel.MainViewModel

@Composable
fun FscanScreen(viewModel: MainViewModel) {
    val targetText by viewModel.fscanTargetText.collectAsStateWithLifecycle()
    val portRangeText by viewModel.fscanPortRangeText.collectAsStateWithLifecycle()
    val threadCountText by viewModel.fscanThreadCountText.collectAsStateWithLifecycle()
    val timeoutSecText by viewModel.fscanTimeoutSecText.collectAsStateWithLifecycle()
    val disableBrute by viewModel.fscanDisableBrute.collectAsStateWithLifecycle()
    val disablePing by viewModel.fscanDisablePing.collectAsStateWithLifecycle()

    val progress by viewModel.fscanProgress.collectAsStateWithLifecycle()
    val isRunning by viewModel.isFscanRunning.collectAsStateWithLifecycle()

    val fscanStatus by viewModel.fscanStatusText.collectAsStateWithLifecycle()
    val isFscanUpdating by viewModel.isFscanUpdating.collectAsStateWithLifecycle()
    val fscanUpdateMsg by viewModel.fscanUpdateProgressMessage.collectAsStateWithLifecycle()
    val fscanSourceChoice by viewModel.fscanSourceChoice.collectAsStateWithLifecycle()
    val fscanCustomProxy by viewModel.fscanCustomProxy.collectAsStateWithLifecycle()
    val fscanVerifyStatus by viewModel.fscanVerifyStatusText.collectAsStateWithLifecycle()
    val isFscanVerifying by viewModel.isFscanVerifying.collectAsStateWithLifecycle()
    val rootState by viewModel.rootState.collectAsStateWithLifecycle()

    var selectedViewMode by remember { mutableIntStateOf(0) } // 0: 终端日志, 1: 卡片视图
    var isPortRangeExpanded by remember { mutableStateOf(false) }
    var isBinaryCardExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Fscan 综合安全扫描器",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // fscan Native Binary Status & Updater Card (Collapsible)
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
                                text = "fscan 官方 Native 二进制引擎",
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
                        text = "状态: $fscanStatus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isBinaryCardExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "二进制文件: native_bin/fscan",
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
                                    selected = fscanSourceChoice == pair.first,
                                    onClick = { viewModel.fscanSourceChoice.value = pair.first },
                                    label = { Text(pair.second) }
                                )
                            }
                        }

                        if (fscanSourceChoice == "CUSTOM") {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = fscanCustomProxy,
                                onValueChange = { viewModel.fscanCustomProxy.value = it },
                                label = { Text("自定义代理地址 (域名或完整前缀)") },
                                placeholder = { Text("例如: https://ghproxy.net/ 或 gh.dpik.top") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (fscanUpdateMsg.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = fscanUpdateMsg,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.updateFscanBinaryOnline() },
                                enabled = !isFscanUpdating && !isFscanVerifying,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isFscanUpdating) {
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
                                onClick = { viewModel.verifyFscanBinary() },
                                enabled = !isFscanUpdating && !isFscanVerifying,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isFscanVerifying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("验证中...", fontSize = 12.sp)
                                } else {
                                    Text("🧪 验证二进制", fontSize = 12.sp)
                                }
                            }
                        }

                        if (fscanVerifyStatus.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = fscanVerifyStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (fscanVerifyStatus.startsWith("✅")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { viewModel.fscanTargetText.value = it },
                        label = { Text("扫描目标 IP / 网段 / IPv6") },
                        placeholder = { Text("例: 192.168.1.1, 192.168.1.1/24, 192.168.1.1-254, 2408:8000::1") },
                        supportingText = { Text("支持单 IP、CIDR 掩码、横杠范围(1-254)及 IPv6 地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "端口范围 (${portRangeText.split(",").filter { it.isNotBlank() }.size} 个端口)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (portRangeText.length > 60) {
                            Text(
                                text = if (isPortRangeExpanded) "收起 ▲" else "展开全部 ▼",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { isPortRangeExpanded = !isPortRangeExpanded }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    OutlinedTextField(
                        value = portRangeText,
                        onValueChange = { viewModel.fscanPortRangeText.value = it },
                        label = null,
                        placeholder = { Text("例如 21, 22, 80, 443, 8000-8080 或 1-65535") },
                        maxLines = if (isPortRangeExpanded) 8 else 3,
                        minLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 端口快捷模板选择
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            AssistChip(
                                onClick = {
                                    viewModel.fscanPortRangeText.value = "21,22,23,25,53,80,81,88,110,111,135,139,143,161,389,443,445,465,502,512,513,514,515,548,554,587,623,636,873,902,993,995,1080,1099,1194,1433,1434,1521,1522,1525,1723,1883,2049,2121,2181,2200,2222,2375,2376,2379,2380,3000,3128,3268,3269,3306,3389,3690,4369,4444,4848,5000,5005,5044,5060,5432,5601,5631,5632,5671,5672,5900,5984,5985,5986,6000,6379,6380,6443,6666,6667,7001,7002,7474,7687,8000,8005,8008,8009,8080,8081,8086,8088,8089,8090,8161,8180,8443,8500,8834,8848,8880,8883,8888,9000,9001,9042,9080,9090,9092,9093,9160,9200,9300,9418,9443,9999,10000,10051,10250,10255,11211,15672,22222,26379,27017,27018,50000,50070,50075,61613,61614,61616"
                                },
                                label = { Text("🚀 Top 1000 常用") }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    viewModel.fscanPortRangeText.value = "1-65535"
                                },
                                label = { Text("🌐 全端口 (1-65535)") }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    viewModel.fscanPortRangeText.value = "21,22,80,443,445,1433,3306,3389,6379,8080"
                                },
                                label = { Text("⚡ 10 高危端口") }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    viewModel.fscanPortRangeText.value = "80,81,88,443,8000,8008,8080,8081,8088,8089,8090,8180,8443,8888,9000,9443"
                                },
                                label = { Text("💻 Web 端口") }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    viewModel.fscanPortRangeText.value = "1433,1521,3306,5432,6379,11211,27017"
                                },
                                label = { Text("🗄️ 数据库端口") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = threadCountText,
                            onValueChange = { viewModel.fscanThreadCountText.value = it },
                            label = { Text("扫描线程数 (-t)") },
                            placeholder = { Text("默认 1000") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = timeoutSecText,
                            onValueChange = { viewModel.fscanTimeoutSecText.value = it },
                            label = { Text("超时时间/秒 (-time)") },
                            placeholder = { Text("默认 1") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Options Checkboxes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = disableBrute,
                                onCheckedChange = { viewModel.fscanDisableBrute.value = it }
                            )
                            Text(text = "禁用爆破 (-nobr)", style = MaterialTheme.typography.bodyMedium)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = disablePing,
                                onCheckedChange = { viewModel.fscanDisablePing.value = it }
                            )
                            Text(text = "禁止存活检测 (-np)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.startFscan() },
                            enabled = !isRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isRunning) "扫描中..." else "开始 Fscan 扫描")
                        }

                        if (isRunning) {
                            Button(
                                onClick = { viewModel.stopFscan() },
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

        progress?.let { prog ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val total = prog.totalIps
                        val scanned = prog.scannedIpsCount
                        val percent = if (total > 0) (scanned.toFloat() / total * 100).toInt() else 0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isRunning) "正在扫描目标: ${prog.currentScanningIp}" else "Fscan 扫描完成",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = if (total > 0) (scanned.toFloat() / total).coerceIn(0f, 1f) else 0f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "已扫描目标: $scanned / $total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "已发现存活主机: ${prog.aliveHosts.size} 台",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedViewMode == 0,
                        onClick = { selectedViewMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("终端输出日志")
                    }
                    SegmentedButton(
                        selected = selectedViewMode == 1,
                        onClick = { selectedViewMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("发现资产卡片 (${prog.aliveHosts.size})")
                    }
                }
            }

            if (selectedViewMode == 0) {
                item {
                    val logListState = rememberLazyListState()
                    val logs = prog.logs

                    LaunchedEffect(logs.size) {
                        if (logs.isNotEmpty()) {
                            try {
                                logListState.scrollToItem((logs.size - 1).coerceAtLeast(0))
                            } catch (_: Exception) {}
                        }
                    }

                    val clipboardManager = LocalClipboardManager.current

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp, max = 450.dp)
                            .background(Color(0xFF181818), shape = RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "fscan 终端输出日志 (${logs.size} 行)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFB0BEC5)
                                )
                                IconButton(
                                    onClick = {
                                        if (logs.isNotEmpty()) {
                                            clipboardManager.setText(AnnotatedString(logs.joinToString("\n")))
                                            Toast.makeText(context, "已复制 fscan 终端日志", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "复制日志",
                                        tint = Color(0xFFB0BEC5),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                LazyColumn(
                                    state = logListState,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(logs) { logLine ->
                                        val textColor = when {
                                            logLine.startsWith("[+] Web Title") -> Color(0xFF00E676) // Bright green
                                            logLine.startsWith("[+] Port scan") -> Color(0xFF00E5FF) // Cyan
                                            logLine.startsWith("[*] LiveTop") -> Color(0xFFFFD54F) // Yellow
                                            logLine.startsWith("[+]") -> Color(0xFF69F0AE)
                                            logLine.startsWith("[-]") -> Color(0xFFFF8A80)
                                            logLine.contains("fscan version") -> Color(0xFFB0BEC5)
                                            else -> Color.White
                                        }

                                        Text(
                                            text = logLine,
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
            } else {
                items(prog.aliveHosts) { host ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectIpForAction(host.ip, "Fscan Result") },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = host.ip,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${host.latencyMs} ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (host.hostname.isNotBlank()) {
                                Text(
                                    text = "Hostname: ${host.hostname}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = "设备类型预测: ${host.osHint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            if (host.portItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "发现开放端口与服务 (支持快速访问):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                host.portItems.forEach { portItem ->
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = portItem.ipPortDisplay,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "[${portItem.serviceName}] ${portItem.titleOrBanner}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            if (portItem.isWeb) {
                                                OutlinedButton(
                                                    onClick = {
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(portItem.webUrl))
                                                            context.startActivity(intent)
                                                        } catch (_: Exception) {
                                                            viewModel.selectIpForAction(portItem.ipPortDisplay, "Fscan Web")
                                                        }
                                                    }
                                                ) {
                                                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("快速访问", fontSize = 11.sp)
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
        }
    }
}


