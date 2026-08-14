package com.yangyx.iptools.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangyx.iptools.data.tools.IperfPoint
import com.yangyx.iptools.ui.viewmodel.MainViewModel

@Composable
fun IperfScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val serverHost by viewModel.iperfServer.collectAsStateWithLifecycle()
    val serverPort by viewModel.iperfPort.collectAsStateWithLifecycle()
    val duration by viewModel.iperfDuration.collectAsStateWithLifecycle()
    val protocol by viewModel.iperfProtocol.collectAsStateWithLifecycle()
    val iperfMode by viewModel.iperfMode.collectAsStateWithLifecycle()
    val bandwidth by viewModel.iperfBandwidth.collectAsStateWithLifecycle()
    val parallel by viewModel.iperfParallel.collectAsStateWithLifecycle()
    val isReverse by viewModel.iperfIsReverse.collectAsStateWithLifecycle()

    val points by viewModel.iperfPoints.collectAsStateWithLifecycle()
    val isRunning by viewModel.isIperfRunning.collectAsStateWithLifecycle()

    // Calculate real-time stats for live dashboard
    val intervalPoints = points.filter { !it.isComplete && it.sec > 0 }
    val latestPoint = intervalPoints.lastOrNull()
    val completedPoint = points.lastOrNull { it.isComplete }

    val currentSpeedMbps = latestPoint?.bitrateMbps ?: 0f
    val currentTransferredMB = intervalPoints.sumOf { it.transferMB.toDouble() }.toFloat()
    val currentSec = latestPoint?.sec ?: 0
    val progressFraction = if (duration > 0) (currentSec.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    val logMessages = points.mapNotNull { if (it.logMessage.isNotBlank()) it.logMessage else null }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "iPerf3 网络性能与带宽测试",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Configuration Card
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

                    Spacer(modifier = Modifier.height(12.dp))

                    if (iperfMode == "CLIENT") {
                        OutlinedTextField(
                            value = serverHost,
                            onValueChange = { viewModel.iperfServer.value = it },
                            label = { Text("iPerf3 服务器地址 / IP (例如 192.168.1.100)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = serverPort.toString(),
                            onValueChange = { viewModel.iperfPort.value = it.toIntOrNull() ?: 5201 },
                            label = { Text("端口 (默认 5201)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        if (iperfMode == "CLIENT") {
                            OutlinedTextField(
                                value = duration.toString(),
                                onValueChange = { viewModel.iperfDuration.value = it.toIntOrNull() ?: 5 },
                                label = { Text("时长 -t (秒)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }

                    if (iperfMode == "CLIENT") {
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
                                text = "反向压测 -R (Server 向 手机发包/测下载速)",
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
                                Text("TCP 传输")
                            }
                            SegmentedButton(
                                selected = protocol == "UDP",
                                onClick = { viewModel.iperfProtocol.value = "UDP" },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("UDP 传输")
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
                                if (isRunning) "iPerf 压测中..."
                                else if (iperfMode == "SERVER") "启动 iPerf Server 监听"
                                else "启动 iPerf Client 测速"
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

        // Live Real-Time Dashboard Card (Active during test or after completion)
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
                                    text = if (isRunning) "实时带宽测速看板" else "测速概览看板",
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
                                    text = if (isRunning) "正在传输..." else "已完成",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isRunning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                                    text = "Mbits / sec (吞吐速率)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isRunning) {
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
                                label = "进度时长",
                                value = if (completedPoint != null) "${duration} / ${duration}s" else "${currentSec} / ${duration}s",
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                label = "已传输数据",
                                value = String.format("%.2f MB", if (completedPoint != null) completedPoint.transferMB else currentTransferredMB),
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                label = "并发线程",
                                value = "$parallel Stream",
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

        // Final Report Summary Card
        completedPoint?.let { completed ->
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

        // Simplified Control / Socket Log Messages (only connection/status events, excluding redundant interval lines)
        if (logMessages.isNotEmpty()) {
            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "控制信道与连接日志",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(logMessages.joinToString("\n")))
                                    Toast.makeText(context, "已复制 iPerf3 控制日志", Toast.LENGTH_SHORT).show()
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
                        Spacer(modifier = Modifier.height(6.dp))
                        logMessages.takeLast(10).forEach { msg ->
                            Text(
                                text = "• $msg",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Section Title for Interval Data Points
        if (intervalPoints.isNotEmpty()) {
            item {
                Text(
                    text = "每秒实时压测序列 (Intervals)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            items(intervalPoints) { pt ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Interval ${(pt.sec - 1)}.0 - ${pt.sec}.0 s",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "${String.format("%.2f", pt.transferMB)} MB",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "${String.format("%.2f", pt.bitrateMbps)} Mbits/s",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IperfBitrateChart(
    points: List<IperfPoint>,
    modifier: Modifier = Modifier
) {
    val intervalPoints = remember(points) { points.filter { !it.isComplete && it.sec > 0 } }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📈 速率实时变化折线图",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (intervalPoints.isNotEmpty()) {
                    val maxVal = intervalPoints.maxOf { it.bitrateMbps }
                    val avgVal = intervalPoints.map { it.bitrateMbps }.average().toFloat()
                    Text(
                        text = "峰值: ${String.format("%.1f", maxVal)} Mbps | 均值: ${String.format("%.1f", avgVal)} Mbps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (intervalPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "等待测速数据，开启后动态生成速率折线图...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val lineColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

                val maxRate = (intervalPoints.maxOfOrNull { it.bitrateMbps } ?: 10f).coerceAtLeast(1f) * 1.15f

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


