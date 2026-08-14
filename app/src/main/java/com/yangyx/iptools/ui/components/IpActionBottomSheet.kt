package com.yangyx.iptools.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.yangyx.iptools.ui.viewmodel.AppScreen
import com.yangyx.iptools.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpActionBottomSheet(
    ip: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var showFavoriteDialog by remember { mutableStateOf(false) }
    var favoriteTitle by remember { mutableStateOf(ip) }
    var favoriteNote by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "操作 IP / 域名",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = ip,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            HorizontalDivider()

            ActionItem(
                icon = Icons.Default.LocationOn,
                title = "查询 IP 归属地 (Ip2Region / Geo)",
                onClick = {
                    viewModel.quickFillIpToTool(ip, AppScreen.IP_GEO)
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.Default.NetworkCheck,
                title = "快速填入 Ping 工具",
                onClick = {
                    viewModel.quickFillIpToTool(ip, AppScreen.PING)
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.Default.Router,
                title = "快速填入 Trace 路由追踪",
                onClick = {
                    viewModel.quickFillIpToTool(ip, AppScreen.TRACE)
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.Default.Radar,
                title = "快速填入 端口扫描",
                onClick = {
                    viewModel.quickFillIpToTool(ip, AppScreen.PORT_SCAN)
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.Default.Terminal,
                title = "快速填入 Fscan 综合扫描",
                onClick = {
                    viewModel.quickFillIpToTool(ip, AppScreen.FSCAN)
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.Default.Speed,
                title = "快速填入 iPerf 测速",
                onClick = {
                    viewModel.quickFillIpToTool(ip, AppScreen.IPERF)
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.Default.Dns,
                title = "快速填入 DNS / Whois",
                onClick = {
                    viewModel.quickFillIpToTool(ip, AppScreen.DNS)
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.Default.Bookmark,
                title = "收藏此 IP 地址",
                onClick = {
                    showFavoriteDialog = true
                }
            )

            ActionItem(
                icon = Icons.Default.ContentCopy,
                title = "复制 IP 到剪贴板",
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("IP Address", ip)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "已复制 $ip 到剪贴板", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )
        }
    }

    if (showFavoriteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showFavoriteDialog = false },
            title = { Text("添加收藏") },
            text = {
                Column {
                    OutlinedTextField(
                        value = favoriteTitle,
                        onValueChange = { favoriteTitle = it },
                        label = { Text("备注名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = favoriteNote,
                        onValueChange = { favoriteNote = it },
                        label = { Text("附加说明 (可选)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addFavorite(ip, favoriteTitle, favoriteNote)
                    Toast.makeText(context, "已收藏 $ip", Toast.LENGTH_SHORT).show()
                    showFavoriteDialog = false
                    onDismiss()
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFavoriteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}
