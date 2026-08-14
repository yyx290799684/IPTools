package com.yangyx.iptools.data.tools

import android.content.Context
import com.yangyx.iptools.data.network.HostNameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class FscanPortItem(
    val ip: String,
    val port: Int,
    val serviceName: String = "Unknown",
    val titleOrBanner: String = "",
    val isWeb: Boolean = false
) {
    val formattedHost: String get() = if (ip.contains(":")) "[$ip]" else ip
    val ipPortDisplay: String get() = "$formattedHost:$port"
    val webUrl: String get() = if (port == 443 || port == 8443) "https://$ipPortDisplay" else "http://$ipPortDisplay"
}

data class FscanHostResult(
    val ip: String,
    val hostname: String = "",
    val isAlive: Boolean = true,
    val openPorts: List<Int> = emptyList(),
    val portItems: List<FscanPortItem> = emptyList(),
    val banners: Map<Int, String> = emptyMap(),
    val osHint: String = "Linux/Windows",
    val latencyMs: Long = 0
)

data class FscanProgress(
    val scannedIpsCount: Int,
    val totalIps: Int,
    val aliveHosts: List<FscanHostResult>,
    val currentScanningIp: String,
    val logs: List<String> = emptyList()
)

object FscanEngine {

    fun parsePortRange(portsInput: String): List<Int> {
        val ports = mutableSetOf<Int>()
        val tokens = portsInput.split(",", ";", " ", "\n").map { it.trim() }.filter { it.isNotBlank() }
        for (token in tokens) {
            if (token.contains("-")) {
                try {
                    val rangeParts = token.split("-")
                    val start = rangeParts[0].toInt().coerceIn(1, 65535)
                    val end = rangeParts[1].toInt().coerceIn(1, 65535)
                    val rangeStart = minOf(start, end)
                    val rangeEnd = maxOf(start, end)
                    for (p in rangeStart..rangeEnd) {
                        ports.add(p)
                    }
                } catch (_: Exception) {}
            } else {
                token.toIntOrNull()?.coerceIn(1, 65535)?.let { ports.add(it) }
            }
        }
        return if (ports.isNotEmpty()) ports.toList().sorted() else listOf(21, 22, 80, 443, 445, 1433, 3306, 3389, 6379, 8080)
    }

    fun parseIpRangeOrCidr(input: String): List<String> {
        val trimmed = input.trim()
        val ips = mutableListOf<String>()

        if (trimmed.contains(":")) {
            // IPv6 address
            ips.add(trimmed)
            return ips
        }

        if (trimmed.contains("/")) {
            // CIDR e.g. 192.168.1.0/24
            try {
                val parts = trimmed.split("/")
                val baseIp = parts[0]
                val prefix = parts[1].toInt()
                val ipParts = baseIp.split(".").map { it.toInt() }
                if (ipParts.size == 4 && prefix in 16..30) {
                    val ipInt = (ipParts[0] shl 24) or (ipParts[1] shl 16) or (ipParts[2] shl 8) or ipParts[3]
                    val mask = (-1 shl (32 - prefix))
                    val networkInt = ipInt and mask
                    val numHosts = (1 shl (32 - prefix)) - 2
                    val maxHosts = numHosts.coerceAtMost(512) // Limit for mobile scan safety

                    for (i in 1..maxHosts) {
                        val currentInt = networkInt + i
                        val ipStr = "${(currentInt ushr 24) and 0xFF}.${(currentInt ushr 16) and 0xFF}.${(currentInt ushr 8) and 0xFF}.${currentInt and 0xFF}"
                        ips.add(ipStr)
                    }
                }
            } catch (_: Exception) {}
        } else if (trimmed.contains("-")) {
            // Range e.g. 192.168.1.1-192.168.1.50
            try {
                val parts = trimmed.split("-")
                val startStr = parts[0].trim()
                val endStr = parts[1].trim()

                if (endStr.contains(".")) {
                    val startParts = startStr.split(".").map { it.toInt() }
                    val endParts = endStr.split(".").map { it.toInt() }
                    val startInt = (startParts[0] shl 24) or (startParts[1] shl 16) or (startParts[2] shl 8) or startParts[3]
                    val endInt = (endParts[0] shl 24) or (endParts[1] shl 16) or (endParts[2] shl 8) or endParts[3]
                    val count = (endInt - startInt + 1).coerceAtMost(512)
                    for (i in 0 until count) {
                        val currentInt = startInt + i
                        val ipStr = "${(currentInt ushr 24) and 0xFF}.${(currentInt ushr 16) and 0xFF}.${(currentInt ushr 8) and 0xFF}.${currentInt and 0xFF}"
                        ips.add(ipStr)
                    }
                } else {
                    // e.g. 192.168.1.1-50
                    val startParts = startStr.split(".").map { it.toInt() }
                    val startLast = startParts[3]
                    val endLast = endStr.toInt()
                    for (last in startLast..endLast.coerceAtMost(255)) {
                        ips.add("${startParts[0]}.${startParts[1]}.${startParts[2]}.$last")
                    }
                }
            } catch (_: Exception) {}
        } else {
            ips.add(trimmed)
        }

        return ips
    }

    /**
     * Test and verify fscan executable binary directly on demand
     */
    suspend fun verifyBinary(context: Context): String = withContext(Dispatchers.IO) {
        val binDir = File(context.filesDir, "native_bin")
        val targetFile = File(binDir, "fscan")
        val fallbackFile = File(context.applicationInfo.nativeLibraryDir, "libfscan.so")
        val runFile = if (targetFile.exists() && targetFile.length() > 300_000) targetFile else if (fallbackFile.exists() && fallbackFile.length() > 300_000) fallbackFile else null

        if (runFile == null) {
            return@withContext "❌ 未找到二进制文件 (请先点击下载/更新)"
        }

        ensureExecutablePermissions(runFile)
        val isRooted = try { RootUtils.isRootAvailable() } catch (_: Exception) { false }
        val suBinary = try { RootUtils.findSuBinaryPath() } catch (_: Exception) { "su" }

        try {
            val pb = if (isRooted) {
                RootUtils.executeSuCmd("chmod 777 ${runFile.absolutePath}")
                ProcessBuilder(suBinary, "-c", "cd ${binDir.absolutePath} && ${runFile.absolutePath} -h")
            } else {
                ProcessBuilder(runFile.absolutePath, "-h")
            }
            pb.directory(binDir)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val reader = java.io.BufferedReader(java.io.InputStreamReader(proc.inputStream))
            val outputLines = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim()
                if (!l.isNullOrBlank()) {
                    outputLines.add(l)
                }
            }
            proc.waitFor()
            val exitCode = proc.exitValue()

            // fscan -h exits with code 2 or 0 when it outputs help/flags, confirming it works on CPU architecture
            val hasFscanHelp = outputLines.any { it.contains("fscan", ignoreCase = true) || it.contains("Usage of", ignoreCase = true) || it.contains("flag needs an argument", ignoreCase = true) }
            if (exitCode == 2 || exitCode == 0 || hasFscanHelp) {
                val mode = if (isRooted) "Root 提权" else "普通模式"
                return@withContext "✅ 验证通过 (运行正常, $mode, 架构兼容)"
            } else {
                return@withContext "⚠️ 运行异常 (exitCode=$exitCode: ${outputLines.take(1).joinToString()})"
            }
        } catch (e: Exception) {
            return@withContext "❌ 验证失败 (${e.message ?: e.javaClass.simpleName})"
        }
    }

    /**
     * Locate or extract official native fscan executable binary packaged in APK / Assets / Online Download
     */
    fun getBinaryStatusInfo(context: Context): String {
        val binDir = File(context.filesDir, "native_bin")
        val targetFile = File(binDir, "fscan")
        val verFile = File(binDir, "fscan_version.txt")
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())

        val versionText = if (verFile.exists() && verFile.length() > 0) verFile.readText().trim() else "最新版"

        if (targetFile.exists() && targetFile.length() > 300_000) {
            val sizeMb = String.format("%.2f", targetFile.length() / (1024.0 * 1024.0))
            val dateStr = sdf.format(java.util.Date(targetFile.lastModified()))
            return "已就绪 (fscan $versionText | 大小: ${sizeMb} MB | 文件更新时间: $dateStr)"
        }

        val nativeLibFile = File(context.applicationInfo.nativeLibraryDir, "libfscan.so")
        if (nativeLibFile.exists() && nativeLibFile.length() > 300_000) {
            val sizeMb = String.format("%.2f", nativeLibFile.length() / (1024.0 * 1024.0))
            val dateStr = sdf.format(java.util.Date(nativeLibFile.lastModified()))
            return "已就绪 (系统库 libfscan.so | 大小: ${sizeMb} MB | 时间: $dateStr)"
        }

        return "未拉取 (.ELF 原生二进制未就绪，使用降级 Kotlin 引擎)"
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
            val targetFile = File(binDir, "fscan")

            onProgress("正在从 GitHub 动态获取 fscan 最新 Release 版本...")
            val releaseInfo = DownloadProxyManager.fetchLatestReleaseTag("shadow1ng/fscan", sourceChoice, customProxy)
            val (version, candidateUrls) = getFscanCandidateUrls(releaseInfo, sourceChoice, customProxy)

            onProgress("正在通过网络代理下载最新 fscan 原生二进制 ($version)...")

            val tempDownload = File(context.cacheDir, "temp_fscan_download")

            for (urlStr in candidateUrls) {
                try {
                    onProgress("正在连接节点: $urlStr")
                    val url = java.net.URL(urlStr)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.instanceFollowRedirects = true

                    if (conn.responseCode in 200..299) {
                        conn.inputStream.use { input ->
                            tempDownload.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempDownload.exists() && tempDownload.length() > 100_000) {
                            onProgress("下载成功 (${tempDownload.length() / 1024} KB)，正在解包部署最新二进制...")
                            val isZip = urlStr.endsWith(".zip") || (tempDownload.length() > 4 && isZipHeader(tempDownload))
                            if (isZip) {
                                extractZipFile(tempDownload, "fscan", targetFile)
                            } else {
                                tempDownload.copyTo(targetFile, overwrite = true)
                            }
                            tempDownload.delete()

                            if (targetFile.exists() && targetFile.length() > 300_000) {
                                targetFile.setExecutable(true, false)
                                try {
                                    Runtime.getRuntime().exec(arrayOf("chmod", "755", targetFile.absolutePath)).waitFor()
                                } catch (_: Exception) {}

                                // Persist version string
                                val verFile = File(binDir, "fscan_version.txt")
                                verFile.writeText(version)

                                onProgress("fscan 最新二进制 ($version) 更新部署成功!")
                                return@withContext Result.success(true)
                            }
                        }
                    }
                } catch (e: Exception) {
                    onProgress("连接节点异常: ${e.message}")
                }
            }
            return@withContext Result.failure(Exception("下载 fscan 失败，请检查网络或切换加速代理"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private fun ensureExecutablePermissions(file: File) {
        if (!file.exists()) return
        file.setReadable(true, false)
        file.setWritable(true, false)
        file.setExecutable(true, false)
        try { Runtime.getRuntime().exec(arrayOf("chmod", "777", file.absolutePath)).waitFor() } catch (_: Exception) {}
        try { Runtime.getRuntime().exec(arrayOf("chmod", "+x", file.absolutePath)).waitFor() } catch (_: Exception) {}
        try { Runtime.getRuntime().exec(arrayOf("/system/bin/chmod", "755", file.absolutePath)).waitFor() } catch (_: Exception) {}
    }

    private fun getFscanExecutable(context: Context, logger: (String) -> Unit): File? {
        val binDir = File(context.filesDir, "native_bin")
        if (!binDir.exists()) binDir.mkdirs()

        val targetFile = File(binDir, "fscan")

        // Step 1: Check if already present and valid (> 300KB)
        if (targetFile.exists() && targetFile.length() > 300_000) {
            ensureExecutablePermissions(targetFile)
            return targetFile
        }

        // Step 2: Check nativeLibraryDir
        val soName = "libfscan.so"
        val nativeLibFile = File(context.applicationInfo.nativeLibraryDir, soName)
        if (nativeLibFile.exists() && nativeLibFile.length() > 300_000) {
            try {
                nativeLibFile.copyTo(targetFile, overwrite = true)
                ensureExecutablePermissions(targetFile)
                logger("✅ 已从系统 Native 库目录载入 fscan 二进制 (${targetFile.length()} 字节)")
                return targetFile
            } catch (e: Exception) {
                logger("⚠️ 从 nativeLibraryDir 复制失败: ${e.message}")
            }
        }

        // Step 3: Open APK sourceDir ZipFile and search lib/<abi>/libfscan.so
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
                        logger("✅ 成功从 APK ZIP 提取并部署 fscan (${targetFile.length()} 字节)")
                        return targetFile
                    }
                }
            }
        } catch (e: Exception) {
            logger("⚠️ 从 APK ZIP 解压二进制失败: ${e.message}")
        }

        // Step 4: Check Assets directory
        try {
            val abis = android.os.Build.SUPPORTED_ABIS
            val assetNamesToTry = mutableListOf<String>()
            for (abi in abis) {
                if (abi.contains("x86_64")) {
                    assetNamesToTry.add("fscan_x86_64.so")
                    assetNamesToTry.add("fscan_x86_64")
                } else if (abi.contains("v8a") || abi.contains("arm64")) {
                    assetNamesToTry.add("fscan_arm64.so")
                    assetNamesToTry.add("fscan_arm64")
                }
            }
            assetNamesToTry.add("fscan.so")
            assetNamesToTry.add("fscan")

            val assetList = context.assets.list("") ?: emptyArray()
            for (assetName in assetNamesToTry) {
                if (assetList.contains(assetName)) {
                    logger("🔍 找到 Assets 预置二进制 $assetName，正在解包...")
                    context.assets.open(assetName).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    targetFile.setExecutable(true, false)
                    Runtime.getRuntime().exec(arrayOf("chmod", "755", targetFile.absolutePath)).waitFor()
                    logger("✅ 成功从 Assets 部署 fscan (${targetFile.length()} 字节)")
                    return targetFile
                }
            }
        } catch (e: Exception) {
            logger("⚠️ 从 Assets 读取失败: ${e.message}")
        }

        // Step 5: Online Download Fallback from Official GitHub Release (shadow1ng/fscan)
        logger("🌐 未能在本地找到预置二进制，正在从官方 GitHub / 镜像拉取 fscan 二进制...")
        return downloadFscanBinary(context, targetFile, logger)
    }

    private fun getFscanCandidateUrls(
        releaseInfo: GitHubReleaseInfo?,
        sourceChoice: String,
        customProxy: String
    ): Pair<String, List<String>> {
        val version = releaseInfo?.tagName ?: "latest"
        val verNum = version.removePrefix("v")

        val abis = android.os.Build.SUPPORTED_ABIS
        val primaryAbi = if (abis.isNotEmpty()) abis[0] else "arm64-v8a"

        val (primarySuffix, altSuffixes, matchKeywords) = when {
            primaryAbi.contains("x86_64") -> Triple(
                "_linux_amd64",
                listOf("_amd64", "_x86_64"),
                listOf("amd64", "x86_64")
            )
            primaryAbi.contains("v8a") || primaryAbi.contains("arm64") -> Triple(
                "_linux_arm64",
                listOf("_arm64", "_aarch64"),
                listOf("arm64", "aarch64")
            )
            primaryAbi.contains("v7a") || primaryAbi.contains("arm") -> Triple(
                "_linux_armv7",
                listOf("_linux_armv6", "_linux_armv5", "_linux_arm", "_armv7", "_arm"),
                listOf("armv7", "armv6", "armv5", "arm")
            )
            else -> Triple(
                "_linux_amd64",
                listOf("_amd64", "_x86_64"),
                listOf("amd64", "x86_64")
            )
        }

        val candidateUrls = mutableListOf<String>()

        // 1. Direct assets from GitHub Release API response
        releaseInfo?.assets?.forEach { asset ->
            val assetName = asset.name.lowercase()
            if (assetName.contains("fscan")) {
                val matchesArch = matchKeywords.any { kw -> assetName.contains(kw) }
                val isNotArm64Conflict = if (!primaryAbi.contains("v8a") && !primaryAbi.contains("arm64")) {
                    !assetName.contains("arm64") && !assetName.contains("aarch64")
                } else true

                if (matchesArch && isNotArm64Conflict) {
                    candidateUrls.addAll(DownloadProxyManager.buildDownloadUrls(asset.downloadUrl, sourceChoice, customProxy))
                }
            }
        }

        // 2. Exact pattern matching: fscan_{verNum}_linux_arm64, fscan_2.2.0_linux_arm64, etc.
        val filenamesToTry = mutableListOf<String>()
        filenamesToTry.add("fscan_${verNum}${primarySuffix}")
        filenamesToTry.add("fscan_${version}${primarySuffix}")

        for (alt in altSuffixes) {
            filenamesToTry.add("fscan_${verNum}${alt}")
            filenamesToTry.add("fscan_${version}${alt}")
        }
        filenamesToTry.add("fscan${primarySuffix}")
        filenamesToTry.add("fscan_arm64")
        filenamesToTry.add("fscan_amd64")

        // 3. Add .zip legacy variations
        filenamesToTry.add("fscan_${verNum}${primarySuffix}.zip")
        filenamesToTry.add("fscan_${verNum}_arm64.zip")

        for (fname in filenamesToTry.distinct()) {
            val rawUrl = "https://github.com/shadow1ng/fscan/releases/download/$version/$fname"
            candidateUrls.addAll(DownloadProxyManager.buildDownloadUrls(rawUrl, sourceChoice, customProxy))
        }

        return Pair(version, candidateUrls.distinct())
    }

    private fun downloadFscanBinary(context: Context, targetFile: File, logger: (String) -> Unit): File? {
        val releaseInfo = DownloadProxyManager.fetchLatestReleaseTag("shadow1ng/fscan", "gh.dpik.top", "")
        val (_, urls) = getFscanCandidateUrls(releaseInfo, "gh.dpik.top", "")

        val tempDownload = File(context.cacheDir, "temp_fscan_download")

        for (urlStr in urls) {
            try {
                logger("⬇️ 正在连接镜像拉取 fscan: $urlStr")
                val url = java.net.URL(urlStr)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true

                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { input ->
                        tempDownload.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempDownload.exists() && tempDownload.length() > 100_000) {
                        logger("📦 拉取成功 (${tempDownload.length() / 1024} KB)，正在解包部署...")
                        val isZip = urlStr.endsWith(".zip") || (tempDownload.length() > 4 && isZipHeader(tempDownload))
                        if (isZip) {
                            extractZipFile(tempDownload, "fscan", targetFile)
                        } else {
                            tempDownload.copyTo(targetFile, overwrite = true)
                        }
                        tempDownload.delete()

                        if (targetFile.exists() && targetFile.length() > 300_000) {
                            targetFile.setExecutable(true, false)
                            Runtime.getRuntime().exec(arrayOf("chmod", "755", targetFile.absolutePath)).waitFor()
                            logger("🎉 成功部署官方 fscan Native 二进制: (${targetFile.length()} 字节)!")
                            return targetFile
                        }
                    }
                }
            } catch (e: Exception) {
                logger("⚠️ 镜像连接异常: ${e.message}")
            }
        }

        logger("⚠️ 在线拉取官方 fscan 失败，将降级使用内置 Native 逻辑引擎。")
        return null
    }

    private fun extractZipFile(zipFile: File, targetName: String, outputFile: File): Boolean {
        try {
            java.util.zip.ZipFile(zipFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entrySimpleName = entry.name.substringAfterLast("/")
                    if (!entry.isDirectory && (entrySimpleName == targetName || entrySimpleName.startsWith("fscan"))) {
                        zip.getInputStream(entry).use { input ->
                            outputFile.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }
                        return true
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun isZipHeader(file: File): Boolean {
        try {
            file.inputStream().use { input ->
                val buf = ByteArray(4)
                if (input.read(buf) == 4) {
                    return buf[0] == 0x50.toByte() && buf[1] == 0x4B.toByte()
                }
            }
        } catch (_: Exception) {}
        return false
    }

    fun executeFscan(
        context: Context? = null,
        targetInput: String,
        portRangeInput: String = "21, 22, 80, 443, 445, 1433, 3306, 3389, 6379, 8080",
        disableBrute: Boolean = true,
        disablePing: Boolean = false,
        enableWebScan: Boolean = true,
        portScanThreads: Int = 1000,
        timeoutSec: Int = 1,
        timeoutMs: Int = 500,
        concurrency: Int = 50
    ): Flow<FscanProgress> = flow {
        val ipList = parseIpRangeOrCidr(targetInput)
        if (ipList.isEmpty()) return@flow

        val portsToProbe = parsePortRange(portRangeInput)
        val totalIps = ipList.size

        val logs = mutableListOf<String>()
        logs.add("   ___  _   _   ___   ___  _   _ ")
        logs.add("  / __|| | | | / __| / __|| | | |")
        logs.add("  \\__ \\| |_| || (__ | (__ | |_| |")
        logs.add("  |___/ \\__,_| \\___| \\___| \\__,_|")
        logs.add("")
        logs.add("[+] Target: $targetInput")
        logs.add("[+] Ports: ${if (portsToProbe.size > 8) "${portsToProbe.take(5).joinToString(",")},...(${portsToProbe.size} ports)" else portsToProbe.joinToString(",")}")

        emit(
            FscanProgress(
                scannedIpsCount = 0,
                totalIps = totalIps,
                aliveHosts = emptyList(),
                currentScanningIp = ipList.first(),
                logs = logs.toList()
            )
        )

        // Try binary process execution if context is available
        if (context != null) {
            val addAndEmitLog: suspend (String) -> Unit = { msg ->
                synchronized(logs) { logs.add(msg) }
                emit(
                    FscanProgress(
                        scannedIpsCount = 0,
                        totalIps = totalIps,
                        aliveHosts = emptyList(),
                        currentScanningIp = ipList.firstOrNull() ?: "",
                        logs = synchronized(logs) { logs.toList() }
                    )
                )
            }

            addAndEmitLog("🔍 [诊断日志] 正在检测本地 fscan 原生二进制文件...")
            val binaryFile = getFscanExecutable(context) { logMsg ->
                synchronized(logs) { logs.add(logMsg) }
            }
            emit(
                FscanProgress(
                    scannedIpsCount = 0,
                    totalIps = totalIps,
                    aliveHosts = emptyList(),
                    currentScanningIp = ipList.firstOrNull() ?: "",
                    logs = synchronized(logs) { logs.toList() }
                )
            )

            if (binaryFile != null && binaryFile.exists()) {
                addAndEmitLog("✅ [诊断日志] 找到可用 Go 原生 fscan 文件: ${binaryFile.absolutePath}")
                addAndEmitLog("   - 文件大小: ${binaryFile.length()} 字节 (${String.format("%.2f", binaryFile.length() / 1024.0 / 1024.0)} MB)")
                addAndEmitLog("   - 权限状态: 可读=${binaryFile.canRead()}, 可写=${binaryFile.canWrite()}, 可执行=${binaryFile.canExecute()}")

                val success = runFscanBinaryProcess(
                    context = context,
                    binaryFile = binaryFile,
                    targetInput = targetInput,
                    portRangeInput = portRangeInput,
                    disableBrute = disableBrute,
                    disablePing = disablePing,
                    enableWebScan = enableWebScan,
                    portScanThreads = portScanThreads,
                    timeoutSec = timeoutSec,
                    timeoutMs = timeoutMs,
                    concurrency = concurrency,
                    logs = logs,
                    emitProgress = { progress -> emit(progress) }
                )
                if (success) return@flow
                else {
                    addAndEmitLog("⚠️ [诊断日志] 原生 fscan 进程未成功完成或异常退出，切换降级引擎...")
                }
            } else {
                addAndEmitLog("ℹ️ [诊断日志] 本地未找到符合条件的原生 fscan 可执行文件 (>300KB)。")
                addAndEmitLog("ℹ️ [降级引擎] 切换为内置的高并发 Kotlin 纯 Socket 扫描引擎...")
            }
        } else {
            synchronized(logs) {
                logs.add("ℹ️ [降级引擎] 上下文为空，切换为内置高并发 Kotlin 扫描引擎...")
            }
        }

        logs.add("[+] Phase 1: Start host alive discovery...")

        // ==========================================
        // PHASE 1: Host Discovery (存活探测 - LiveTop)
        // ==========================================
        val aliveIpsMap = mutableMapOf<String, Long>() // ip -> latency
        var hostScannedCount = 0

        if (disablePing) {
            for (ip in ipList) {
                aliveIpsMap[ip] = 0L
            }
            synchronized(logs) {
                logs.add("[*] Ping disabled, treating all $totalIps host(s) as target candidates.")
            }
            emit(
                FscanProgress(
                    scannedIpsCount = totalIps,
                    totalIps = totalIps,
                    aliveHosts = emptyList(),
                    currentScanningIp = ipList.first(),
                    logs = synchronized(logs) { logs.toList() }
                )
            )
        } else {
            val hostChunks = ipList.chunked(concurrency)
            for (chunk in hostChunks) {
                coroutineScope {
                    val jobs = chunk.map { ip ->
                        async(Dispatchers.IO) {
                            val startTime = System.currentTimeMillis()
                            var isAlive = false
                            try {
                                val addr = InetAddress.getByName(ip)
                                isAlive = addr.isReachable(timeoutMs)
                            } catch (_: Exception) {}

                            // Fallback fast TCP probe on port 80 or 445 if ICMP fails
                            if (!isAlive) {
                                for (checkPort in listOf(80, 445, 22)) {
                                    try {
                                        val s = Socket()
                                        s.connect(InetSocketAddress(ip, checkPort), 300)
                                        s.close()
                                        isAlive = true
                                        break
                                    } catch (_: Exception) {}
                                }
                            }

                            val latency = System.currentTimeMillis() - startTime
                            if (isAlive) Pair(ip, latency) else null
                        }
                    }

                    val results = jobs.awaitAll().filterNotNull()
                    for ((ip, latency) in results) {
                        aliveIpsMap[ip] = latency
                        synchronized(logs) {
                            logs.add("[*] LiveTop: $ip is alive")
                        }
                    }

                    hostScannedCount += chunk.size
                    emit(
                        FscanProgress(
                            scannedIpsCount = hostScannedCount,
                            totalIps = totalIps,
                            aliveHosts = emptyList(),
                            currentScanningIp = chunk.lastOrNull() ?: "",
                            logs = synchronized(logs) { logs.toList() }
                        )
                    )
                }
            }
        }

        val aliveIpsList = aliveIpsMap.keys.toList()
        synchronized(logs) {
            logs.add("[+] Phase 1 Complete. Found ${aliveIpsList.size} alive host(s) out of $totalIps target(s).")
            if (aliveIpsList.isNotEmpty()) {
                logs.add("[+] Phase 2: Start port scanning on alive hosts...")
            }
        }

        emit(
            FscanProgress(
                scannedIpsCount = totalIps,
                totalIps = totalIps,
                aliveHosts = emptyList(),
                currentScanningIp = aliveIpsList.firstOrNull() ?: "",
                logs = synchronized(logs) { logs.toList() }
            )
        )

        if (aliveIpsList.isEmpty()) {
            synchronized(logs) {
                logs.add("[+] Scan completed. No alive hosts found.")
            }
            emit(
                FscanProgress(
                    scannedIpsCount = totalIps,
                    totalIps = totalIps,
                    aliveHosts = emptyList(),
                    currentScanningIp = "",
                    logs = synchronized(logs) { logs.toList() }
                )
            )
            return@flow
        }

        // ==========================================
        // PHASE 2 & 3: Port Scan & Service/Web Title Protocol Detection
        // ==========================================
        val hostResults = mutableListOf<FscanHostResult>()

        for (ip in aliveIpsList) {
            val openPorts = mutableListOf<Int>()
            val portItems = mutableListOf<FscanPortItem>()
            val banners = mutableMapOf<Int, String>()

            val portBatchSize = when {
                portsToProbe.size > 10000 -> 300
                portsToProbe.size > 1000 -> 150
                else -> 50
            }
            val portTimeout = when {
                portsToProbe.size > 10000 -> 150
                portsToProbe.size > 1000 -> 250
                else -> timeoutMs
            }

            var scannedPortCount = 0
            val totalPortsToProbe = portsToProbe.size

            val portChunks = portsToProbe.chunked(portBatchSize)
            for (pChunk in portChunks) {
                coroutineScope {
                    val portJobs = pChunk.map { port ->
                        async(Dispatchers.IO) {
                            try {
                                val socket = Socket()
                                socket.connect(InetSocketAddress(ip, port), portTimeout)

                                var bannerOrTitle = ""
                                val isWeb = port in listOf(80, 443, 8000, 8080, 8081, 8443, 8888, 9000, 3000, 5000)
                                val serviceName = when (port) {
                                    21 -> "FTP"
                                    22 -> "SSH"
                                    23 -> "Telnet"
                                    25 -> "SMTP"
                                    53 -> "DNS"
                                    80 -> "HTTP"
                                    110 -> "POP3"
                                    143 -> "IMAP"
                                    443 -> "HTTPS"
                                    445 -> "SMB"
                                    1433 -> "MSSQL"
                                    1521 -> "Oracle"
                                    3306 -> "MySQL"
                                    3389 -> "RDP"
                                    5432 -> "PostgreSQL"
                                    6379 -> "Redis"
                                    8080 -> "HTTP-Alt"
                                    27017 -> "MongoDB"
                                    else -> "Custom"
                                }

                                synchronized(logs) {
                                    logs.add("[+] Port scan: $ip:$port open ($serviceName)")
                                }

                                // Phase 3: Protocol / Web Title Fingerprinting
                                if (enableWebScan && isWeb) {
                                    try {
                                        socket.soTimeout = 300
                                        val out = socket.getOutputStream()
                                        out.write("GET / HTTP/1.1\r\nHost: $ip\r\nUser-Agent: Fscan/1.8.4\r\nConnection: close\r\n\r\n".toByteArray())
                                        out.flush()

                                        val r = socket.getInputStream().bufferedReader()
                                        var line: String?
                                        var lineCount = 0
                                        while (r.readLine().also { line = it } != null && lineCount < 20) {
                                            lineCount++
                                            val l = line ?: continue
                                            if (l.contains("<title>", ignoreCase = true)) {
                                                bannerOrTitle = l.substringAfter("<title>", "").substringBefore("</title>", "").trim()
                                                break
                                            } else if (l.startsWith("Server:", ignoreCase = true) && bannerOrTitle.isBlank()) {
                                                bannerOrTitle = l.substringAfter(":").trim()
                                            }
                                        }
                                    } catch (_: Exception) {}

                                    if (bannerOrTitle.isNotBlank()) {
                                        synchronized(logs) {
                                            logs.add("[+] Web Title: http://$ip:$port [$bannerOrTitle]")
                                        }
                                    }
                                }

                                if (bannerOrTitle.isBlank()) {
                                    bannerOrTitle = serviceName
                                }

                                socket.close()
                                Triple(port, serviceName, bannerOrTitle)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }

                    val resList = portJobs.awaitAll().filterNotNull()
                    for ((port, serviceName, bannerOrTitle) in resList) {
                        openPorts.add(port)
                        banners[port] = bannerOrTitle
                        portItems.add(
                            FscanPortItem(
                                ip = ip,
                                port = port,
                                serviceName = serviceName,
                                titleOrBanner = bannerOrTitle,
                                isWeb = port in listOf(80, 443, 8000, 8080, 8081, 8443, 8888, 9000, 3000, 5000)
                            )
                        )
                    }
                }

                scannedPortCount += pChunk.size
                if (totalPortsToProbe > 200) {
                    val portPct = (scannedPortCount * 100) / totalPortsToProbe
                    emit(
                        FscanProgress(
                            scannedIpsCount = hostResults.size,
                            totalIps = totalIps,
                            aliveHosts = hostResults.toList(),
                            currentScanningIp = "$ip [端口 $scannedPortCount/$totalPortsToProbe ($portPct%)]",
                            logs = synchronized(logs) { logs.toList() }
                        )
                    )
                }
            }

            var hostName = ""
            try {
                hostName = HostNameResolver.resolveHostName(ip, 300)
            } catch (_: Exception) {}

            val osHint = when {
                openPorts.contains(445) || openPorts.contains(3389) || openPorts.contains(139) -> "Windows OS"
                openPorts.contains(22) -> "Linux / Unix OS"
                openPorts.contains(80) || openPorts.contains(443) || openPorts.contains(8080) -> "Web Server / Router"
                else -> "Network Device"
            }

            hostResults.add(
                FscanHostResult(
                    ip = ip,
                    hostname = hostName,
                    isAlive = true,
                    openPorts = openPorts,
                    portItems = portItems,
                    banners = banners,
                    osHint = osHint,
                    latencyMs = aliveIpsMap[ip] ?: 0L
                )
            )

            emit(
                FscanProgress(
                    scannedIpsCount = hostResults.size,
                    totalIps = totalIps,
                    aliveHosts = hostResults.toList(),
                    currentScanningIp = ip,
                    logs = synchronized(logs) { logs.toList() }
                )
            )
        }

        val totalOpenPorts = hostResults.sumOf { it.openPorts.size }
        synchronized(logs) {
            logs.add("[+] Scan completed. Total: ${hostResults.size} alive host(s), $totalOpenPorts open port(s)")
        }

        emit(
            FscanProgress(
                scannedIpsCount = totalIps,
                totalIps = totalIps,
                aliveHosts = hostResults.toList(),
                currentScanningIp = aliveIpsList.lastOrNull() ?: "",
                logs = synchronized(logs) { logs.toList() }
            )
        )
    }.flowOn(Dispatchers.IO)

    private suspend fun runFscanBinaryProcess(
        context: Context,
        binaryFile: File,
        targetInput: String,
        portRangeInput: String,
        disableBrute: Boolean,
        disablePing: Boolean,
        enableWebScan: Boolean,
        portScanThreads: Int,
        timeoutSec: Int,
        timeoutMs: Int,
        concurrency: Int,
        logs: MutableList<String>,
        emitProgress: suspend (FscanProgress) -> Unit
    ): Boolean {
        val aliveMap = mutableMapOf<String, FscanHostResult>()
        val cleanTarget = targetInput.trim()
        val cleanPortRange = portRangeInput.replace(" ", "")
        val validTimeoutSec = timeoutSec.coerceAtLeast(1)
        val validThreads = portScanThreads.coerceAtLeast(1)
        var currentScanningTarget = cleanTarget

        suspend fun addLogAndEmit(msg: String) {
            synchronized(logs) { logs.add(msg) }
            try {
                emitProgress(
                    FscanProgress(
                        scannedIpsCount = aliveMap.size,
                        totalIps = aliveMap.size.coerceAtLeast(1),
                        aliveHosts = aliveMap.values.toList(),
                        currentScanningIp = currentScanningTarget,
                        logs = synchronized(logs) { logs.toList() }
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("FscanEngine", "emitProgress error: ${e.message}", e)
            }
        }

        try {
            addLogAndEmit("🚀 [进程初始化] 准备运行本地 Go 原生 fscan 二进制...")
            val execDir = File(context.filesDir, "native_bin").apply { if (!exists()) mkdirs() }
            val execFile = File(execDir, "fscan")
            if (binaryFile.canonicalPath != execFile.canonicalPath) {
                try {
                    binaryFile.copyTo(execFile, overwrite = true)
                } catch (e: Exception) {
                    addLogAndEmit("⚠️ 复制二进制文件到 native_bin 目录异常: ${e.message}")
                }
            }

            val runTarget = if (execFile.exists() && execFile.length() > 300_000) execFile else binaryFile
            ensureExecutablePermissions(runTarget)

            val isRooted = try { RootUtils.isRootAvailable() } catch (_: Exception) { false }
            val rootStatus = try { RootUtils.getRootStatusDescription() } catch (_: Exception) { "检测失败" }
            val suBinary = try { RootUtils.findSuBinaryPath() } catch (_: Exception) { "su" }

            addLogAndEmit("⚡ Root 状态检测: $rootStatus (提权程序: $suBinary, Root可用=$isRooted)")

            if (isRooted) {
                val chmodOk = RootUtils.executeSuCmd("chmod 777 ${runTarget.absolutePath}")
                addLogAndEmit("🔑 执行 Root chmod 777: $chmodOk (${runTarget.absolutePath})")
            } else {
                ensureExecutablePermissions(runTarget)
            }

            // ========================================================
            // 构造正式 fscan 参数与命令行
            // ========================================================
            val argsList = mutableListOf<String>(
                "-h", cleanTarget,
                "-p", cleanPortRange,
                "-t", validThreads.toString(),
                "-num", concurrency.toString(),
                "-time", validTimeoutSec.toString(),
                "-no",    // 禁用保存 result.txt 文件，解决 read-only file system 报错
                "-gt", "0" // 禁用全局超时限制
            )
            if (disableBrute) argsList.add("-nobr")
            if (disablePing) argsList.add("-np")
            if (!enableWebScan) argsList.add("-nw")

            val argsStr = argsList.joinToString(" ")
            // 切换工作目录到应用可写的 execDir 目录，避免在系统根目录 / 下创建临时文件
            val fullCmdStr = "cd ${execDir.absolutePath} && ${runTarget.absolutePath} $argsStr"

            val pb: ProcessBuilder
            if (isRooted) {
                addLogAndEmit("🚀 [启动扫描 (Root)] $suBinary -c \"$fullCmdStr\"")
                pb = ProcessBuilder(suBinary, "-c", fullCmdStr)
            } else {
                val cmd = mutableListOf<String>(runTarget.absolutePath).apply { addAll(argsList) }
                addLogAndEmit("🚀 [启动扫描 (普通)] ${cmd.joinToString(" ")}")
                pb = ProcessBuilder(cmd)
            }
            pb.directory(execDir)

            pb.redirectErrorStream(true)
            val proc = try {
                pb.start()
            } catch (startEx: Exception) {
                addLogAndEmit("⚠️ [进程启动错误] ${startEx.message}")
                if (isRooted) {
                    addLogAndEmit("💡 尝试普通模式 (非 Root) 回退启动...")
                    val fallbackCmd = mutableListOf<String>(runTarget.absolutePath).apply { addAll(argsList) }
                    ProcessBuilder(fallbackCmd).apply {
                        directory(execDir)
                        redirectErrorStream(true)
                    }.start()
                } else {
                    throw startEx
                }
            }

            val reader = java.io.BufferedReader(java.io.InputStreamReader(proc.inputStream))
            var line: String?
            var lastEmitTime = 0L
            var readLineCount = 0

            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.isBlank()) continue
                readLineCount++

                synchronized(logs) {
                    logs.add(l)
                }

                parseFscanOutputLine(l, aliveMap)

                val extractedTarget = extractIpAndPortFromLine(l)
                if (extractedTarget != null) {
                    currentScanningTarget = extractedTarget.ip
                }

                val now = System.currentTimeMillis()
                if (now - lastEmitTime > 50L) {
                    lastEmitTime = now
                    emitProgress(
                        FscanProgress(
                            scannedIpsCount = aliveMap.size,
                            totalIps = aliveMap.size.coerceAtLeast(1),
                            aliveHosts = aliveMap.values.toList(),
                            currentScanningIp = currentScanningTarget,
                            logs = synchronized(logs) { logs.toList() }
                        )
                    )
                }
            }

            proc.waitFor()
            val exitCode = proc.exitValue()
            addLogAndEmit("[+] fscan 进程运行结束, exitCode = $exitCode, 共读取到 $readLineCount 行输出")

            if (aliveMap.isEmpty() && !disablePing) {
                addLogAndEmit("ℹ️ [诊断分析] fscan 执行完毕但未发现存活主机 (Ping 探测失败或目标拒绝 ICMP 包)。")
                addLogAndEmit("💡 [操作建议] 请在 Fscan 界面选项中勾选【禁用 Ping 探测 (-np)】后再次尝试扫描！")
            }

            emitProgress(
                FscanProgress(
                    scannedIpsCount = aliveMap.size,
                    totalIps = aliveMap.size.coerceAtLeast(1),
                    aliveHosts = aliveMap.values.toList(),
                    currentScanningIp = "扫描完成",
                    logs = synchronized(logs) { logs.toList() }
                )
            )
            return true
        } catch (t: Throwable) {
            addLogAndEmit("❌ [致命异常捕获] fscan 二进制进程调用中断: ${t.javaClass.simpleName} - ${t.message}")
            return false
        }
    }

    private data class ExtractedTarget(val ip: String, val port: Int?, val serviceHint: String)

    private fun extractIpAndPortFromLine(line: String): ExtractedTarget? {
        val l = line.trim()

        // 1. IPv6 bracket notation: e.g. [2408:8000:b001:8101:90:213:0:1]:3082 or [2408:...]:53 domain
        val ipv6BracketRegex = Regex("\\[([0-9a-fA-F:]+)\\](?::([0-9]{1,5}))?(?:\\s+([a-zA-Z0-9_-]+))?")
        val bracketMatch = ipv6BracketRegex.find(l)
        if (bracketMatch != null) {
            val ipCandidate = bracketMatch.groupValues[1]
            if (isValidIpv6(ipCandidate)) {
                val port = bracketMatch.groupValues[2].toIntOrNull()?.takeIf { it in 1..65535 }
                val service = bracketMatch.groupValues[3].ifBlank { "unknown" }
                return ExtractedTarget(ipCandidate, port, service)
            }
        }

        // 2. IPv4 notation: e.g. 192.168.1.1:8080 or http://192.168.1.1:8080 or 192.168.1.1
        val ipv4Regex = Regex("(?:https?://|ftp://|ssh://|mysql://|smb://|redis://)?([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})(?::([0-9]{1,5}))?(?:\\s+([a-zA-Z0-9_-]+))?")
        val v4Match = ipv4Regex.find(l)
        if (v4Match != null) {
            val ipCandidate = v4Match.groupValues[1]
            val octets = ipCandidate.split(".").mapNotNull { it.toIntOrNull() }
            if (octets.size == 4 && octets.all { it in 0..255 }) {
                val port = v4Match.groupValues[2].toIntOrNull()?.takeIf { it in 1..65535 }
                val service = v4Match.groupValues[3].ifBlank { "unknown" }
                return ExtractedTarget(ipCandidate, port, service)
            }
        }

        // 3. Raw IPv6 notation without brackets: e.g. 2408:8000:b001:8101:90:213:0:1:10010 or 2408:8000:b001:8101:90:213:0:1
        val rawIpv6Regex = Regex("(?:https?://)?([0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{0,4}){2,7})(?::([0-9]{1,5}))?")
        val rawV6Match = rawIpv6Regex.find(l)
        if (rawV6Match != null) {
            val ipCandidate = rawV6Match.groupValues[1]
            if (isValidIpv6(ipCandidate)) {
                val port = rawV6Match.groupValues[2].toIntOrNull()?.takeIf { it in 1..65535 }
                return ExtractedTarget(ipCandidate, port, "unknown")
            }
        }

        return null
    }

    private fun isValidIpv6(ip: String): Boolean {
        if (!ip.contains(":")) return false
        val parts = ip.split(":")
        if (parts.size < 3 || parts.size > 8) return false
        return parts.all { it.isEmpty() || (it.length <= 4 && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' }) }
    }

    private fun parseFscanOutputLine(
        line: String,
        aliveMap: MutableMap<String, FscanHostResult>
    ) {
        val l = line.trim()
        if (l.isBlank()) return

        // 1. Skip error / failure logs (fscan uses [-] prefix for error/failure logs)
        if (l.startsWith("[-]") || l.contains("插件扫描错误") || l.contains("初始化失败") ||
            l.contains("failed") || l.contains("EOF") || l.contains("connection refused") ||
            l.contains("i/o timeout") || l.contains("context deadline exceeded")) {
            return
        }

        // 2. Filter out headers, ASCII banners, stats or logs generated by wrapper
        if (l.contains("服务插件") || l.contains("参数自适应") || l.contains("POC加载完成") ||
            l.contains("扫描完成") || l.contains("存活主机数") || l.contains("扫描任务完成") ||
            l.contains("进程运行结束") || l.contains("诊断分析") || l.contains("操作建议") ||
            l.contains("Usage of") || l.contains("flag needs an argument") || l.contains("┌─") ||
            l.contains("│") || l.contains("└─") || l.contains("Fscan ") || l.contains("二进制验证") ||
            l.contains("进程初始化") || l.contains("Root 状态检测") || l.contains("执行 Root") ||
            l.contains("启动扫描")) {
            return
        }

        // 3. Only process positive result lines starting with [+] or [*], or explicit host alive indicators
        val isPositiveResult = l.startsWith("[+]") || l.startsWith("[*]") ||
                l.contains("is alive") || l.contains("存活") || l.contains("NetInfo") || l.contains("LiveTop")
        if (!isPositiveResult) return

        // 4. Extract IP address & Port from line (Supporting both IPv4 and IPv6)
        val extracted = extractIpAndPortFromLine(l) ?: return
        val (ip, extractedPort, serviceHint) = extracted

        // Fetch or create host entry
        val existingHost = aliveMap[ip] ?: FscanHostResult(
            ip = ip,
            isAlive = true,
            osHint = if (ip.contains(":")) "IPv6 设备/网络主机" else "网络设备/Linux/Windows"
        )

        // Check if line indicates plain host alive
        if (l.contains("存活") || l.contains("is alive") || l.contains("NetInfo") || l.contains("LiveTop")) {
            aliveMap[ip] = existingHost.copy(isAlive = true)
            return
        }

        var port = extractedPort
        var serviceName = serviceHint
        var isWeb = false

        val lower = l.lowercase()
        when {
            lower.contains("https://") || lower.contains(" https ") -> {
                if (serviceName == "unknown") serviceName = "https"
                isWeb = true
                if (port == null) port = 443
            }
            lower.contains("http://") || lower.contains(" http ") || lower.contains("webtitle") -> {
                if (serviceName == "unknown") serviceName = "http"
                isWeb = true
                if (port == null) port = 80
            }
            lower.contains("ssh") -> {
                if (serviceName == "unknown") serviceName = "ssh"
                if (port == null) port = 22
            }
            lower.contains("ftp") -> {
                if (serviceName == "unknown") serviceName = "ftp"
                if (port == null) port = 21
            }
            lower.contains("mysql") -> {
                if (serviceName == "unknown") serviceName = "mysql"
                if (port == null) port = 3306
            }
            lower.contains("mssql") -> {
                if (serviceName == "unknown") serviceName = "mssql"
                if (port == null) port = 1433
            }
            lower.contains("redis") -> {
                if (serviceName == "unknown") serviceName = "redis"
                if (port == null) port = 6379
            }
            lower.contains("smb") -> {
                if (serviceName == "unknown") serviceName = "smb"
                if (port == null) port = 445
            }
            lower.contains("rdp") -> {
                if (serviceName == "unknown") serviceName = "rdp"
                if (port == null) port = 3389
            }
        }

        if (port != null && port in 1..65535) {
            val updatedPorts = (existingHost.openPorts + port).distinct()

            // Clean up banner or title text
            var info = l.replace("[+]", "").replace("[*]", "").trim()
            if (info.startsWith("http://")) info = info.removePrefix("http://")
            if (info.startsWith("https://")) info = info.removePrefix("https://")
            if (info.startsWith("[$ip]")) info = info.removePrefix("[$ip]").trim()
            if (info.startsWith(ip)) info = info.removePrefix(ip).trim()
            if (info.startsWith(":$port")) info = info.removePrefix(":$port").trim()

            if (port == 80 || port == 443 || port == 8080 || port == 8443 || port == 8000 || port == 9000 ||
                lower.contains("title:") || lower.contains("code:") || lower.contains("server:")) {
                isWeb = true
            }

            val portItem = FscanPortItem(
                ip = ip,
                port = port,
                serviceName = if (serviceName != "unknown") serviceName else if (isWeb) "web" else "service",
                titleOrBanner = info.ifBlank { "Port $port open" },
                isWeb = isWeb
            )

            val updatedPortItems = (existingHost.portItems.filterNot { it.port == port } + portItem)
                .sortedBy { it.port }

            aliveMap[ip] = existingHost.copy(
                isAlive = true,
                openPorts = updatedPorts,
                portItems = updatedPortItems
            )
        } else {
            // Any other line with a valid IP from [+] or [*] means host is alive
            aliveMap[ip] = existingHost.copy(isAlive = true)
        }
    }
}


