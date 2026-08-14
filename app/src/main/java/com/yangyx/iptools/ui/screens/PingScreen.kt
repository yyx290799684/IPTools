package com.yangyx.iptools.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangyx.iptools.ui.viewmodel.MainViewModel

@Composable
fun PingScreen(viewModel: MainViewModel) {
    val host by viewModel.pingHost.collectAsStateWithLifecycle()
    val count by viewModel.pingCount.collectAsStateWithLifecycle()
    val isContinuous by viewModel.isContinuousPing.collectAsStateWithLifecycle()
    val intervalMs by viewModel.pingIntervalMs.collectAsStateWithLifecycle()
    val timeoutMs by viewModel.pingTimeoutMs.collectAsStateWithLifecycle()
    val dontFragment by viewModel.pingDontFragment.collectAsStateWithLifecycle()

    val size by viewModel.pingSize.collectAsStateWithLifecycle()
    val ttl by viewModel.pingTtl.collectAsStateWithLifecycle()

    val packets by viewModel.pingPackets.collectAsStateWithLifecycle()
    val summary by viewModel.pingSummary.collectAsStateWithLifecycle()
    val isRunning by viewModel.isPingRunning.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Ping 网络延迟与连通性测试",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Control Panel Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { viewModel.pingHost.value = it },
                        label = { Text("目标 IP / 域名 (IPv4 或 IPv6)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = if (isContinuous) "长 Ping (无限)" else count.toString(),
                            onValueChange = { viewModel.pingCount.value = it.toIntOrNull() ?: 4 },
                            enabled = !isContinuous,
                            label = { Text("包数") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = intervalMs.toString(),
                            onValueChange = { viewModel.pingIntervalMs.value = it.toIntOrNull()?.coerceAtLeast(1) ?: 1000 },
                            label = { Text("间隔 ms (最低1ms)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = timeoutMs.toString(),
                            onValueChange = { viewModel.pingTimeoutMs.value = it.toIntOrNull() ?: 1000 },
                            label = { Text("超时 ms") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = size.toString(),
                            onValueChange = { viewModel.pingSize.value = it.toIntOrNull() ?: 56 },
                            label = { Text("包大小 (bytes)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = ttl.toString(),
                            onValueChange = { viewModel.pingTtl.value = it.toIntOrNull() ?: 64 },
                            label = { Text("TTL 存活时间") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Continuous Ping & DF Checkboxes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isContinuous,
                                onCheckedChange = { viewModel.isContinuousPing.value = it }
                            )
                            Text(text = "长 Ping (-t 无限包数)", style = MaterialTheme.typography.bodyMedium)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = dontFragment,
                                onCheckedChange = { viewModel.pingDontFragment.value = it }
                            )
                            Text(text = "禁止分片 (-M do DF)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isRunning) {
                        Button(
                            onClick = { viewModel.startPing() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始 Ping 测试")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.stopPing() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("停止测试")
                        }
                    }
                }
            }
        }

        // Statistics Summary Card
        summary?.let { sum ->
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Ping 结果统计汇总 (${sum.targetHost})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("发送: ${sum.transmitted} | 接收: ${sum.received}")
                            Text(
                                "丢包率: ${sum.packetLossPercentage.toInt()}%",
                                color = if (sum.packetLossPercentage > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "RTT (ms): 最快 ${sum.minRttMs} / 平均 ${sum.avgRttMs} / 最慢 ${sum.maxRttMs}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Real-time Packets Stream
        if (packets.isNotEmpty()) {
            item {
                val context = LocalContext.current
                val clipboardManager = LocalClipboardManager.current

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ping 实时报文日志 (${packets.size} 条)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = {
                            val logText = packets.joinToString("\n") { pkt ->
                                if (pkt.isSuccess) "Seq #${pkt.seq} | IP: ${pkt.ip} | Bytes: ${pkt.bytes} | TTL: ${pkt.ttl} | RTT: ${pkt.timeMs}ms"
                                else "Seq #${pkt.seq} | IP: ${pkt.ip} | Timeout/Failed"
                            }
                            clipboardManager.setText(AnnotatedString(logText))
                            Toast.makeText(context, "已复制 Ping 报文日志", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制 Ping 日志",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        items(packets) { pkt ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectIpForAction(pkt.ip, "Ping") },
                colors = CardDefaults.cardColors(
                    containerColor = if (pkt.isSuccess) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "包 #${pkt.seq} | 来自 ${pkt.ip}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (pkt.isSuccess) "${pkt.bytes} bytes, TTL=${pkt.ttl}" else "请求超时 / Destination Unreachable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (pkt.isSuccess) "${pkt.timeMs} ms" else "TIMEOUT",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (pkt.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

