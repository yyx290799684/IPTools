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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangyx.iptools.ui.viewmodel.MainViewModel

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.ui.text.font.FontFamily

@Composable
fun DnsScreen(viewModel: MainViewModel) {
    val domain by viewModel.dnsDomain.collectAsStateWithLifecycle()
    val serverChoice by viewModel.dnsServerChoice.collectAsStateWithLifecycle()
    val customServer by viewModel.dnsCustomServer.collectAsStateWithLifecycle()
    val toolMode by viewModel.dnsToolMode.collectAsStateWithLifecycle()
    val recordType by viewModel.dnsRecordType.collectAsStateWithLifecycle()
    val digShort by viewModel.digShort.collectAsStateWithLifecycle()
    val digTrace by viewModel.digTrace.collectAsStateWithLifecycle()
    val digTcp by viewModel.digTcp.collectAsStateWithLifecycle()
    val digRecurse by viewModel.digRecurse.collectAsStateWithLifecycle()

    val queryResult by viewModel.dnsQueryResult.collectAsStateWithLifecycle()
    val results by viewModel.dnsResults.collectAsStateWithLifecycle()
    val isQuerying by viewModel.isDnsQuerying.collectAsStateWithLifecycle()
    val systemDnsServer by viewModel.systemDnsServer.collectAsStateWithLifecycle()

    val toolModes = listOf("DIG" to "Linux dig", "NSLOOKUP" to "nslookup", "STANDARD" to "标准 DNS")

    val dnsServers = listOf(
        "DEFAULT" to "当前默认 DNS ($systemDnsServer)",
        "8.8.8.8" to "Google (8.8.8.8)",
        "1.1.1.1" to "Cloudflare (1.1.1.1)",
        "223.5.5.5" to "AliDNS (223.5.5.5)",
        "114.114.114.114" to "114 DNS",
        "CUSTOM" to "自定义服务器"
    )

    val recordTypes = listOf("A", "AAAA", "CNAME", "MX", "TXT", "NS", "SOA", "ANY")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "DNS 解析工具 (dig & nslookup & 自定义服务器)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "选择查询工具模式:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        toolModes.forEachIndexed { idx, pair ->
                            SegmentedButton(
                                selected = toolMode == pair.first,
                                onClick = { viewModel.dnsToolMode.value = pair.first },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = toolModes.size)
                            ) {
                                Text(pair.second, maxLines = 1)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = domain,
                        onValueChange = { viewModel.dnsDomain.value = it },
                        label = { Text("要解析的域名 / 主机名 (例如 google.com)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "选择上游 DNS 服务器:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dnsServers.forEach { pair ->
                            FilterChip(
                                selected = serverChoice == pair.first,
                                onClick = { viewModel.dnsServerChoice.value = pair.first },
                                label = { Text(pair.second) }
                            )
                        }
                    }

                    if (serverChoice == "CUSTOM") {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customServer,
                            onValueChange = { viewModel.dnsCustomServer.value = it },
                            label = { Text("自定义 DNS 服务器 IP (例如 192.168.1.1 或 8.8.4.4)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "记录类型 (Record Type):",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        recordTypes.forEach { type ->
                            FilterChip(
                                selected = recordType == type,
                                onClick = { viewModel.dnsRecordType.value = type },
                                label = { Text(type) }
                            )
                        }
                    }

                    if (toolMode == "DIG") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "dig 命令行高级参数:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Checkbox(
                                    checked = digShort,
                                    onCheckedChange = { viewModel.digShort.value = it }
                                )
                                Text("+short (精简)", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Checkbox(
                                    checked = digTrace,
                                    onCheckedChange = { viewModel.digTrace.value = it }
                                )
                                Text("+trace (根追踪)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Checkbox(
                                    checked = digTcp,
                                    onCheckedChange = { viewModel.digTcp.value = it }
                                )
                                Text("+tcp (TCP 传输)", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Checkbox(
                                    checked = digRecurse,
                                    onCheckedChange = { viewModel.digRecurse.value = it }
                                )
                                Text("+recurse (递归)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.runDnsQuery() },
                        enabled = !isQuerying,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isQuerying) "正在执行 DNS $toolMode 查询..." else "执行 $toolMode 查询")
                    }
                }
            }
        }

        queryResult?.let { qRes ->
            if (qRes.rawTerminalOutput.isNotBlank()) {
                item {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "终端原始命令输出 (${qRes.modeUsed} @${qRes.serverUsed})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${qRes.queryTimeMs} ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(qRes.rawTerminalOutput))
                                            Toast.makeText(context, "已复制 DNS 终端日志", Toast.LENGTH_SHORT).show()
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
                            Text(
                                text = qRes.rawTerminalOutput,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        items(results) { rec ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (rec.type == "A" || rec.type == "AAAA") {
                            viewModel.selectIpForAction(rec.value, "DNS Record")
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rec.type,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(60.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rec.value,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "TTL: ${rec.ttl}s | Server: ${rec.serverUsed}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
