package com.yangyx.iptools.data.tools

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "FrpEngine"

enum class FrpProxyType(val label: String) {
    TCP("TCP 映射"),
    UDP("UDP 映射"),
    HTTP("HTTP Web"),
    HTTPS("HTTPS 加密"),
    SOCKS5("SOCKS5 代理")
}

data class FrpProxyConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val type: FrpProxyType = FrpProxyType.TCP,
    val localIp: String = "",
    val localPort: Int = 0,
    val remotePort: Int = 0,
    val useEncryption: Boolean = false,
    val useCompression: Boolean = false,
    val customDomains: String = "",
    val socksUser: String = "",
    val socksPass: String = "",
    val isEnabled: Boolean = true
)

data class FrpClientConfig(
    val serverAddr: String = "",
    val serverPort: Int = 0,
    val authToken: String = "",
    val proxies: List<FrpProxyConfig> = emptyList()
)

data class FrpServerConfig(
    val bindPort: Int = 0,
    val authToken: String = "",
    val dashboardPort: Int = 0,
    val dashboardUser: String = "",
    val dashboardPwd: String = "",
    val maxPoolCount: Int = 0
)

data class FrpStatus(
    val isRunning: Boolean = false,
    val activeConnections: Int = 0,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val activeTunnels: List<String> = emptyList(),
    val lastError: String? = null
)

object FrpEngine {

    private val isServerRunning = AtomicBoolean(false)
    private val isClientRunning = AtomicBoolean(false)

    private var serverJob: Job? = null
    private var clientJob: Job? = null

    private var frpcProcess: Process? = null
    private var frpsProcess: Process? = null

    private val _serverStatus = MutableStateFlow(FrpStatus())
    val serverStatus: StateFlow<FrpStatus> = _serverStatus.asStateFlow()

    private val _clientStatus = MutableStateFlow(FrpStatus())
    val clientStatus: StateFlow<FrpStatus> = _clientStatus.asStateFlow()

    private val _serverLogs = MutableStateFlow<List<String>>(emptyList())
    val serverLogs: StateFlow<List<String>> = _serverLogs.asStateFlow()

    private val _clientLogs = MutableStateFlow<List<String>>(emptyList())
    val clientLogs: StateFlow<List<String>> = _clientLogs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private fun addServerLog(msg: String) {
        val timeStr = dateFormat.format(Date())
        val formattedMsg = "[$timeStr] $msg"
        Log.i(TAG, "[FRPS] $formattedMsg")
        _serverLogs.update { (it + formattedMsg).takeLast(200) }
    }

    private fun addClientLog(msg: String) {
        val timeStr = dateFormat.format(Date())
        val formattedMsg = "[$timeStr] $msg"
        Log.i(TAG, "[FRPC] $formattedMsg")
        _clientLogs.update { (it + formattedMsg).takeLast(200) }
    }

    /**
     * Locate or extract the official native frp executable binary packaged in APK jniLibs / Assets / Online Download
     */
    fun getBinaryStatusInfo(context: Context): String {
        val binDir = File(context.filesDir, "native_bin")
        val frpcFile = File(binDir, "frpc")
        val frpsFile = File(binDir, "frps")
        val verFile = File(binDir, "frp_version.txt")
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())

        val frpcReady = frpcFile.exists() && frpcFile.length() > 500_000
        val frpsReady = frpsFile.exists() && frpsFile.length() > 500_000

        val versionText = if (verFile.exists() && verFile.length() > 0) verFile.readText().trim() else "最新版"

        if (frpcReady || frpsReady) {
            val frpcSize = if (frpcReady) String.format("%.1f MB", frpcFile.length() / (1024.0 * 1024.0)) else "未就绪"
            val frpsSize = if (frpsReady) String.format("%.1f MB", frpsFile.length() / (1024.0 * 1024.0)) else "未就绪"
            val lastMod = maxOf(if (frpcReady) frpcFile.lastModified() else 0L, if (frpsReady) frpsFile.lastModified() else 0L)
            val dateStr = if (lastMod > 0) sdf.format(java.util.Date(lastMod)) else "系统预置"

            return "已就绪 (frpc: $frpcSize, frps: $frpsSize | 版本: $versionText | 更新时间: $dateStr)"
        }

        return "未就绪 (FRP Native 可执行二进制文件未下载，请点击下方更新)"
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

            val abis = android.os.Build.SUPPORTED_ABIS
            val primaryAbi = if (abis.isNotEmpty()) abis[0] else "arm64-v8a"

            onProgress("正在从 GitHub 动态获取 FRP 最新 Release 版本...")
            val releaseInfo = DownloadProxyManager.fetchLatestReleaseTag("fatedier/frp", sourceChoice, customProxy)
            val (version, distinctUrls) = getFrpCandidateUrls(releaseInfo, sourceChoice, customProxy)

            onProgress("正在通过网络代理下载最新 FRP 原生包 ($version)...")
            val tempTarGz = File(context.cacheDir, "temp_frp_download.tar.gz")

            for (urlStr in distinctUrls) {
                try {
                    onProgress("正在连接节点: $urlStr")
                    val url = java.net.URL(urlStr)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.instanceFollowRedirects = true

                    if (conn.responseCode in 200..299) {
                        conn.inputStream.use { input ->
                            tempTarGz.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempTarGz.exists() && tempTarGz.length() > 100_000) {
                            onProgress("下载成功 (${tempTarGz.length() / 1024} KB)，正在解压提取 frpc & frps...")

                            val frpcFile = File(binDir, "frpc")
                            val frpsFile = File(binDir, "frps")

                            val extractedFrpc = extractTarGzFile(tempTarGz, "frpc", frpcFile)
                            val extractedFrps = extractTarGzFile(tempTarGz, "frps", frpsFile)

                            tempTarGz.delete()

                            if (extractedFrpc || extractedFrps || frpcFile.exists() || frpsFile.exists()) {
                                if (frpcFile.exists()) {
                                    frpcFile.setExecutable(true, false)
                                    try { Runtime.getRuntime().exec(arrayOf("chmod", "755", frpcFile.absolutePath)).waitFor() } catch (_: Exception) {}
                                }
                                if (frpsFile.exists()) {
                                    frpsFile.setExecutable(true, false)
                                    try { Runtime.getRuntime().exec(arrayOf("chmod", "755", frpsFile.absolutePath)).waitFor() } catch (_: Exception) {}
                                }

                                // Persist version
                                val verFile = File(binDir, "frp_version.txt")
                                verFile.writeText(version)

                                onProgress("FRP 最新二进制 ($version) 更新部署成功!")
                                return@withContext Result.success(true)
                            }
                        }
                    }
                } catch (e: Exception) {
                    onProgress("连接节点异常: ${e.message}")
                }
            }
            return@withContext Result.failure(Exception("下载 FRP 失败，请检查网络或切换加速代理"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private fun ensureExecutablePermissions(file: File) {
        if (!file.exists()) return
        file.setReadable(true, false)
        file.setWritable(true, false)
        file.setExecutable(true, false)
        try { Runtime.getRuntime().exec(arrayOf("/system/bin/chmod", "777", file.absolutePath)).waitFor() } catch (_: Exception) {}
        try { Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath)).waitFor() } catch (_: Exception) {}
        try { Runtime.getRuntime().exec(arrayOf("chmod", "+x", file.absolutePath)).waitFor() } catch (_: Exception) {}
    }

    private fun prepareFrpForExecution(context: Context, binaryName: String, binFile: File): File {
        val execDir = File(context.filesDir, "native_bin").apply { if (!exists()) mkdirs() }
        val execFile = File(execDir, binaryName)
        if (binFile.canonicalPath != execFile.canonicalPath) {
            try {
                binFile.copyTo(execFile, overwrite = true)
            } catch (_: Exception) {}
        }

        val runTarget = if (execFile.exists() && execFile.length() > 300_000) execFile else binFile
        ensureExecutablePermissions(runTarget)
        return runTarget
    }

    private fun getFrpExecutable(context: Context, binaryName: String, logger: (String) -> Unit): File? {
        val binDir = File(context.filesDir, "native_bin")
        if (!binDir.exists()) binDir.mkdirs()

        val simpleName = binaryName.removePrefix("lib").removeSuffix(".so")
        val targetFile = File(binDir, simpleName)

        // Step 1: Check if already extracted and valid (> 500KB)
        if (targetFile.exists() && targetFile.length() > 500_000) {
            targetFile.setExecutable(true, false)
            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "755", targetFile.absolutePath)).waitFor()
            } catch (_: Exception) {}
            return targetFile
        }

        val soName = "lib$simpleName.so"

        // Step 2: Check nativeLibraryDir (system native lib dir)
        val nativeLibFile = File(context.applicationInfo.nativeLibraryDir, soName)
        if (nativeLibFile.exists() && nativeLibFile.length() > 500_000) {
            try {
                nativeLibFile.copyTo(targetFile, overwrite = true)
                targetFile.setExecutable(true, false)
                Runtime.getRuntime().exec(arrayOf("chmod", "755", targetFile.absolutePath)).waitFor()
                logger("✅ 已从系统 Native 库目录载入 $simpleName (${targetFile.length()} 字节)")
                return targetFile
            } catch (e: Exception) {
                logger("⚠️ 从 nativeLibraryDir 复制失败: ${e.message}")
            }
        }

        // Step 3: Open APK sourceDir ZipFile and search lib/<abi>/lib<name>.so
        try {
            val apkFile = File(context.applicationInfo.sourceDir)
            if (apkFile.exists()) {
                java.util.zip.ZipFile(apkFile).use { zip ->
                    val abis = android.os.Build.SUPPORTED_ABIS
                    var targetEntry: java.util.zip.ZipEntry? = null

                    for (abi in abis) {
                        val entryPath = "lib/$abi/$soName"
                        val entry = zip.getEntry(entryPath)
                        if (entry != null) {
                            targetEntry = entry
                            logger("🔍 在 APK ZIP 中匹配架构 ($abi) 二进制: $entryPath")
                            break
                        }
                    }

                    if (targetEntry == null) {
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.name.endsWith(soName)) {
                                targetEntry = entry
                                logger("🔍 在 APK 中找到二进制: ${entry.name}")
                                break
                            }
                        }
                    }

                    if (targetEntry != null) {
                        zip.getInputStream(targetEntry).use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        targetFile.setExecutable(true, false)
                        Runtime.getRuntime().exec(arrayOf("chmod", "755", targetFile.absolutePath)).waitFor()
                        logger("✅ 成功从 APK ZIP 提取并安装 $simpleName (${targetFile.length()} 字节)")
                        return targetFile
                    }
                }
            }
        } catch (e: Exception) {
            logger("⚠️ 从 APK ZIP 解压二进制失败: ${e.message}")
        }

        // Step 4: Check Assets directory (frpc_x86_64.so, frpc_arm64.so, etc.)
        try {
            val abis = android.os.Build.SUPPORTED_ABIS
            val assetNamesToTry = mutableListOf<String>()
            for (abi in abis) {
                if (abi.contains("x86_64")) {
                    assetNamesToTry.add("${simpleName}_x86_64.so")
                } else if (abi.contains("v8a") || abi.contains("arm64")) {
                    assetNamesToTry.add("${simpleName}_arm64.so")
                }
            }
            assetNamesToTry.add("${simpleName}.so")

            val assetList = context.assets.list("") ?: emptyArray()
            for (assetName in assetNamesToTry) {
                if (assetList.contains(assetName)) {
                    logger("🔍 找到 Assets 预置文件 $assetName，正在提取...")
                    context.assets.open(assetName).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    targetFile.setExecutable(true, false)
                    Runtime.getRuntime().exec(arrayOf("chmod", "755", targetFile.absolutePath)).waitFor()
                    logger("✅ 成功从 Assets 部署 $simpleName (${targetFile.length()} 字节)")
                    return targetFile
                }
            }
        } catch (e: Exception) {
            logger("⚠️ 从 Assets 读取失败: ${e.message}")
        }

        // Step 5: Online Download Fallback from Official GitHub Release (fatedier/frp)
        logger("🌐 未能在本地找到预置二进制，正在从官方 GitHub Release 下载 $simpleName ...")
        return downloadAndExtractFrp(context, simpleName, targetFile, logger)
    }

    private fun getFrpCandidateUrls(
        releaseInfo: GitHubReleaseInfo?,
        sourceChoice: String,
        customProxy: String
    ): Pair<String, List<String>> {
        val version = releaseInfo?.tagName ?: "latest"
        val verNum = version.removePrefix("v")

        val abis = android.os.Build.SUPPORTED_ABIS
        val primaryAbi = if (abis.isNotEmpty()) abis[0] else "arm64-v8a"

        val (primaryArchs, matchKeywords) = when {
            primaryAbi.contains("x86_64") -> Pair(
                listOf("linux_amd64", "android_amd64"),
                listOf("amd64", "x86_64")
            )
            primaryAbi.contains("v8a") || primaryAbi.contains("arm64") -> Pair(
                listOf("android_arm64", "linux_arm64"),
                listOf("arm64", "aarch64")
            )
            primaryAbi.contains("v7a") || primaryAbi.contains("arm") -> Pair(
                listOf("linux_arm", "android_arm", "linux_armv7"),
                listOf("arm")
            )
            else -> Pair(
                listOf("linux_amd64", "android_amd64"),
                listOf("amd64", "x86_64")
            )
        }

        val candidateUrls = mutableListOf<String>()

        // 1. Direct assets from GitHub Release API response
        releaseInfo?.assets?.forEach { asset ->
            val assetName = asset.name.lowercase()
            if (assetName.contains("frp")) {
                val matchesArch = matchKeywords.any { kw -> assetName.contains(kw) }
                val isNotArm64Conflict = if (!primaryAbi.contains("v8a") && !primaryAbi.contains("arm64")) {
                    !assetName.contains("arm64") && !assetName.contains("aarch64")
                } else true

                if (matchesArch && isNotArm64Conflict) {
                    candidateUrls.addAll(DownloadProxyManager.buildDownloadUrls(asset.downloadUrl, sourceChoice, customProxy))
                }
            }
        }

        // 2. Constructed tar.gz filenames: frp_{verNum}_{arch}.tar.gz
        for (arch in primaryArchs) {
            val fname = "frp_${verNum}_${arch}.tar.gz"
            val fnameWithV = "frp_${version}_${arch}.tar.gz"

            val rawUrl1 = "https://github.com/fatedier/frp/releases/download/$version/$fname"
            val rawUrl2 = "https://github.com/fatedier/frp/releases/download/$version/$fnameWithV"

            candidateUrls.addAll(DownloadProxyManager.buildDownloadUrls(rawUrl1, sourceChoice, customProxy))
            candidateUrls.addAll(DownloadProxyManager.buildDownloadUrls(rawUrl2, sourceChoice, customProxy))
        }

        return Pair(version, candidateUrls.distinct())
    }

    private fun downloadAndExtractFrp(context: Context, simpleName: String, targetFile: File, logger: (String) -> Unit): File? {
        val releaseInfo = DownloadProxyManager.fetchLatestReleaseTag("fatedier/frp", "gh.dpik.top", "")
        val (_, urls) = getFrpCandidateUrls(releaseInfo, "gh.dpik.top", "")

        val tempTarGz = File(context.cacheDir, "temp_frp_download.tar.gz")

        for (urlStr in urls) {
            try {
                logger("⬇️ 正在连接下载镜像: $urlStr")
                val url = java.net.URL(urlStr)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true

                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { input ->
                        tempTarGz.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempTarGz.exists() && tempTarGz.length() > 100_000) {
                        logger("📦 下载成功 (${tempTarGz.length() / 1024} KB)，正在解压 $simpleName...")
                        val extracted = extractTarGzFile(tempTarGz, simpleName, targetFile)
                        tempTarGz.delete()
                        if (extracted && targetFile.exists() && targetFile.length() > 500_000) {
                            targetFile.setExecutable(true, false)
                            Runtime.getRuntime().exec(arrayOf("chmod", "755", targetFile.absolutePath)).waitFor()
                            logger("🎉 成功部署官方 FRP Native 二进制: $simpleName (${targetFile.length()} 字节)!")
                            return targetFile
                        }
                    }
                }
            } catch (e: Exception) {
                logger("⚠️ 镜像连接异常: ${e.message}")
            }
        }

        logger("❌ 下载并安装官方 $simpleName 失败，请检查网络连接。")
        return null
    }

    private fun extractTarGzFile(tarGzFile: File, targetName: String, outputFile: File): Boolean {
        try {
            java.util.zip.GZIPInputStream(tarGzFile.inputStream().buffered()).use { gis ->
                val header = ByteArray(512)
                while (true) {
                    var bytesRead = 0
                    while (bytesRead < 512) {
                        val r = gis.read(header, bytesRead, 512 - bytesRead)
                        if (r <= 0) break
                        bytesRead += r
                    }
                    if (bytesRead < 512) break
                    if (header[0] == 0.toByte()) break

                    val filename = String(header, 0, 100, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                    val sizeOctalStr = String(header, 124, 12, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                    val fileSize = try { sizeOctalStr.toLong(8) } catch (_: Exception) { 0L }
                    val typeFlag = header[156]

                    val isFile = typeFlag == '0'.toByte() || typeFlag == 0.toByte()
                    val entryName = filename.substringAfterLast("/")

                    val padding = ((fileSize + 511) / 512 * 512) - fileSize

                    if (isFile && entryName == targetName && fileSize > 0) {
                        outputFile.outputStream().buffered().use { fos ->
                            val buf = ByteArray(8192)
                            var remaining = fileSize
                            while (remaining > 0) {
                                val toRead = minOf(buf.size.toLong(), remaining).toInt()
                                val read = gis.read(buf, 0, toRead)
                                if (read <= 0) break
                                fos.write(buf, 0, read)
                                remaining -= read
                            }
                        }
                        var padRemaining = padding
                        val skipBuf = ByteArray(512)
                        while (padRemaining > 0) {
                            val toSkip = minOf(skipBuf.size.toLong(), padRemaining).toInt()
                            val skipped = gis.read(skipBuf, 0, toSkip)
                            if (skipped <= 0) break
                            padRemaining -= skipped
                        }
                        return true
                    } else {
                        var totalSkip = fileSize + padding
                        val skipBuf = ByteArray(8192)
                        while (totalSkip > 0) {
                            val toSkip = minOf(skipBuf.size.toLong(), totalSkip).toInt()
                            val skipped = gis.read(skipBuf, 0, toSkip)
                            if (skipped <= 0) break
                            totalSkip -= skipped
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Un-targz error: ${e.message}")
        }
        return false
    }

    /**
     * Generates standard INI config for frpc
     */
    fun generateFrpcIni(config: FrpClientConfig): String {
        val sb = StringBuilder()
        sb.append("[common]\n")
        sb.append("server_addr = ").append(config.serverAddr.ifBlank { "127.0.0.1" }).append("\n")
        sb.append("server_port = ").append(config.serverPort).append("\n")
        if (config.authToken.isNotBlank()) {
            sb.append("token = ").append(config.authToken).append("\n")
        }
        sb.append("tls_enable = false\n\n")

        val enabledProxies = config.proxies.filter { it.isEnabled }
        for (proxy in enabledProxies) {
            val ruleName = proxy.name.ifBlank { "rule_${proxy.id.takeLast(4)}" }
            sb.append("[").append(ruleName).append("]\n")
            sb.append("type = ").append(proxy.type.name.lowercase()).append("\n")
            sb.append("local_ip = ").append(proxy.localIp.ifBlank { "127.0.0.1" }).append("\n")
            sb.append("local_port = ").append(proxy.localPort).append("\n")
            if (proxy.remotePort > 0) {
                sb.append("remote_port = ").append(proxy.remotePort).append("\n")
            }
            if (proxy.customDomains.isNotBlank() && (proxy.type == FrpProxyType.HTTP || proxy.type == FrpProxyType.HTTPS)) {
                sb.append("custom_domains = ").append(proxy.customDomains).append("\n")
            }
            sb.append("use_encryption = ").append(proxy.useEncryption).append("\n")
            sb.append("use_compression = ").append(proxy.useCompression).append("\n\n")
        }
        return sb.toString()
    }

    /**
     * Generates standard INI config for frps
     */
    fun generateFrpsIni(config: FrpServerConfig): String {
        val sb = StringBuilder()
        sb.append("[common]\n")
        sb.append("bind_port = ").append(config.bindPort).append("\n")
        if (config.authToken.isNotBlank()) {
            sb.append("token = ").append(config.authToken).append("\n")
        }
        if (config.dashboardPort > 0) {
            sb.append("dashboard_port = ").append(config.dashboardPort).append("\n")
            sb.append("dashboard_user = ").append(config.dashboardUser.ifBlank { "admin" }).append("\n")
            sb.append("dashboard_pwd = ").append(config.dashboardPwd.ifBlank { "admin" }).append("\n")
        }
        sb.append("max_pool_count = 5\n")
        return sb.toString()
    }

    // =========================================================================
    // FRP SERVER (frps) USING OFFICIAL NATIVE BINARY
    // =========================================================================

    fun startServer(context: Context, scope: CoroutineScope, config: FrpServerConfig) {
        if (isServerRunning.getAndSet(true)) {
            stopServer()
        }
        isServerRunning.set(true)
        _serverLogs.value = emptyList()

        addServerLog("🚀 正在初始化官方 Native FRP 服务端引擎 (fatedier/frps)...")

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val iniContent = generateFrpsIni(config)
                val iniFile = File(context.filesDir, "frps.ini")
                iniFile.writeText(iniContent, Charsets.UTF_8)

                addServerLog("📄 已生成服务端配置文件 ${iniFile.name}")

                val binFile = getFrpExecutable(context, "frps") { addServerLog(it) }
                if (binFile == null || !binFile.exists()) {
                    addServerLog("❌ 未找到官方 Native frps 可执行文件 (frps)")
                    stopServer()
                    return@launch
                }

                val runTarget = prepareFrpForExecution(context, "frps", binFile)

                val isRooted = RootUtils.isRootAvailable()
                val rootStatus = RootUtils.getRootStatusDescription()
                val suBinary = RootUtils.findSuBinaryPath()
                if (isRooted) {
                    RootUtils.executeSuCmd("chmod 777 ${runTarget.absolutePath}")
                }

                val pb: ProcessBuilder
                if (isRooted) {
                    addServerLog("⚡ Root 权限状态: $rootStatus (使用 $suBinary 提权启动)")
                    addServerLog("⚡ 启动 Root Native frps 服务端 [监听端口 :${config.bindPort}] ...")
                    pb = ProcessBuilder(suBinary, "-c", "${runTarget.absolutePath} -c ${iniFile.absolutePath}")
                } else {
                    addServerLog("ℹ️ Root 权限状态: $rootStatus")
                    addServerLog("⚡ 启动官方 frps 服务端 [监听端口 :${config.bindPort}] (${runTarget.absolutePath}) ...")
                    pb = ProcessBuilder(runTarget.absolutePath, "-c", iniFile.absolutePath)
                }
                pb.redirectErrorStream(true)
                val proc = pb.start()
                frpsProcess = proc

                _serverStatus.update { it.copy(isRunning = true) }

                val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
                val activeTunnels = mutableListOf<String>()

                var line: String? = null
                while (isServerRunning.get() && proc.isAlive && reader.readLine().also { line = it } != null) {
                    val cleanLine = line?.replace("\u001B\\[[;\\d]*m".toRegex(), "")?.trim() ?: continue
                    if (cleanLine.isBlank()) continue

                    addServerLog(cleanLine)

                    if (cleanLine.contains("frps started successfully")) {
                        addServerLog("✅ 官方 FRP 服务端启动成功，监听端口 :${config.bindPort}")
                    }
                    if (cleanLine.contains("proxy added:")) {
                        val tunnelName = cleanLine.substringAfter("proxy added:").trim()
                        activeTunnels.add(tunnelName)
                        _serverStatus.update { st -> st.copy(activeTunnels = activeTunnels.distinct()) }
                    }
                }

                val exitCode = if (proc.isAlive) proc.waitFor() else proc.exitValue()
                addServerLog("ℹ️ FRP 服务端进程已退出 (ExitCode: $exitCode)")

            } catch (e: Exception) {
                addServerLog("❌ FRP 服务端运行失败: ${e.message ?: e.toString()}")
            } finally {
                _serverStatus.update { it.copy(isRunning = false, activeTunnels = emptyList()) }
                isServerRunning.set(false)
            }
        }
    }

    fun stopServer() {
        isServerRunning.set(false)
        try {
            frpsProcess?.destroy()
            frpsProcess?.destroyForcibly()
        } catch (_: Exception) {}
        frpsProcess = null
        serverJob?.cancel()
        _serverStatus.update { it.copy(isRunning = false, activeTunnels = emptyList()) }
        addServerLog("🛑 FRP 服务端已停止")
    }

    // =========================================================================
    // FRP CLIENT (frpc) USING OFFICIAL NATIVE BINARY
    // =========================================================================

    fun startClient(context: Context, scope: CoroutineScope, config: FrpClientConfig) {
        if (isClientRunning.getAndSet(true)) {
            stopClient()
        }
        isClientRunning.set(true)
        _clientLogs.value = emptyList()

        val enabledCount = config.proxies.count { it.isEnabled }
        addClientLog("🚀 正在初始化官方 Native FRP 客户端引擎 (fatedier/frpc)...")
        addClientLog("🎯 目标服务端: [${config.serverAddr}:${config.serverPort}], 启用映射规则数: $enabledCount")

        clientJob = scope.launch(Dispatchers.IO) {
            try {
                val iniContent = generateFrpcIni(config)
                val iniFile = File(context.filesDir, "frpc.ini")
                iniFile.writeText(iniContent, Charsets.UTF_8)

                addClientLog("📄 已生成客户端配置文件 (${iniFile.name}):")
                iniContent.lines().filter { it.isNotBlank() }.forEach { line ->
                    addClientLog("   $line")
                }

                val binFile = getFrpExecutable(context, "frpc") { addClientLog(it) }
                if (binFile == null || !binFile.exists()) {
                    addClientLog("❌ 未能定位官方 Native frpc 可执行文件 (frpc)")
                    stopClient()
                    return@launch
                }

                val runTarget = prepareFrpForExecution(context, "frpc", binFile)

                val isRooted = RootUtils.isRootAvailable()
                val rootStatus = RootUtils.getRootStatusDescription()
                val suBinary = RootUtils.findSuBinaryPath()
                if (isRooted) {
                    RootUtils.executeSuCmd("chmod 777 ${runTarget.absolutePath}")
                }

                val pb: ProcessBuilder
                if (isRooted) {
                    addClientLog("⚡ Root 权限状态: $rootStatus (使用 $suBinary 提权启动)")
                    addClientLog("⚡ 启动 Root Native frpc 客户端进程 [${runTarget.name}] -c ${iniFile.name} ...")
                    pb = ProcessBuilder(suBinary, "-c", "${runTarget.absolutePath} -c ${iniFile.absolutePath}")
                } else {
                    addClientLog("ℹ️ Root 权限状态: $rootStatus")
                    addClientLog("⚡ 启动官方 frpc 客户端进程 [${runTarget.name}] -c ${iniFile.name} ...")
                    pb = ProcessBuilder(runTarget.absolutePath, "-c", iniFile.absolutePath)
                }
                pb.redirectErrorStream(true)
                val proc = pb.start()
                frpcProcess = proc

                _clientStatus.update { it.copy(isRunning = true) }

                val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
                val activeTunnels = mutableListOf<String>()

                var line: String? = null
                while (isClientRunning.get() && proc.isAlive && reader.readLine().also { line = it } != null) {
                    val cleanLine = line?.replace("\u001B\\[[;\\d]*m".toRegex(), "")?.trim() ?: continue
                    if (cleanLine.isBlank()) continue

                    addClientLog(cleanLine)

                    if (cleanLine.contains("login to server success")) {
                        addClientLog("🎉 官方 FRP 客户端登录服务端成功！")
                    }
                    if (cleanLine.contains("start proxy success") || cleanLine.contains("proxy added:")) {
                        val tunnelName = cleanLine.substringAfter("start proxy success").substringAfter("proxy added:").trim()
                        if (tunnelName.isNotBlank()) {
                            activeTunnels.add(tunnelName)
                            _clientStatus.update { st -> st.copy(activeTunnels = activeTunnels.distinct()) }
                        }
                    }
                }

                val exitCode = if (proc.isAlive) proc.waitFor() else proc.exitValue()
                addClientLog("ℹ️ FRP 客户端进程已结束 (ExitCode: $exitCode)")

            } catch (e: Exception) {
                addClientLog("❌ FRP 客户端执行失败: ${e.message ?: e.toString()}")
            } finally {
                _clientStatus.update { it.copy(isRunning = false, activeTunnels = emptyList()) }
                isClientRunning.set(false)
            }
        }
    }

    fun stopClient() {
        isClientRunning.set(false)
        try {
            frpcProcess?.destroy()
            frpcProcess?.destroyForcibly()
        } catch (_: Exception) {}
        frpcProcess = null
        clientJob?.cancel()
        _clientStatus.update { it.copy(isRunning = false, activeTunnels = emptyList()) }
        addClientLog("🛑 FRP 客户端已停止")
    }
}
