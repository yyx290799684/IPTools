package com.yangyx.iptools.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow

@Composable
fun IpGeoScreen(viewModel: MainViewModel) {
    val inputIp by viewModel.ipGeoInput.collectAsStateWithLifecycle()
    val geoResult by viewModel.ipGeoResult.collectAsStateWithLifecycle()
    val isQuerying by viewModel.isGeoQuerying.collectAsStateWithLifecycle()
    val preferredProvider by viewModel.geoProviderChoice.collectAsStateWithLifecycle()

    val providers = listOf("IP2REGION" to "ip2region (本地离线)", "ZXINC_API" to "ZXINC API (在线)")

    val xdbStatus by viewModel.xdbStatusText.collectAsStateWithLifecycle()
    val isXdbUpdating by viewModel.isXdbUpdating.collectAsStateWithLifecycle()
    val xdbProgressMsg by viewModel.xdbUpdateProgressMessage.collectAsStateWithLifecycle()

    val xdbSourceChoice by viewModel.xdbSourceChoice.collectAsStateWithLifecycle()
    val xdbCustomProxy by viewModel.xdbCustomProxy.collectAsStateWithLifecycle()

    val xdbSources = listOf(
        "RAW" to "GitHub 原始地址",
        "gh.dpik.top" to "gh.dpik.top",
        "gh-proxy.com" to "gh-proxy.com",
        "github.tbap.top" to "github.tbap.top",
        "github.dpik.top" to "github.dpik.top",
        "ghfile.geekertao.top" to "ghfile.geekertao.top",
        "ghproxy.net" to "ghproxy.net",
        "CUSTOM" to "自定义代理"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "IP 归属地与 GeoLocation (ip2region & API)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // ip2region xdb Offline Database Updater Card
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ip2region 离线数据库 (.xdb)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "GitHub 实时源",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "数据库文件: ip2region_v4.xdb & ip2region_v6.xdb\n$xdbStatus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

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
                        xdbSources.forEach { pair ->
                            FilterChip(
                                selected = xdbSourceChoice == pair.first,
                                onClick = { viewModel.xdbSourceChoice.value = pair.first },
                                label = { Text(pair.second) }
                            )
                        }
                    }

                    if (xdbSourceChoice == "CUSTOM") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = xdbCustomProxy,
                            onValueChange = { viewModel.xdbCustomProxy.value = it },
                            label = { Text("自定义代理地址 (域名或完整前缀)") },
                            placeholder = { Text("例如: https://ghproxy.net/ 或 gh.dpik.top") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (xdbProgressMsg.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = xdbProgressMsg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { viewModel.updateXdbDatabaseOnline() },
                        enabled = !isXdbUpdating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isXdbUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在从选中节点下载...")
                        } else {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("在线更新/恢复 ip2region .xdb 数据库")
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = inputIp,
                        onValueChange = { viewModel.ipGeoInput.value = it },
                        label = { Text("IP 地址 (IPv4 或 IPv6)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "全局路由及工具(Trace/Ping)默认显示的数据源:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        providers.forEachIndexed { idx, pair ->
                            SegmentedButton(
                                selected = preferredProvider == pair.first,
                                onClick = { viewModel.geoProviderChoice.value = pair.first },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = providers.size)
                            ) {
                                Text(pair.second, maxLines = 1)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.runIpGeoQuery() },
                        enabled = !isQuerying,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isQuerying) "正在对比 ip2region 与 API 库..." else "双方案对比查询 IP 归属地")
                    }
                }
            }
        }

        geoResult?.let { geo ->
            // Solution 1: ip2region
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectIpForAction(geo.ip, "GeoResult") },
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (preferredProvider == "IP2REGION") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "方案 1: ip2region 本地离线库",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (preferredProvider == "IP2REGION") {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "默认源",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        GeoInfoRow("国家", geo.ip2regionCountry)
                        GeoInfoRow("省份 / 区域", geo.ip2regionProvince)
                        GeoInfoRow("城市", geo.ip2regionCity)
                        GeoInfoRow("网络运营商 (ISP)", geo.ip2regionIsp)
                        GeoInfoRow("标准 xdb 格式", geo.ip2regionRaw)
                    }
                }
            }

            // Solution 2: ZXINC / IP-API Online API
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectIpForAction(geo.ip, "GeoResult") },
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (preferredProvider == "ZXINC_API") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "方案 2: ZXINC API 在线查询",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (preferredProvider == "ZXINC_API") {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                ) {
                                    Text(
                                        text = "默认源",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        GeoInfoRow("国家 / 地区", geo.apiCountry)
                        GeoInfoRow("省份 / 州", geo.apiRegion)
                        GeoInfoRow("城市", geo.apiCity)
                        GeoInfoRow("网络运营商 (ISP)", geo.apiIsp)
                        if (geo.org.isNotBlank()) GeoInfoRow("所属机构", geo.org)
                    }
                }
            }

            if (geo.queryLogs.isNotEmpty()) {
                item {
                    val context = LocalContext.current
                    val clipboardManager = LocalClipboardManager.current

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "IP 归属地查询调试日志",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(geo.queryLogs.joinToString("\n")))
                                        Toast.makeText(context, "已复制 IP 归属地调试日志", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "复制",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            geo.queryLogs.forEach { logLine ->
                                Text(
                                    text = logLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (logLine.contains("Error") || logLine.contains("Warning")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeoInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
