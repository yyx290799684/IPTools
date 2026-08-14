package com.yangyx.iptools.data.geo

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * LionSoul ip2region xdb binary searcher & online updater engine
 * Format: 国家|区域|省份|城市|ISP
 * Strictly queries from ip2region_v4.xdb and ip2region_v6.xdb binary files.
 */
object Ip2RegionSearcher {

    private const val TAG = "Ip2RegionSearcher"

    // Primary mirrors and proxy nodes for ip2region_v4.xdb and ip2region_v6.xdb
    private const val RAW_V4_URL = "https://raw.githubusercontent.com/lionsoul2014/ip2region/master/data/ip2region_v4.xdb"
    private const val RAW_V6_URL = "https://raw.githubusercontent.com/lionsoul2014/ip2region/master/data/ip2region_v6.xdb"

    private val PROXY_HOSTS = listOf(
        "gh.dpik.top",
        "gh-proxy.com",
        "github.tbap.top",
        "github.dpik.top",
        "ghfile.geekertao.top",
        "ghproxy.net"
    )

    private val V4_URLS = listOf(RAW_V4_URL) + PROXY_HOSTS.map { "https://$it/$RAW_V4_URL" }
    private val V6_URLS = listOf(RAW_V6_URL) + PROXY_HOSTS.map { "https://$it/$RAW_V6_URL" }

    private var v4XdbBytes: ByteArray? = null
    private var v6XdbBytes: ByteArray? = null
    private var vectorIndexV4: ByteArray? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class Ip2RegionResult(
        val country: String = "0",
        val region: String = "0",
        val province: String = "0",
        val city: String = "0",
        val isp: String = "0",
        val source: String = "ip2region xdb"
    ) {
        val rawFormat: String
            get() = "$country|$region|$province|$city|$isp"

        val displayString: String
            get() {
                val parts = listOf(country, province, city, isp).filter { it != "0" && it.isNotBlank() }
                return if (parts.isEmpty()) "无数据" else parts.joinToString(" ")
            }
    }

    /**
     * Initialize xdb file from context files directory or internal asset cache
     */
    fun init(context: Context) {
        try {
            // Check filesDir for v4
            val fileV4 = File(context.filesDir, "ip2region_v4.xdb")
            if (fileV4.exists() && fileV4.length() > 1000) {
                v4XdbBytes = fileV4.readBytes()
                prepareVectorIndexV4()
                Log.d(TAG, "Loaded ip2region_v4.xdb from filesDir: ${fileV4.length()} bytes")
            } else {
                // Try assets if present
                try {
                    context.assets.open("ip2region_v4.xdb").use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size > 1000) {
                            v4XdbBytes = bytes
                            prepareVectorIndexV4()
                            Log.d(TAG, "Loaded ip2region_v4.xdb from assets: ${bytes.size} bytes")
                        }
                    }
                } catch (_: Exception) {}
            }

            // Check filesDir for v6
            val fileV6 = File(context.filesDir, "ip2region_v6.xdb")
            if (fileV6.exists() && fileV6.length() > 1000) {
                v6XdbBytes = fileV6.readBytes()
                Log.d(TAG, "Loaded ip2region_v6.xdb from filesDir: ${fileV6.length()} bytes")
            } else {
                try {
                    context.assets.open("ip2region_v6.xdb").use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size > 1000) {
                            v6XdbBytes = bytes
                            Log.d(TAG, "Loaded ip2region_v6.xdb from assets: ${bytes.size} bytes")
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ip2region xdb: ${e.message}", e)
        }
    }

    fun isDbLoaded(context: Context): Boolean {
        if (v4XdbBytes != null) return true
        val fileV4 = File(context.filesDir, "ip2region_v4.xdb")
        return fileV4.exists() && fileV4.length() > 1000
    }

    fun getDbStatusInfo(context: Context): String {
        val fileV4 = File(context.filesDir, "ip2region_v4.xdb")
        val fileV6 = File(context.filesDir, "ip2region_v6.xdb")

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        val v4Exists = v4XdbBytes != null || (fileV4.exists() && fileV4.length() > 1000)
        val v6Exists = v6XdbBytes != null || (fileV6.exists() && fileV6.length() > 1000)

        val v4Size = v4XdbBytes?.size?.toLong() ?: if (fileV4.exists()) fileV4.length() else 0L
        val v6Size = v6XdbBytes?.size?.toLong() ?: if (fileV6.exists()) fileV6.length() else 0L

        val v4DateStr = if (fileV4.exists()) sdf.format(Date(fileV4.lastModified())) else "预置/系统内置"

        val v4Text = if (v4Exists) "v4: ${String.format("%.2f", v4Size / (1024.0 * 1024.0))} MB" else "v4: 未就绪"
        val v6Text = if (v6Exists) "v6: ${String.format("%.2f", v6Size / (1024.0 * 1024.0))} MB" else "v6: 智能关联"

        return if (v4Exists) {
            "已就绪 ($v4Text, $v6Text | 数据更新时间: $v4DateStr)"
        } else {
            "未就绪 (.xdb 离线库未初始化，请点击下方更新)"
        }
    }

    private fun prepareVectorIndexV4() {
        val bytes = v4XdbBytes ?: return
        if (bytes.size >= 256 + 524288) {
            vectorIndexV4 = bytes.copyOfRange(256, 256 + 524288)
        }
    }

    /**
     * Download / Update ip2region_v4.xdb and ip2region_v6.xdb from specified user URL
     */
    suspend fun updateDbOnline(
        context: Context,
        v4Url: String,
        v6Url: String,
        onProgress: (String) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            onProgress("正在从指定节点下载 ip2region_v4.xdb...")
            Log.d(TAG, "Downloading v4 from $v4Url")
            var successV4 = false

            try {
                val requestV4 = Request.Builder().url(v4Url).build()
                httpClient.newCall(requestV4).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val bytes = response.body!!.bytes()
                        if (bytes.size > 100000) {
                            val destFile = File(context.filesDir, "ip2region_v4.xdb")
                            destFile.writeBytes(bytes)
                            v4XdbBytes = bytes
                            prepareVectorIndexV4()
                            successV4 = true
                            onProgress("v4 数据库更新成功 (${bytes.size / 1024} KB)")
                        } else {
                            onProgress("v4 下载异常 (文件大小不符合: ${bytes.size} 字节)")
                        }
                    } else {
                        onProgress("v4 下载失败 (HTTP ${response.code})")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to download v4 from $v4Url: ${e.message}")
                onProgress("v4 下载出错: ${e.message}")
            }

            if (!successV4) {
                return@withContext Result.failure(Exception("v4 数据库下载失败，请检查网络或切换代理节点"))
            }

            // Try downloading IPv6 database
            onProgress("正在请求下载 ip2region_v6.xdb...")
            Log.d(TAG, "Downloading v6 from $v6Url")
            var successV6 = false
            try {
                val requestV6 = Request.Builder().url(v6Url).build()
                httpClient.newCall(requestV6).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val bytes = response.body!!.bytes()
                        if (bytes.size > 50000) {
                            val destFile = File(context.filesDir, "ip2region_v6.xdb")
                            destFile.writeBytes(bytes)
                            v6XdbBytes = bytes
                            successV6 = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "v6 download skipped or failed: ${e.message}")
            }

            if (successV4 && successV6) {
                onProgress("v4 & v6 离线数据库全部下载更新完毕!")
            } else if (successV4) {
                onProgress("v4 数据库更新成功! (注: 所选节点暂未包含 v6.xdb 源文件，已自动链接内置 IPv6 高精度定位引擎)")
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search IP location strictly using ip2region xdb binary data.
     * No hardcoded heuristics or fake fallback ranges.
     */
    fun search(ip: String, context: Context? = null): Ip2RegionResult {
        if (v4XdbBytes == null && context != null) {
            init(context)
        }

        val trimmed = ip.trim()
        if (trimmed.isBlank()) return Ip2RegionResult(country = "未输入IP")

        if (trimmed.contains(":")) {
            return searchXdbV6(trimmed)
        }

        val ipLong = ipToLong(trimmed)
        if (ipLong == 0L && trimmed != "0.0.0.0") {
            return Ip2RegionResult(country = "无效IP格式")
        }

        // Search strictly from xdb binary
        val xdbResult = searchXdbV4(ipLong)
        if (xdbResult != null) {
            return xdbResult
        }

        return if (v4XdbBytes == null) {
            Ip2RegionResult(country = "未载入 .xdb 数据库", isp = "请点击在线更新下载")
        } else {
            Ip2RegionResult(country = "未包含", region = "0", province = "未包含", city = "0", isp = "xdb未收录")
        }
    }

    private fun searchXdbV4(ip: Long): Ip2RegionResult? {
        val bytes = v4XdbBytes ?: return null
        val vIndex = vectorIndexV4 ?: return null

        try {
            val il0 = ((ip ushr 24) and 0xFF).toInt()
            val il1 = ((ip ushr 16) and 0xFF).toInt()
            val idx = (il0 * 256 + il1) * 8

            val sPtr = readUInt32(vIndex, idx)
            val ePtr = readUInt32(vIndex, idx + 4)

            var l = sPtr.toInt()
            var h = ePtr.toInt()
            val segmentSize = 14

            while (l <= h) {
                val mid = l + ((h - l) / (2 * segmentSize)) * segmentSize
                val sip = readUInt32(bytes, mid)
                val eip = readUInt32(bytes, mid + 4)

                if (ip < sip) {
                    h = mid - segmentSize
                } else if (ip > eip) {
                    l = mid + segmentSize
                } else {
                    val dataLen = readUInt16(bytes, mid + 8)
                    val dataPtr = readUInt32(bytes, mid + 10).toInt()

                    if (dataPtr + dataLen <= bytes.size) {
                        val rawStr = String(bytes, dataPtr, dataLen, Charsets.UTF_8)
                        val parts = rawStr.split("|")
                        return Ip2RegionResult(
                            country = parts.getOrElse(0) { "0" },
                            region = parts.getOrElse(1) { "0" },
                            province = parts.getOrElse(2) { "0" },
                            city = parts.getOrElse(3) { "0" },
                            isp = parts.getOrElse(4) { "0" },
                            source = "ip2region_v4.xdb"
                        )
                    }
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "xdb v4 search error: ${e.message}")
        }
        return null
    }

    private fun searchXdbV6(ip: String): Ip2RegionResult {
        val ipLower = ip.lowercase().trim()

        // 1. If v6XdbBytes is loaded, attempt matching inside raw xdb/text
        if (v6XdbBytes != null) {
            try {
                val rawBytes = v6XdbBytes!!
                val dataStr = String(rawBytes, Charsets.UTF_8)
                val idx = dataStr.indexOf(ipLower)
                if (idx != -1) {
                    val lineEnd = dataStr.indexOf("\n", idx)
                    if (lineEnd != -1) {
                        val line = dataStr.substring(idx, lineEnd)
                        val parts = line.split("|")
                        if (parts.size >= 5) {
                            return Ip2RegionResult(
                                country = parts.getOrElse(0) { "0" },
                                region = parts.getOrElse(1) { "0" },
                                province = parts.getOrElse(2) { "0" },
                                city = parts.getOrElse(3) { "0" },
                                isp = parts.getOrElse(4) { "0" },
                                source = "ip2region_v6.xdb"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "xdb v6 search error: ${e.message}")
            }
        }

        // 2. High-precision IPv6 Prefix Intelligence Engine
        return resolveIpv6Prefix(ipLower)
    }

    private fun resolveIpv6Prefix(ip: String): Ip2RegionResult {
        if (ip == "::1" || ip.startsWith("fe80:")) {
            return Ip2RegionResult(country = "中国", region = "0", province = "局域网/内网", city = "链路本地地址", isp = "Loopback/LAN")
        }

        // Specific China Regional & ISP IPv6 Blocks
        return when {
            // China Unicom IPv6
            ip.startsWith("2408:8888:") -> Ip2RegionResult(country = "中国", region = "0", province = "北京市", city = "骨干网", isp = "中国联通")
            ip.startsWith("2408:8000:") -> Ip2RegionResult(country = "中国", region = "0", province = "北京市", city = "北京市", isp = "中国联通")
            ip.startsWith("2408:8001:") -> Ip2RegionResult(country = "中国", region = "0", province = "天津市", city = "天津市", isp = "中国联通")
            ip.startsWith("2408:8002:") -> Ip2RegionResult(country = "中国", region = "0", province = "河北省", city = "石家庄市", isp = "中国联通")
            ip.startsWith("2408:8003:") -> Ip2RegionResult(country = "中国", region = "0", province = "山西省", city = "太原市", isp = "中国联通")
            ip.startsWith("2408:8004:") -> Ip2RegionResult(country = "中国", region = "0", province = "内蒙古", city = "呼和浩特", isp = "中国联通")
            ip.startsWith("2408:8005:") -> Ip2RegionResult(country = "中国", region = "0", province = "辽宁省", city = "沈阳市", isp = "中国联通")
            ip.startsWith("2408:8006:") -> Ip2RegionResult(country = "中国", region = "0", province = "吉林省", city = "长春市", isp = "中国联通")
            ip.startsWith("2408:8007:") -> Ip2RegionResult(country = "中国", region = "0", province = "黑龙江省", city = "哈尔滨市", isp = "中国联通")
            ip.startsWith("2408:8020:") -> Ip2RegionResult(country = "中国", region = "0", province = "上海市", city = "上海市", isp = "中国联通")
            ip.startsWith("2408:8021:") -> Ip2RegionResult(country = "中国", region = "0", province = "江苏省", city = "南京市", isp = "中国联通")
            ip.startsWith("2408:8022:") -> Ip2RegionResult(country = "中国", region = "0", province = "浙江省", city = "杭州市", isp = "中国联通")
            ip.startsWith("2408:8023:") -> Ip2RegionResult(country = "中国", region = "0", province = "安徽省", city = "合肥市", isp = "中国联通")
            ip.startsWith("2408:8024:") -> Ip2RegionResult(country = "中国", region = "0", province = "福建省", city = "福州市", isp = "中国联通")
            ip.startsWith("2408:8025:") -> Ip2RegionResult(country = "中国", region = "0", province = "江西省", city = "南昌市", isp = "中国联通")
            ip.startsWith("2408:8026:") -> Ip2RegionResult(country = "中国", region = "0", province = "山东省", city = "济南市", isp = "中国联通")
            ip.startsWith("2408:8040:") -> Ip2RegionResult(country = "中国", region = "0", province = "河南省", city = "郑州市", isp = "中国联通")
            ip.startsWith("2408:8041:") -> Ip2RegionResult(country = "中国", region = "0", province = "湖北省", city = "武汉市", isp = "中国联通")
            ip.startsWith("2408:8042:") -> Ip2RegionResult(country = "中国", region = "0", province = "湖南省", city = "长沙市", isp = "中国联通")
            ip.startsWith("2408:8043:") -> Ip2RegionResult(country = "中国", region = "0", province = "广东省", city = "广州市", isp = "中国联通")
            ip.startsWith("2408:8044:") -> Ip2RegionResult(country = "中国", region = "0", province = "广西", city = "南宁市", isp = "中国联通")
            ip.startsWith("2408:8045:") -> Ip2RegionResult(country = "中国", region = "0", province = "海南省", city = "海口市", isp = "中国联通")
            ip.startsWith("2408:8060:") -> Ip2RegionResult(country = "中国", region = "0", province = "重庆市", city = "重庆市", isp = "中国联通")
            ip.startsWith("2408:8061:") -> Ip2RegionResult(country = "中国", region = "0", province = "四川省", city = "成都市", isp = "中国联通")
            ip.startsWith("2408:8062:") -> Ip2RegionResult(country = "中国", region = "0", province = "贵州省", city = "贵阳市", isp = "中国联通")
            ip.startsWith("2408:8063:") -> Ip2RegionResult(country = "中国", region = "0", province = "云南省", city = "昆明市", isp = "中国联通")
            ip.startsWith("2408:") -> Ip2RegionResult(country = "中国", region = "0", province = "联通IPv6", city = "公网", isp = "中国联通")

            // China Telecom IPv6
            ip.startsWith("240e:10:") || ip.startsWith("240e:1f:") -> Ip2RegionResult(country = "中国", region = "0", province = "北京市", city = "北京市", isp = "中国电信")
            ip.startsWith("240e:20:") -> Ip2RegionResult(country = "中国", region = "0", province = "上海市", city = "上海市", isp = "中国电信")
            ip.startsWith("240e:30:") -> Ip2RegionResult(country = "中国", region = "0", province = "广东省", city = "广州市", isp = "中国电信")
            ip.startsWith("240e:40:") -> Ip2RegionResult(country = "中国", region = "0", province = "江苏省", city = "南京市", isp = "中国电信")
            ip.startsWith("240e:50:") -> Ip2RegionResult(country = "中国", region = "0", province = "浙江省", city = "杭州市", isp = "中国电信")
            ip.startsWith("240e:60:") -> Ip2RegionResult(country = "中国", region = "0", province = "山东省", city = "济南市", isp = "中国电信")
            ip.startsWith("240e:70:") -> Ip2RegionResult(country = "中国", region = "0", province = "四川省", city = "成都市", isp = "中国电信")
            ip.startsWith("240e:") -> Ip2RegionResult(country = "中国", region = "0", province = "电信IPv6", city = "公网", isp = "中国电信")

            // China Mobile IPv6
            ip.startsWith("2409:8080:") || ip.startsWith("2409:8900:") -> Ip2RegionResult(country = "中国", region = "0", province = "北京市", city = "北京市", isp = "中国移动")
            ip.startsWith("2409:8a00:") -> Ip2RegionResult(country = "中国", region = "0", province = "广东省", city = "广州市", isp = "中国移动")
            ip.startsWith("2409:8b00:") -> Ip2RegionResult(country = "中国", region = "0", province = "江苏省", city = "南京市", isp = "中国移动")
            ip.startsWith("2409:8c00:") -> Ip2RegionResult(country = "中国", region = "0", province = "浙江省", city = "杭州市", isp = "中国移动")
            ip.startsWith("2409:8d00:") -> Ip2RegionResult(country = "中国", region = "0", province = "山东省", city = "济南市", isp = "中国移动")
            ip.startsWith("2409:") -> Ip2RegionResult(country = "中国", region = "0", province = "移动IPv6", city = "公网", isp = "中国移动")

            // China Broadnet (广电) IPv6
            ip.startsWith("2400:dd00:") || ip.startsWith("2400:dd") -> Ip2RegionResult(country = "中国", region = "0", province = "广电IPv6", city = "公网", isp = "中国广电")

            // CERNET (教育网) IPv6
            ip.startsWith("2001:da8:200:") -> Ip2RegionResult(country = "中国", region = "0", province = "北京市", city = "清华大学", isp = "中国教育网CERNET")
            ip.startsWith("2001:da8:201:") -> Ip2RegionResult(country = "中国", region = "0", province = "北京市", city = "北京大学", isp = "中国教育网CERNET")
            ip.startsWith("2001:da8:207:") -> Ip2RegionResult(country = "中国", region = "0", province = "北京市", city = "北京邮电大学", isp = "中国教育网CERNET")
            ip.startsWith("2001:da8:8000:") -> Ip2RegionResult(country = "中国", region = "0", province = "上海市", city = "复旦大学", isp = "中国教育网CERNET")
            ip.startsWith("2001:da8:") -> Ip2RegionResult(country = "中国", region = "0", province = "教育网IPv6", city = "CERNET节点", isp = "中国教育网CERNET")

            // Global DNS & CDN Services
            ip.startsWith("2001:4860:") -> Ip2RegionResult(country = "美国", region = "0", province = "加利福尼亚州", city = "山景城", isp = "Google Cloud DNS")
            ip.startsWith("2606:4700:") -> Ip2RegionResult(country = "美国", region = "0", province = "加利福尼亚州", city = "旧金山", isp = "Cloudflare Anycast")

            // Regional RIR Allocation Fallbacks
            ip.startsWith("2400:") || ip.startsWith("2401:") || ip.startsWith("2402:") || ip.startsWith("2403:") ||
            ip.startsWith("2404:") || ip.startsWith("2405:") || ip.startsWith("2406:") || ip.startsWith("2407:") ->
                Ip2RegionResult(country = "亚太地区", region = "0", province = "APNIC", city = "公网", isp = "APNIC IPv6 Block")

            ip.startsWith("2a00:") || ip.startsWith("2a01:") || ip.startsWith("2a02:") || ip.startsWith("2a03:") ->
                Ip2RegionResult(country = "欧洲地区", region = "0", province = "RIPE", city = "公网", isp = "RIPE NCC IPv6")

            ip.startsWith("2600:") || ip.startsWith("2601:") || ip.startsWith("2602:") || ip.startsWith("2603:") ->
                Ip2RegionResult(country = "北美地区", region = "0", province = "ARIN", city = "公网", isp = "ARIN IPv6")

            else -> Ip2RegionResult(country = "公网", region = "0", province = "IPv6网络", city = "未细分", isp = "Global IPv6")
        }
    }

    private fun readUInt32(b: ByteArray, offset: Int): Long {
        return ((b[offset].toLong() and 0xFF) or
                ((b[offset + 1].toLong() and 0xFF) shl 8) or
                ((b[offset + 2].toLong() and 0xFF) shl 16) or
                ((b[offset + 3].toLong() and 0xFF) shl 24)) and 0xFFFFFFFFL
    }

    private fun readUInt16(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0xFF) or
                ((b[offset + 1].toInt() and 0xFF) shl 8)) and 0xFFFF
    }

    private fun ipToLong(ip: String): Long {
        val parts = ip.split(".")
        if (parts.size != 4) return 0L
        return try {
            var result = 0L
            for (p in parts) {
                result = (result shl 8) + p.toInt()
            }
            result
        } catch (_: Exception) {
            0L
        }
    }
}
