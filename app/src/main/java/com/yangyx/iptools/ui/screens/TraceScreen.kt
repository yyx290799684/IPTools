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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
fun TraceScreen(viewModel: MainViewModel) {
    val host by viewModel.traceHost.collectAsStateWithLifecycle()
    val maxHops by viewModel.traceMaxHops.collectAsStateWithLifecycle()
    val timeoutMs by viewModel.traceTimeoutMs.collectAsStateWithLifecycle()
    val mode by viewModel.traceMode.collectAsStateWithLifecycle()

    val hops by viewModel.traceHops.collectAsStateWithLifecycle()
    val isRunning by viewModel.isTraceRunning.collectAsStateWithLifecycle()
    val geoProviderChoice by viewModel.geoProviderChoice.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Trace 路由追踪与 IP 归属",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { viewModel.traceHost.value = it },
                        label = { Text("目标主机 IP / 域名") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = maxHops.toString(),
                            onValueChange = { viewModel.traceMaxHops.value = it.toIntOrNull() ?: 20 },
                            label = { Text("最大 Hop 跳数 (1-30)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = timeoutMs.toString(),
                            onValueChange = { viewModel.traceTimeoutMs.value = it.toIntOrNull() ?: 1500 },
                            label = { Text("超时 ms (默认 1500)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = mode == "ICMP",
                            onClick = { viewModel.traceMode.value = "ICMP" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("ICMP 模式 (原生 ping -t)")
                        }
                        SegmentedButton(
                            selected = mode == "UDP",
                            onClick = { viewModel.traceMode.value = "UDP" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("UDP 模式 (Socket 探测)")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isRunning) {
                        Button(
                            onClick = { viewModel.startTrace() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始 Traceroute 追踪")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.stopTrace() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("停止追踪")
                        }
                    }
                }
            }
        }

        if (hops.isNotEmpty()) {
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
                        text = "Traceroute 追踪节点日志 (${hops.size} Hops)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = {
                            val logText = hops.joinToString("\n") { hop ->
                                "Hop #${hop.hopNumber} | IP: ${hop.ip} | Host: ${hop.hostname} | Geo: ${hop.geoInfo?.getDisplayLocation("IP-API") ?: "Unknown"}"
                            }
                            clipboardManager.setText(AnnotatedString(logText))
                            Toast.makeText(context, "已复制 Traceroute 节点日志", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制 Traceroute 日志",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        items(hops) { hop ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectIpForAction(hop.ip, "Trace Hop ${hop.hopNumber}") },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hop #${hop.hopNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(70.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hop.ip,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        hop.geoInfo?.let { geo ->
                            Text(
                                text = geo.getDisplayLocation(geoProviderChoice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } ?: run {
                            if (hop.hostname.isNotBlank()) {
                                Text(
                                    text = hop.hostname,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = if (hop.isReached && hop.ip != "*") "${hop.timeMs} ms" else "* 请求超时",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (hop.isReached && hop.ip != "*") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

