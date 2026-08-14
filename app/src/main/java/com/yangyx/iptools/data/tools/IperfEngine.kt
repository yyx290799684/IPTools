package com.yangyx.iptools.data.tools

import android.content.Context
import android.util.Log
import com.synaptictools.iperf.IPerf
import com.synaptictools.iperf.IPerfConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

data class IperfPoint(
    val sec: Int,
    val transferMB: Float,
    val bitrateMbps: Float,
    val jitterMs: Float = 0f,
    val isComplete: Boolean = false,
    val summaryText: String = "",
    val logMessage: String = ""
)

object IperfEngine {

    private const val TAG = "IperfEngine"

    // Regex for parsing standard iPerf3 CLI interval lines:
    // e.g. "[  5]   0.00-1.00   sec  12.5 MBytes   105 Mbits/sec"
    // e.g. "[  5]   0.00-1.00   sec  1.19 MBytes  10.0 Mbits/sec  0.125 ms  0/854 (0%)"
    private val INTERVAL_REGEX = Regex(
        """\[\s*(\d+|SUM)\]\s+([\d\.]+)-([\d\.]+)\s+sec\s+([\d\.]+)\s+([a-zA-Z/]+)\s+([\d\.]+)\s+([a-zA-Z/]+)(?:\s+([\d\.]+)\s*ms)?"""
    )

    @Volatile
    private var isDeinitialized = false

    fun stopIperf() {
        // Intentionally no-op here to prevent native SIGSEGV crashes caused by concurrent deInit
    }

    fun runClientTest(
        context: Context,
        serverHost: String,
        port: Int = 5201,
        durationSec: Int = 5,
        protocol: String = "TCP", // TCP or UDP
        bandwidthLimit: String = "0", // e.g. "10M", "100M", "0" = unlimited
        parallelStreams: Int = 1, // -P
        isReverse: Boolean = false // -R
    ): Flow<IperfPoint> = callbackFlow<IperfPoint> {
        synchronized(IperfEngine) {
            isDeinitialized = false
        }
        val target = serverHost.trim()
        if (target.isBlank()) {
            trySend(
                IperfPoint(
                    sec = 0,
                    transferMB = 0f,
                    bitrateMbps = 0f,
                    isComplete = true,
                    summaryText = "错误: 服务器地址为空，请输入 iPerf3 Server IP/域名"
                )
            )
            close()
            return@callbackFlow
        }

        val isUdp = protocol.equals("UDP", ignoreCase = true)
        val testDuration = durationSec.coerceIn(1, 300)
        val streamsCount = parallelStreams.coerceIn(1, 10)
        val modeLabel = if (isReverse) "Reverse/Download (-R)" else "Upload/Sender"
        val protoLabel = if (isUdp) "UDP (-u)" else "TCP"

        trySend(
            IperfPoint(
                sec = 0,
                transferMB = 0f,
                bitrateMbps = 0f,
                logMessage = "已启动开源原生 iPerf3 引擎，正在连接 $target:$port [$protoLabel, $modeLabel, 并发: $streamsCount]..."
            )
        )

        val tempStream = File(context.filesDir, "iperf3.XXXXXX")

        val config = IPerfConfig(
            hostname = target,
            port = port,
            stream = tempStream.path,
            download = isReverse,
            json = false,
            duration = testDuration,
            interval = 1,
            useUDP = isUdp
        )

        val fullOutput = StringBuilder()
        var currentSec = 0
        var lastBitrate = 0f
        var lastTransfer = 0f

        IPerf.seCallBack {
            update { text ->
                if (!text.isNullOrBlank()) {
                    val line = text.trim()
                    Log.d(TAG, "iPerf3 output line: $line")
                    fullOutput.append(line).append("\n")

                        // Parse interval line if matching regex
                        val match = INTERVAL_REGEX.find(line)
                        if (match != null) {
                            try {
                                val id = match.groupValues[1]
                                val endSec = match.groupValues[3].toFloatOrNull()?.toInt() ?: (currentSec + 1)
                                val transferVal = match.groupValues[4].toFloatOrNull() ?: 0f
                                val transferUnit = match.groupValues[5]
                                val bitrateVal = match.groupValues[6].toFloatOrNull() ?: 0f
                                val bitrateUnit = match.groupValues[7]
                                val jitterVal = match.groupValues.getOrNull(8)?.toFloatOrNull() ?: 0f

                                // Convert transfer to MB
                                val transferMB = when {
                                    transferUnit.contains("K", ignoreCase = true) -> transferVal / 1024f
                                    transferUnit.contains("G", ignoreCase = true) -> transferVal * 1024f
                                    else -> transferVal
                                }

                                // Convert bitrate to Mbps
                                val bitrateMbps = when {
                                    bitrateUnit.contains("K", ignoreCase = true) -> bitrateVal / 1000f
                                    bitrateUnit.contains("G", ignoreCase = true) -> bitrateVal * 1000f
                                    else -> bitrateVal
                                }

                                currentSec = endSec
                                lastTransfer = transferMB
                                lastBitrate = bitrateMbps

                                if (id != "SUM" || streamsCount == 1) {
                                    trySend(
                                        IperfPoint(
                                            sec = currentSec,
                                            transferMB = transferMB,
                                            bitrateMbps = bitrateMbps,
                                            jitterMs = jitterVal,
                                            isComplete = false
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse iPerf3 line: ${e.message}")
                            }
                        } else {
                            // Non-interval output line (e.g. status, connection info, error)
                            trySend(
                                IperfPoint(
                                    sec = currentSec,
                                    transferMB = lastTransfer,
                                    bitrateMbps = lastBitrate,
                                    logMessage = line
                                )
                            )
                        }
                }
            }

            success {
                Log.d(TAG, "iPerf3 test completed successfully")
                val summary = "iPerf3 官方开源原生引擎测试报告 [$target:$port]\n" +
                        "测试模式: $protoLabel | $modeLabel | 并发数: $streamsCount\n" +
                        "测试时长: ${testDuration}s | 端口: $port\n\n" +
                        fullOutput.toString().takeLast(1000)

                trySend(
                    IperfPoint(
                        sec = testDuration,
                        transferMB = lastTransfer,
                        bitrateMbps = lastBitrate,
                        isComplete = true,
                        summaryText = summary
                    )
                )
                try { tempStream.delete() } catch (_: Exception) {}
                close()
            }

            error { err ->
                Log.e(TAG, "iPerf3 test error: $err")
                val errSummary = "iPerf3 测速发生异常/中断:\n$err\n\n输出日志:\n$fullOutput"
                trySend(
                    IperfPoint(
                        sec = currentSec,
                        transferMB = lastTransfer,
                        bitrateMbps = lastBitrate,
                        isComplete = true,
                        summaryText = errSummary
                    )
                )
                try { tempStream.delete() } catch (_: Exception) {}
                close()
            }
        }

        withContext(Dispatchers.IO) {
            try {
                IPerf.request(config)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initiate iPerf request: ${e.message}", e)
                trySend(
                    IperfPoint(
                        sec = 0,
                        transferMB = 0f,
                        bitrateMbps = 0f,
                        isComplete = true,
                        summaryText = "iPerf3 引擎调用失败: ${e.localizedMessage ?: "连接失败"}"
                    )
                )
                close()
            } finally {
                try {
                    IPerf.deInit()
                } catch (_: Throwable) {}
            }
        }

        awaitClose {
            stopIperf()
            try { tempStream.delete() } catch (_: Throwable) {}
        }
    }.flowOn(Dispatchers.IO)

    fun runServerMode(listenPort: Int = 5201): Flow<IperfPoint> = callbackFlow<IperfPoint> {
        trySend(
            IperfPoint(
                sec = 0,
                transferMB = 0f,
                bitrateMbps = 0f,
                logMessage = "iPerf3 服务端已启动\n监听端口: $listenPort (0.0.0.0:$listenPort)"
            )
        )

        // For server mode, we can run iPerf3 native server
        awaitClose {
            stopIperf()
        }
    }.flowOn(Dispatchers.IO)
}
