package com.yangyx.iptools.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.yangyx.iptools.ui.viewmodel.SpeedSample
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangyx.iptools.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val overview by viewModel.networkOverview.collectAsStateWithLifecycle()
    val publicV4 by viewModel.publicIpV4.collectAsStateWithLifecycle()
    val publicV6 by viewModel.publicIpV6.collectAsStateWithLifecycle()
    val publicV4Info by viewModel.publicIpV4Details.collectAsStateWithLifecycle()
    val publicV6Info by viewModel.publicIpV6Details.collectAsStateWithLifecycle()
    val speed by viewModel.currentSpeed.collectAsStateWithLifecycle()
    val lanProgress by viewModel.lanScanProgress.collectAsStateWithLifecycle()
    val isLanScanning by viewModel.isLanScanning.collectAsStateWithLifecycle()

    var hasLocationPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.refreshNetworkOverview()
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Refresh Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "设备网络概览",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "实时感知 LAN、WAN (IPv4/v6) & 信号指标",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    viewModel.refreshNetworkOverview()
                    viewModel.fetchPublicIps()
                }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }

        // Real-time Traffic Speed Widget with Line Chart
        item {
            val samples by viewModel.speedSamples.collectAsStateWithLifecycle()
            val timeRange by viewModel.selectedSpeedRange.collectAsStateWithLifecycle()

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row: Icon, Title & Time Range Filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "实时网速与流量",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Dropdown Range Selector
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                modifier = Modifier.height(34.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = timeRange.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                com.yangyx.iptools.ui.viewmodel.SpeedTimeRange.values().forEach { range ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = range.label,
                                                fontWeight = if (timeRange == range) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.selectedSpeedRange.value = range
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        SpeedGauge(
                            icon = Icons.Default.Download,
                            label = "实时下载",
                            value = formatBytesRate(speed.downloadSpeedBytesPerSec)
                        )
                        SpeedGauge(
                            icon = Icons.Default.Upload,
                            label = "实时上传",
                            value = formatBytesRate(speed.uploadSpeedBytesPerSec)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Line Chart Component
                    SpeedTrafficChart(
                        samples = samples,
                        timeRangeSec = timeRange.seconds,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    )
                }
            }
        }

        // IP Network Addresses (IPv4 & IPv6 Dual-Stack Support)
        item {
            Text(
                text = "网络 IP 地址 (Dual-Stack IPv4/IPv6)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // IPv4 Row
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IpCard(
                    modifier = Modifier.weight(1f),
                    title = "局域网 IPv4",
                    ip = overview.lanIpV4,
                    icon = Icons.Default.Router,
                    tag = "LAN",
                    onIpClick = { viewModel.selectIpForAction(overview.lanIpV4, "LAN IPv4") }
                )
                IpCard(
                    modifier = Modifier.weight(1f),
                    title = "公网 IPv4 (zxinc)",
                    ip = publicV4,
                    subtitle = publicV4Info?.displayLocation ?: "",
                    icon = Icons.Default.Public,
                    tag = "WAN v4",
                    onIpClick = { viewModel.selectIpForAction(publicV4, "Public IPv4") }
                )
            }
        }

        // IPv6 Row (Smart merge when local & public IPv6 are identical)
        item {
            val lanV6Valid = overview.lanIpV6.isNotBlank() && !overview.lanIpV6.contains("未配置") && !overview.lanIpV6.contains("未")
            val publicV6Valid = publicV6.isNotBlank() && !publicV6.contains("未配置") && !publicV6.contains("查询") && !publicV6.contains("无")

            val isIpv6Identical = lanV6Valid && publicV6Valid && (
                overview.lanIpV6.equals(publicV6, ignoreCase = true) ||
                overview.lanIpV6.take(16).equals(publicV6.take(16), ignoreCase = true)
            )

            if (isIpv6Identical) {
                // Merged IPv6 Card
                IpCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "直通 IPv6 全球单播地址 (局网与公网一致)",
                    ip = publicV6,
                    subtitle = (publicV6Info?.displayLocation?.takeIf { it.isNotBlank() } ?: "全球独立直连") + " • 本地网卡与外网公网直通",
                    icon = Icons.Default.Language,
                    tag = "IPv6 GUA",
                    onIpClick = { viewModel.selectIpForAction(publicV6, "IPv6 Global Address") }
                )
            } else if (lanV6Valid && publicV6Valid) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IpCard(
                        modifier = Modifier.weight(1f),
                        title = "局域网 IPv6",
                        ip = overview.lanIpV6,
                        icon = Icons.Default.Language,
                        tag = "LAN v6",
                        onIpClick = { viewModel.selectIpForAction(overview.lanIpV6, "LAN IPv6") }
                    )
                    IpCard(
                        modifier = Modifier.weight(1f),
                        title = "公网外网 IPv6",
                        ip = publicV6,
                        subtitle = publicV6Info?.displayLocation ?: "",
                        icon = Icons.Default.Public,
                        tag = "WAN v6",
                        onIpClick = { viewModel.selectIpForAction(publicV6, "Public IPv6") }
                    )
                }
            } else if (publicV6Valid) {
                IpCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "公网外网 IPv6 (zxinc)",
                    ip = publicV6,
                    subtitle = publicV6Info?.displayLocation ?: "",
                    icon = Icons.Default.Public,
                    tag = "WAN v6",
                    onIpClick = { viewModel.selectIpForAction(publicV6, "Public IPv6") }
                )
            } else if (lanV6Valid) {
                IpCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "局域网 IPv6",
                    ip = overview.lanIpV6,
                    icon = Icons.Default.Language,
                    tag = "LAN v6",
                    onIpClick = { viewModel.selectIpForAction(overview.lanIpV6, "LAN IPv6") }
                )
            } else {
                IpCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "IPv6 地址",
                    ip = "未配置 / 无 IPv6 连接",
                    subtitle = "当前网络环境未分配公网或局域网 IPv6",
                    icon = Icons.Default.Language,
                    tag = "None",
                    onIpClick = {}
                )
            }
        }

        // Wireless Signal Strength Info (Wi-Fi & Cellular)
        item {
            Text(
                text = "无线与信号状态 (Signal Quality)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Wi-Fi details
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (overview.isWifiActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wi-Fi: ${overview.wifiSsid}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "BSSID: ${overview.wifiBssid} | ${overview.wifiFrequency} MHz | ${overview.wifiLinkSpeed} Mbps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${overview.wifiRssi} dBm",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "信号等级 ${overview.wifiSignalLevel}/4",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Cellular Signal details
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CellTower,
                            contentDescription = null,
                            tint = if (overview.isCellularActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "基站无线: ${overview.cellularOperator} (${overview.cellularNetworkType})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "网络类型: ${overview.cellularNetworkType} | 接口: ${overview.interfaceName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${overview.cellularRssi} dBm",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "信号等级 ${overview.cellularSignalLevel}/4",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (!hasLocationPermission) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("授权位置权限以获取准确基站/Wi-Fi名称")
                        }
                    }
                }
            }
        }

        // Quick Subnet Devices Discovery
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "局域网在线设备",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.scanLanSubnet() },
                    enabled = !isLanScanning
                ) {
                    Icon(imageVector = Icons.Default.WifiTethering, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isLanScanning) "扫描中..." else "扫描局域网")
                }
            }
        }

        lanProgress?.let { prog ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "正在扫描: ${prog.currentScanningIp}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "${prog.scannedIpsCount}/${prog.totalIps} (已发现 ${prog.aliveHosts.size})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (prog.totalIps > 0) prog.scannedIpsCount.toFloat() / prog.totalIps else 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            items(prog.aliveHosts) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectIpForAction(device.ip, "Local Subnet") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.ip,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hostname: ${if (device.hostname.isNotBlank()) device.hostname else "未知设备"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = device.osHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedGauge(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Box(modifier = Modifier.padding(8.dp)) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IpCard(
    modifier: Modifier = Modifier,
    title: String,
    ip: String,
    subtitle: String = "",
    icon: ImageVector,
    tag: String,
    onIpClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onIpClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = ip,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2
                )
            }
        }
    }
}

private fun formatBytesRate(bytesPerSec: Long): String {
    val kb = bytesPerSec / 1024f
    return if (kb > 1024) {
        String.format("%.2f MB/s", kb / 1024f)
    } else {
        String.format("%.1f KB/s", kb)
    }
}

@Composable
fun SpeedTrafficChart(
    samples: List<SpeedSample>,
    timeRangeSec: Int,
    modifier: Modifier = Modifier
) {
    val downloadColor = MaterialTheme.colorScheme.primary
    val uploadColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    val now = System.currentTimeMillis()
    val cutoff = now - (timeRangeSec * 1000L)
    val filtered = remember(samples, timeRangeSec) {
        samples.filter { it.timestampMs >= cutoff }
    }

    val maxDl = filtered.maxOfOrNull { it.downloadBytesPerSec }?.coerceAtLeast(1024L) ?: 1024L
    val maxUl = filtered.maxOfOrNull { it.uploadBytesPerSec }?.coerceAtLeast(1024L) ?: 1024L
    val maxRate = maxOf(maxDl, maxUl).toFloat()

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Draw Horizontal Grid Lines
                for (i in 1..3) {
                    val y = height * (i / 4f)
                    drawLine(
                        color = gridColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                if (filtered.size >= 2) {
                    val startTime = cutoff
                    val timeSpan = (now - startTime).coerceAtLeast(1000L).toFloat()

                    // Draw Download Path
                    val dlPath = Path()
                    filtered.forEachIndexed { index, s ->
                        val x = ((s.timestampMs - startTime) / timeSpan) * width
                        val y = height - ((s.downloadBytesPerSec / maxRate) * height)
                        if (index == 0) dlPath.moveTo(x, y) else dlPath.lineTo(x, y)
                    }
                    drawPath(path = dlPath, color = downloadColor, style = Stroke(width = 3f))

                    // Draw Upload Path
                    val ulPath = Path()
                    filtered.forEachIndexed { index, s ->
                        val x = ((s.timestampMs - startTime) / timeSpan) * width
                        val y = height - ((s.uploadBytesPerSec / maxRate) * height)
                        if (index == 0) ulPath.moveTo(x, y) else ulPath.lineTo(x, y)
                    }
                    drawPath(path = ulPath, color = uploadColor, style = Stroke(width = 3f))
                }
            }

            // Legend Overlay
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.extraSmall, color = downloadColor, modifier = Modifier.width(10.dp).height(10.dp)) {}
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "下载", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.extraSmall, color = uploadColor, modifier = Modifier.width(10.dp).height(10.dp)) {}
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "上传", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

