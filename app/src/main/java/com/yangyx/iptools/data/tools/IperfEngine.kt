package com.yangyx.iptools.data.tools

import android.content.Context
import android.os.Build
import android.util.Log
import com.synaptictools.iperf.IPerf
import com.synaptictools.iperf.IPerfConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class IperfPoint(
    val sec: Int,
    val transferMB: Float,
    val bitrateMbps: Float,
    val jitterMs: Float = 0f,
    val lostPercent: Float = 0f,
    val isComplete: Boolean = false,
    val summaryText: String = "",
    val logMessage: String = "",
    val clientIp: String = ""
)

object IperfEngine {

    private const val TAG = "IperfEngine"

    // Regex for parsing standard iPerf3 CLI interval lines:
    // e.g. "[  5]   0.00-1.00   sec  12.5 MBytes   105 Mbits/sec"
    // e.g. "[  5]   0.00-1.00   sec  1.19 MBytes  10.0 Mbits/sec  0.125 ms  0/854 (0%)"
    private val INTERVAL_REGEX = Regex(
        """\[\s*(\d+|SUM)\]\s+([\d\.]+)-([\d\.]+)\s+sec\s+([\d\.]+)\s+([a-zA-Z/]+)\s+([\d\.]+)\s+([a-zA-Z/]+)(?:\s+([\d\.]+)\s*ms)?.*"""
    )

    // Regex for detecting accepted client connection in server mode:
    // e.g. "Accepted connection from 192.168.1.50, port 54321"
    private val ACCEPTED_CONN_REGEX = Regex(
        """Accepted connection from ([^,\s]+), port (\d+)"""
    )

    @Volatile
    private var activeProcess: Process? = null

    @Volatile
    private var activeServerSocket: ServerSocket? = null

    private val isStopping = AtomicBoolean(false)

    fun getLocalIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (ifaceAddr in iface.interfaceAddresses) {
                    val addr = ifaceAddr.address
                    val prefix = ifaceAddr.networkPrefixLength.toInt()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostIp = addr.hostAddress ?: continue
                        val ipWithMask = if (prefix in 1..32) "$hostIp/$prefix" else hostIp
                        ips.add("$ipWithMask (${iface.name})")
                    }
                }
            }
        } catch (_: Exception) {}
        return if (ips.isEmpty()) listOf("127.0.0.1/8 (lo)") else ips
    }

    fun getBinaryStatusInfo(context: Context): String {
        val binDir = File(context.filesDir, "native_bin")
        val iperfFile = File(binDir, "iperf3")
        val fallbackFile = File(context.applicationInfo.nativeLibraryDir, "libiperf3.so")
        val verFile = File(binDir, "iperf_version.txt")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        val isReady = (iperfFile.exists() && iperfFile.length() > 50_000) ||
                (fallbackFile.exists() && fallbackFile.length() > 50_000)

        val versionText = if (verFile.exists() && verFile.length() > 0) verFile.readText().trim() else "v3.17 (Official CLI)"

        if (isReady) {
            val target = if (iperfFile.exists() && iperfFile.length() > 50_000) iperfFile else fallbackFile
            val sizeMb = String.format(Locale.US, "%.1f MB", target.length() / (1024.0 * 1024.0))
            val dateStr = if (target.lastModified() > 0) sdf.format(Date(target.lastModified())) else "系统预置"
            return "已就绪: iPerf3 官方原生 CLI [$sizeMb | $versionText | $dateStr]"
        }

        return "未安装官方二进制 (当前使用内置 Socket/JNI 引擎，可点击下方一键下载官方 iPerf3 原生 CLI)"
    }

    private fun ensureExecutablePermissions(file: File) {
        if (!file.exists()) return
        file.setReadable(true, false)
        file.setWritable(true, false)
        file.setExecutable(true, false)
        try {
            val isRooted = try { RootUtils.isRootAvailable() } catch (_: Exception) { false }
            if (isRooted) {
                RootUtils.executeSuCmd("chmod 777 ${file.absolutePath}")
            }
        } catch (_: Exception) {}
        try { Runtime.getRuntime().exec(arrayOf("/system/bin/chmod", "777", file.absolutePath)).waitFor() } catch (_: Exception) {}
        try { Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath)).waitFor() } catch (_: Exception) {}
        try { Runtime.getRuntime().exec(arrayOf("chmod", "+x", file.absolutePath)).waitFor() } catch (_: Exception) {}
    }

    fun getIperfExecutable(context: Context): File? {
        val binDir = File(context.filesDir, "native_bin")
        if (!binDir.exists()) binDir.mkdirs()

        val candidates = listOf(
            File(binDir, "iperf3"),
            File(binDir, "iperf"),
            File(context.applicationInfo.nativeLibraryDir, "libiperf3.so"),
            File(context.applicationInfo.nativeLibraryDir, "libiperf.so")
        )

        for (cand in candidates) {
            if (cand.exists() && cand.length() > 50_000) {
                ensureExecutablePermissions(cand)
                return cand
            }
        }
        return null
    }

    suspend fun verifyBinary(context: Context, execMode: ProcessExecMode = ProcessExecMode.AUTO): String = withContext(Dispatchers.IO) {
        val exec = getIperfExecutable(context) ?: return@withContext "❌ 未检测到官方 iperf3 二进制文件，请先点击【下载/更新】"
        try {
            ensureExecutablePermissions(exec)
            val spawnResult = NativeProcessLauncher.startProcess(
                context = context,
                binaryFile = exec,
                args = listOf("-v"),
                execMode = execMode
            )
            val proc = spawnResult.process
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()

            if (output.isNotBlank()) {
                "✅ iperf3 二进制运行正常 [${spawnResult.modeUsed}]:\n${output.trim()}"
            } else {
                "✅ 二进制就绪 [${spawnResult.modeUsed}]: ${exec.name} (${exec.length() / 1024} KB)"
            }
        } catch (e: Exception) {
            "⚠️ 验证执行异常: ${e.message}\n提示: 若设备已 Root，请授予 Root 权限；未 Root 设备部分系统支持直接运行。"
        }
    }

    suspend fun downloadOrUpdateBinaryOnline(
        context: Context,
        sourceChoice: String,
        customProxy: String,
        onProgress: (String) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val binDir = File(context.filesDir, "native_bin")
            if (!binDir.exists()) binDir.mkdirs()

            val abis = Build.SUPPORTED_ABIS
            val primaryAbi = if (abis.isNotEmpty()) abis[0] else "arm64-v8a"

            // Target filenames according to https://github.com/userdocs/iperf3-static/releases
            val (primaryAssetName, fallbackAssetNames) = when {
                primaryAbi.contains("arm64", ignoreCase = true) -> {
                    "iperf3-arm64v8" to listOf("iperf3-arm64v8", "iperf3-arm64", "iperf3-android-arm64")
                }
                primaryAbi.contains("armeabi", ignoreCase = true) || primaryAbi.contains("armv7", ignoreCase = true) -> {
                    "iperf3-arm32v7" to listOf("iperf3-arm32v7", "iperf3-armv7", "iperf3-arm", "iperf3-android-arm")
                }
                primaryAbi.contains("x86_64", ignoreCase = true) -> {
                    "iperf3-x86_64" to listOf("iperf3-x86_64", "iperf3-amd64")
                }
                primaryAbi.contains("x86", ignoreCase = true) -> {
                    "iperf3-x86" to listOf("iperf3-x86", "iperf3-i386")
                }
                else -> {
                    "iperf3-arm64v8" to listOf("iperf3-arm64v8", "iperf3-android-arm64")
                }
            }

            onProgress("正在从 GitHub (userdocs/iperf3-static) 获取最新 Release 版本与文件列表...")
            val releaseInfo = try {
                DownloadProxyManager.fetchLatestReleaseTag("userdocs/iperf3-static", sourceChoice, customProxy)
            } catch (_: Exception) {
                null
            }

            val versionTag = releaseInfo?.tagName ?: "latest"
            onProgress("解析完成: 版本 [$versionTag]，目标架构文件 [$primaryAssetName]")

            val candidateRawUrls = mutableListOf<String>()

            // 1. URLs from dynamic release assets if available
            if (releaseInfo != null && releaseInfo.assets.isNotEmpty()) {
                val matchedAsset = releaseInfo.assets.firstOrNull { asset ->
                    fallbackAssetNames.any { it.equals(asset.name, ignoreCase = true) }
                } ?: releaseInfo.assets.firstOrNull { asset ->
                    asset.name.contains(primaryAssetName, ignoreCase = true)
                }

                if (matchedAsset != null) {
                    candidateRawUrls.add(matchedAsset.downloadUrl)
                }
            }

            // 2. Specific tag release download URLs
            if (releaseInfo?.tagName?.isNotBlank() == true) {
                for (assetName in fallbackAssetNames) {
                    candidateRawUrls.add("https://github.com/userdocs/iperf3-static/releases/download/${releaseInfo.tagName}/$assetName")
                }
            }

            // 3. Official latest release direct download URLs
            for (assetName in fallbackAssetNames) {
                candidateRawUrls.add("https://github.com/userdocs/iperf3-static/releases/latest/download/$assetName")
            }

            // 4. Master branch fallback
            for (assetName in fallbackAssetNames) {
                candidateRawUrls.add("https://raw.githubusercontent.com/userdocs/iperf3-static/master/$assetName")
            }

            val distinctUrls = mutableListOf<String>()
            for (raw in candidateRawUrls.distinct()) {
                distinctUrls.addAll(DownloadProxyManager.buildDownloadUrls(raw, sourceChoice, customProxy))
            }

            val tempFile = File(context.cacheDir, "temp_iperf3_download")
            for (urlStr in distinctUrls.distinct()) {
                try {
                    onProgress("正在连接镜像下载: $urlStr")
                    val url = java.net.URL(urlStr)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 12000
                    conn.readTimeout = 35000
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")

                    if (conn.responseCode in 200..299) {
                        conn.inputStream.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 50_000) {
                            val sizeKb = tempFile.length() / 1024
                            onProgress("下载成功 ($sizeKb KB)，正在校验权限并安装至本地...")
                            val destFile = File(binDir, "iperf3")
                            tempFile.copyTo(destFile, overwrite = true)
                            tempFile.delete()

                            ensureExecutablePermissions(destFile)

                            // Save version tag
                            val verFile = File(binDir, "iperf_version.txt")
                            verFile.writeText("$versionTag ($primaryAssetName)")

                            onProgress("iPerf3 官方原生可执行二进制 ($primaryAssetName @ $versionTag) 部署就绪！")
                            return@withContext Result.success(true)
                        }
                    }
                } catch (e: Exception) {
                    onProgress("节点连接异常: ${e.message}")
                }
            }

            return@withContext Result.failure(Exception("下载 iPerf3 官方原生二进制失败，请检查网络或切换加速节点"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    fun stopIperf() {
        isStopping.set(true)
        try {
            activeProcess?.destroy()
        } catch (_: Exception) {}
        try {
            activeProcess?.destroyForcibly()
        } catch (_: Exception) {}
        activeProcess = null

        try {
            activeServerSocket?.close()
        } catch (_: Exception) {}
        activeServerSocket = null

        val isRooted = try { RootUtils.isRootAvailable() } catch (_: Exception) { false }
        if (isRooted) {
            try {
                RootUtils.executeSuCmd("killall -9 iperf3 2>/dev/null; pkill -9 -f iperf3 2>/dev/null; killall -9 iperf 2>/dev/null; pkill -9 -f iperf 2>/dev/null")
            } catch (_: Exception) {}
        }
        try {
            Runtime.getRuntime().exec(arrayOf("killall", "-9", "iperf3")).waitFor()
        } catch (_: Exception) {}
        try {
            Runtime.getRuntime().exec(arrayOf("pkill", "-9", "-f", "iperf3")).waitFor()
        } catch (_: Exception) {}
        try {
            Runtime.getRuntime().exec(arrayOf("pkill", "-9", "-f", "iperf")).waitFor()
        } catch (_: Exception) {}
    }

    fun runServerMode(
        context: Context,
        listenPort: Int = 5201,
        execMode: ProcessExecMode = ProcessExecMode.AUTO
    ): Flow<IperfPoint> = callbackFlow<IperfPoint> {
        isStopping.set(false)
        stopIperf()
        isStopping.set(false)

        val localIps = getLocalIpAddresses()
        val primaryLocalIp = localIps.firstOrNull()?.split(" ")?.firstOrNull() ?: "192.168.x.x"

        val exec = getIperfExecutable(context)
        if (exec == null) {
            trySend(
                IperfPoint(
                    sec = 0,
                    transferMB = 0f,
                    bitrateMbps = 0f,
                    isComplete = true,
                    summaryText = "❌ 未检测到 iperf3 官方二进制文件。\n\n请在上方【iPerf3 原生二进制管理】中点击【下载/更新】下载官方二进制。"
                )
            )
            close()
            return@callbackFlow
        }

        ensureExecutablePermissions(exec)
        val binDir = exec.parentFile ?: context.filesDir
        val cmdString = "iperf3 -s -p $listenPort -i 1 --forceflush"

        var currentSec = 0
        var lastTransferMB = 0f
        var lastBitrateMbps = 0f
        var currentClient = ""

        try {
            val spawnResult = NativeProcessLauncher.startProcess(
                context = context,
                binaryFile = exec,
                args = listOf("-s", "-p", listenPort.toString(), "-i", "1", "--forceflush"),
                execMode = execMode,
                workDir = binDir
            )

            trySend(
                IperfPoint(
                    sec = 0,
                    transferMB = 0f,
                    bitrateMbps = 0f,
                    logMessage = "【iPerf3 服务端 | 运行模式: ${spawnResult.modeUsed}】\n" +
                            "执行指令: $cmdString\n" +
                            "监听端口: 0.0.0.0:$listenPort (TCP/UDP)\n" +
                            "本机网络: ${localIps.joinToString(", ")}\n" +
                            "👉 客户端测速指令: iperf3 -c $primaryLocalIp -p $listenPort"
                )
            )

            val proc = spawnResult.process
            activeProcess = proc

            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String? = null

            while (!isStopping.get()) {
                line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isBlank()) continue

                Log.d(TAG, "[iPerf3 Server] $trimmed")

                // Check accepted client connection
                val acceptMatch = ACCEPTED_CONN_REGEX.find(trimmed)
                if (acceptMatch != null) {
                    val clientIp = acceptMatch.groupValues[1]
                    val clientPort = acceptMatch.groupValues[2]
                    currentClient = "$clientIp:$clientPort"
                    trySend(
                        IperfPoint(
                            sec = currentSec,
                            transferMB = lastTransferMB,
                            bitrateMbps = lastBitrateMbps,
                            clientIp = currentClient,
                            logMessage = "🟢 收到客户端连接: $currentClient，正在进行带宽压测..."
                        )
                    )
                    continue
                }

                // Check interval metrics
                val intervalMatch = INTERVAL_REGEX.find(trimmed)
                if (intervalMatch != null) {
                    try {
                        val id = intervalMatch.groupValues[1]
                        val endSec = intervalMatch.groupValues[3].toFloatOrNull()?.toInt() ?: (currentSec + 1)
                        val transferVal = intervalMatch.groupValues[4].toFloatOrNull() ?: 0f
                        val transferUnit = intervalMatch.groupValues[5]
                        val bitrateVal = intervalMatch.groupValues[6].toFloatOrNull() ?: 0f
                        val bitrateUnit = intervalMatch.groupValues[7]
                        val jitterVal = intervalMatch.groupValues.getOrNull(8)?.toFloatOrNull() ?: 0f

                        val transferMB = when {
                            transferUnit.contains("K", ignoreCase = true) -> transferVal / 1024f
                            transferUnit.contains("G", ignoreCase = true) -> transferVal * 1024f
                            else -> transferVal
                        }

                        val bitrateMbps = when {
                            bitrateUnit.contains("K", ignoreCase = true) -> bitrateVal / 1000f
                            bitrateUnit.contains("G", ignoreCase = true) -> bitrateVal * 1000f
                            else -> bitrateVal
                        }

                        currentSec = endSec
                        lastTransferMB = transferMB
                        lastBitrateMbps = bitrateMbps

                        trySend(
                            IperfPoint(
                                sec = currentSec,
                                transferMB = transferMB,
                                bitrateMbps = bitrateMbps,
                                jitterMs = jitterVal,
                                clientIp = currentClient,
                                logMessage = trimmed
                            )
                        )
                    } catch (_: Exception) {}
                } else {
                    trySend(
                        IperfPoint(
                            sec = currentSec,
                            transferMB = lastTransferMB,
                            bitrateMbps = lastBitrateMbps,
                            clientIp = currentClient,
                            logMessage = trimmed
                        )
                    )
                }
            }
        } catch (e: Exception) {
            if (!isStopping.get()) {
                trySend(
                    IperfPoint(
                        sec = 0,
                        transferMB = 0f,
                        bitrateMbps = 0f,
                        isComplete = true,
                        summaryText = "❌ iPerf3 服务端异常退出: ${e.message}\n提示: 若设备已 Root，请在管理卡片中选择【仅使用 Root 权限】。"
                    )
                )
            }
        }

        awaitClose {
            stopIperf()
        }
    }.flowOn(Dispatchers.IO)

    fun runClientTest(
        context: Context,
        serverHost: String,
        port: Int = 5201,
        durationSec: Int = 5,
        protocol: String = "TCP", // TCP or UDP
        bandwidthLimit: String = "0", // e.g. "10M", "100M", "0" = unlimited
        parallelStreams: Int = 1, // -P
        isReverse: Boolean = false, // -R
        execMode: ProcessExecMode = ProcessExecMode.AUTO
    ): Flow<IperfPoint> = callbackFlow<IperfPoint> {
        isStopping.set(false)
        stopIperf()
        isStopping.set(false)

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

        val exec = getIperfExecutable(context)
        if (exec == null) {
            trySend(
                IperfPoint(
                    sec = 0,
                    transferMB = 0f,
                    bitrateMbps = 0f,
                    isComplete = true,
                    summaryText = "❌ 未检测到 iperf3 官方二进制文件。\n\n请在上方【iPerf3 原生二进制管理】中点击【下载/更新】下载官方二进制。"
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

        ensureExecutablePermissions(exec)
        val binDir = exec.parentFile ?: context.filesDir

        val rawArgs = mutableListOf(
            "-c", target,
            "-p", port.toString(),
            "-t", testDuration.toString(),
            "-P", streamsCount.toString(),
            "-i", "1",
            "--forceflush"
        )
        if (isReverse) rawArgs.add("-R")
        if (isUdp) rawArgs.add("-u")
        if (bandwidthLimit.isNotBlank() && bandwidthLimit != "0") {
            rawArgs.add("-b")
            rawArgs.add(bandwidthLimit)
        }

        val displayCmd = "iperf3 ${rawArgs.joinToString(" ")}"
        val fullOutput = StringBuilder()
        var currentSec = 0
        var lastBitrate = 0f
        var lastTransfer = 0f

        try {
            val spawnResult = NativeProcessLauncher.startProcess(
                context = context,
                binaryFile = exec,
                args = rawArgs,
                execMode = execMode,
                workDir = binDir
            )

            trySend(
                IperfPoint(
                    sec = 0,
                    transferMB = 0f,
                    bitrateMbps = 0f,
                    logMessage = "已启动官方原生 iPerf3 CLI 引擎 [${spawnResult.modeUsed}]: $displayCmd"
                )
            )

            val proc = spawnResult.process
            activeProcess = proc

            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String? = null

            while (!isStopping.get()) {
                line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isBlank()) continue

                fullOutput.append(trimmed).append("\n")

                val match = INTERVAL_REGEX.find(trimmed)
                if (match != null) {
                    try {
                        val id = match.groupValues[1]
                        val endSec = match.groupValues[3].toFloatOrNull()?.toInt() ?: (currentSec + 1)
                        val transferVal = match.groupValues[4].toFloatOrNull() ?: 0f
                        val transferUnit = match.groupValues[5]
                        val bitrateVal = match.groupValues[6].toFloatOrNull() ?: 0f
                        val bitrateUnit = match.groupValues[7]
                        val jitterVal = match.groupValues.getOrNull(8)?.toFloatOrNull() ?: 0f

                        val transferMB = when {
                            transferUnit.contains("K", ignoreCase = true) -> transferVal / 1024f
                            transferUnit.contains("G", ignoreCase = true) -> transferVal * 1024f
                            else -> transferVal
                        }

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
                    } catch (_: Exception) {}
                } else {
                    trySend(
                        IperfPoint(
                            sec = currentSec,
                            transferMB = lastTransfer,
                            bitrateMbps = lastBitrate,
                            logMessage = trimmed
                        )
                    )
                }
            }

            proc.waitFor()

            val summary = "iPerf3 官方原生 CLI 测试报告 [$target:$port]\n" +
                    "指令: $displayCmd\n" +
                    "模式: $protoLabel | $modeLabel | 并发数: $streamsCount | 时长: ${testDuration}s\n\n" +
                    fullOutput.toString().takeLast(1200)

            trySend(
                IperfPoint(
                    sec = testDuration,
                    transferMB = lastTransfer,
                    bitrateMbps = lastBitrate,
                    isComplete = true,
                    summaryText = summary
                )
            )
            close()
        } catch (e: Exception) {
            trySend(
                IperfPoint(
                    sec = 0,
                    transferMB = 0f,
                    bitrateMbps = 0f,
                    isComplete = true,
                    summaryText = "❌ iPerf3 客户端运行异常: ${e.message}\n提示: 若设备已 Root，请在管理卡片中选择【仅使用 Root 权限】。"
                )
            )
            close()
        }

        awaitClose {
            stopIperf()
        }
    }.flowOn(Dispatchers.IO)
}
