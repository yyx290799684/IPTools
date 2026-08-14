package com.yangyx.iptools.data.geo

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.TimeUnit

data class PublicIpInfo(
    val ip: String = "",
    val location: String = "",
    val country: String = "",
    val local: String = "",
    val ver4: String = "",
    val ver6: String = ""
) {
    val displayLocation: String
        get() {
            if (location.isNotBlank()) return location
            if (country.isNotBlank() || local.isNotBlank()) {
                return listOf(country, local).filter { it.isNotBlank() }.joinToString(" ")
            }
            return ""
        }
}

data class IpGeoInfo(
    val ip: String,
    val country: String = "未知",
    val region: String = "未知",
    val city: String = "未知",
    val isp: String = "未知",
    val org: String = "未知",
    val asName: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val isPrivate: Boolean = false,
    val ipVersion: String = "IPv4",
    val queryLogs: List<String> = emptyList(),
    // ip2region offline lookup result
    val ip2regionCountry: String = "未知",
    val ip2regionProvince: String = "未知",
    val ip2regionCity: String = "未知",
    val ip2regionIsp: String = "未知",
    val ip2regionRaw: String = "",
    // API (ZXINC / IP-API) result
    val apiCountry: String = "未知",
    val apiRegion: String = "未知",
    val apiCity: String = "未知",
    val apiIsp: String = "未知"
) {
    fun getDisplayLocation(provider: String = "IP2REGION"): String {
        if (provider.equals("IP2REGION", ignoreCase = true)) {
            val validParts = listOf(ip2regionCountry, ip2regionProvince, ip2regionCity)
                .filter { it.isNotBlank() && it != "0" && it != "未知" }
                .distinct()
            val ispPart = if (ip2regionIsp.isNotBlank() && ip2regionIsp != "0" && ip2regionIsp != "未知") ip2regionIsp else ""
            val loc = validParts.joinToString(" ")
            return when {
                loc.isNotBlank() && ispPart.isNotBlank() -> "$loc ($ispPart)"
                loc.isNotBlank() -> loc
                ispPart.isNotBlank() -> ispPart
                ip2regionRaw.isNotBlank() -> ip2regionRaw
                else -> listOf(country, region, city, isp).filter { it.isNotBlank() && it != "未知" }.joinToString(" ")
            }
        }
        val validParts = listOf(country, region, city).filter { it.isNotBlank() && it != "0" && it != "未知" }.distinct()
        val loc = validParts.joinToString(" ")
        return when {
            loc.isNotBlank() && isp.isNotBlank() && isp != "未知" -> "$loc ($isp)"
            loc.isNotBlank() -> loc
            isp.isNotBlank() && isp != "未知" -> isp
            else -> "未知"
        }
    }
}

object IpGeoEngine {

    private const val TAG = "IpGeoEngine"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun isPrivateIp(ip: String): Boolean {
        return try {
            val addr = InetAddress.getByName(ip)
            addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress
        } catch (e: Exception) {
            false
        }
    }

    suspend fun lookup(input: String): IpGeoInfo = withContext(Dispatchers.IO) {
        val trimmedInput = input.trim()
        val logs = mutableListOf<String>()
        logs.add("[Start] 正在检索归属地: $trimmedInput")
        Log.d(TAG, "[Start] Querying IP/Domain: $trimmedInput")

        var resolvedIp = trimmedInput
        try {
            val addr = InetAddress.getByName(trimmedInput)
            resolvedIp = addr.hostAddress ?: trimmedInput
            if (resolvedIp != trimmedInput) {
                logs.add("[DNS] 域名域名解析结果: $trimmedInput -> $resolvedIp")
                Log.d(TAG, "[DNS] Resolved domain $trimmedInput to $resolvedIp")
            }
        } catch (e: Exception) {
            logs.add("[DNS Warning] DNS 解析失败: ${e.localizedMessage}，直接使用原始输入")
            Log.w(TAG, "[DNS Warning] Resolution failed for $trimmedInput: ${e.message}")
        }

        val isV6 = resolvedIp.contains(":")
        val versionStr = if (isV6) "IPv6" else "IPv4"

        // Perform ip2region offline search first
        val ip2r = Ip2RegionSearcher.search(resolvedIp)
        logs.add("[ip2region] 本地离线库查询结果: ${ip2r.rawFormat}")

        var apiCountry = ""
        var apiRegion = ""
        var apiCity = ""
        var apiIsp = ""

        // Check if private IP
        if (isPrivateIp(resolvedIp)) {
            logs.add("[IP Type] 检测为私有局域网 IP ($resolvedIp)，跳过公网地理位置查询")
            Log.i(TAG, "[IP Type] Private IP detected: $resolvedIp")
            return@withContext IpGeoInfo(
                ip = resolvedIp,
                country = "中国",
                region = "局域网/内网",
                city = "私有地址段",
                isp = "LAN/本地网络",
                org = "Local Private Network",
                isPrivate = true,
                ipVersion = versionStr,
                queryLogs = logs,
                ip2regionCountry = ip2r.country,
                ip2regionProvince = ip2r.province,
                ip2regionCity = ip2r.city,
                ip2regionIsp = ip2r.isp,
                ip2regionRaw = ip2r.rawFormat,
                apiCountry = "中国",
                apiRegion = "局域网/内网",
                apiCity = "私有地址段",
                apiIsp = "LAN/本地网络"
            )
        }

        // 1. Try ZXINC API (https://ip.zxinc.org/api.php?type=json&ip=)
        try {
            val zxUrl = "https://ip.zxinc.org/api.php?type=json&ip=$resolvedIp"
            logs.add("[API-1] 正在请求 ZXINC 数据库 API: $zxUrl")
            Log.d(TAG, "[API-1] Querying zxinc: $zxUrl")

            val request = Request.Builder()
                .url(zxUrl)
                .header("User-Agent", "Mozilla/5.0 IPTools/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                logs.add("[API-1] HTTP 状态码: ${response.code}")
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        logs.add("[API-1] 响应数据: $body")
                        Log.d(TAG, "[API-1] Response: $body")
                        val json = JSONObject(body)
                        if (json.optInt("code", -1) == 0) {
                            val data = json.optJSONObject("data")
                            if (data != null) {
                                val country = data.optString("country", "中国")
                                    .replace("\t", " ").replace(Regex("\\s+"), " ").trim()
                                val location = data.optString("location", "")
                                    .replace("\t", " ").replace(Regex("\\s+"), " ").trim()
                                val local = data.optString("local", "未知ISP")
                                    .replace("\t", " ").replace(Regex("\\s+"), " ").trim()

                                var regionStr = "未知"
                                var cityStr = "未知"
                                val parts = country.split("–", "-", " ").filter { it.isNotBlank() }
                                if (parts.size >= 2) {
                                    regionStr = parts[1]
                                    if (parts.size >= 3) cityStr = parts[2]
                                } else {
                                    regionStr = country
                                }

                                apiCountry = if (country.isNotBlank()) country else "中国"
                                apiRegion = if (regionStr.isNotBlank()) regionStr else "公网"
                                apiCity = if (cityStr.isNotBlank()) cityStr else location
                                apiIsp = if (local.isNotBlank()) local else "公共网络"

                                logs.add("[API-1 SUCCESS] 归属地: $location | 运营商: $apiIsp")
                                return@withContext IpGeoInfo(
                                    ip = resolvedIp,
                                    country = apiCountry,
                                    region = apiRegion,
                                    city = apiCity,
                                    isp = apiIsp,
                                    org = location,
                                    isPrivate = false,
                                    ipVersion = versionStr,
                                    queryLogs = logs,
                                    ip2regionCountry = ip2r.country,
                                    ip2regionProvince = ip2r.province,
                                    ip2regionCity = ip2r.city,
                                    ip2regionIsp = ip2r.isp,
                                    ip2regionRaw = ip2r.rawFormat,
                                    apiCountry = apiCountry,
                                    apiRegion = apiRegion,
                                    apiCity = apiCity,
                                    apiIsp = apiIsp
                                )
                            }
                        } else {
                            logs.add("[API-1] 接口 code!=0, 返回消息: ${json.optString("msg")}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logs.add("[API-1 Error] ZXINC 请求失败: ${e.localizedMessage}")
            Log.e(TAG, "[API-1 Error] Failed: ${e.message}", e)
        }

        // 2. Try IP-API.com
        try {
            val ipApiUrl = "http://ip-api.com/json/$resolvedIp?lang=zh-CN"
            logs.add("[API-2] 正在请求 IP-API 备用接口: $ipApiUrl")
            Log.d(TAG, "[API-2] Querying ip-api: $ipApiUrl")

            val request = Request.Builder()
                .url(ipApiUrl)
                .build()

            client.newCall(request).execute().use { response ->
                logs.add("[API-2] HTTP 状态码: ${response.code}")
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        logs.add("[API-2] 响应数据: $body")
                        val json = JSONObject(body)
                        if (json.optString("status") == "success") {
                            logs.add("[API-2 SUCCESS] 成功获取归属地数据")
                            apiCountry = json.optString("country", "未知")
                            apiRegion = json.optString("regionName", "未知")
                            apiCity = json.optString("city", "未知")
                            apiIsp = json.optString("isp", "未知")
                            return@withContext IpGeoInfo(
                                ip = resolvedIp,
                                country = apiCountry,
                                region = apiRegion,
                                city = apiCity,
                                isp = apiIsp,
                                org = json.optString("org", json.optString("as", "未知")),
                                asName = json.optString("as", ""),
                                lat = json.optDouble("lat", 0.0),
                                lon = json.optDouble("lon", 0.0),
                                isPrivate = false,
                                ipVersion = versionStr,
                                queryLogs = logs,
                                ip2regionCountry = ip2r.country,
                                ip2regionProvince = ip2r.province,
                                ip2regionCity = ip2r.city,
                                ip2regionIsp = ip2r.isp,
                                ip2regionRaw = ip2r.rawFormat,
                                apiCountry = apiCountry,
                                apiRegion = apiRegion,
                                apiCity = apiCity,
                                apiIsp = apiIsp
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logs.add("[API-2 Error] IP-API 请求失败: ${e.localizedMessage}")
            Log.e(TAG, "[API-2 Error] Failed: ${e.message}", e)
        }

        logs.add("[ip2region Fallback] 使用 ip2region 离线库结果做最终回退")
        return@withContext IpGeoInfo(
            ip = resolvedIp,
            country = ip2r.country,
            region = ip2r.province,
            city = ip2r.city,
            isp = ip2r.isp,
            org = ip2r.displayString,
            isPrivate = false,
            ipVersion = versionStr,
            queryLogs = logs,
            ip2regionCountry = ip2r.country,
            ip2regionProvince = ip2r.province,
            ip2regionCity = ip2r.city,
            ip2regionIsp = ip2r.isp,
            ip2regionRaw = ip2r.rawFormat,
            apiCountry = apiCountry,
            apiRegion = apiRegion,
            apiCity = apiCity,
            apiIsp = apiIsp
        )
    }

    suspend fun getPublicIpV4Info(): PublicIpInfo = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://v4.ip.zxinc.org/info.php?type=json")
                .header("User-Agent", "Mozilla/5.0 IPTools/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        if (json.optInt("code", -1) == 0) {
                            val data = json.optJSONObject("data")
                            if (data != null) {
                                val myip = data.optString("myip", "").trim()
                                val ver4 = data.optString("ver4", "").trim()
                                val ver6 = data.optString("ver6", "").trim()
                                
                                val location = data.optString("location", "")
                                    .replace("\t", " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                                val country = data.optString("country", "")
                                    .replace("\t", " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                                val local = data.optString("local", "")
                                    .replace("\t", " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()

                                var finalV4 = if (!myip.contains(":") && myip.contains(".")) myip else ver4
                                if (finalV4.contains(":") || !finalV4.contains(".")) {
                                    finalV4 = ""
                                }

                                if (finalV4.isNotBlank()) {
                                    return@withContext PublicIpInfo(
                                        ip = finalV4,
                                        location = location,
                                        country = country,
                                        local = local,
                                        ver4 = ver4,
                                        ver6 = ver6
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val fallbackIp = getPublicIpV4Fallback()
        return@withContext PublicIpInfo(ip = fallbackIp)
    }

    suspend fun getPublicIpV6Info(): PublicIpInfo = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://v6.ip.zxinc.org/info.php?type=json")
                .header("User-Agent", "Mozilla/5.0 IPTools/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        if (json.optInt("code", -1) == 0) {
                            val data = json.optJSONObject("data")
                            if (data != null) {
                                val myip = data.optString("myip", "").trim()
                                val ver4 = data.optString("ver4", "").trim()
                                val ver6 = data.optString("ver6", "").trim()

                                val location = data.optString("location", "")
                                    .replace("\t", " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                                val country = data.optString("country", "")
                                    .replace("\t", " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                                val local = data.optString("local", "")
                                    .replace("\t", " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()

                                var finalV6 = if (myip.contains(":")) myip else ver6
                                if (!finalV6.contains(":")) {
                                    finalV6 = ""
                                }

                                if (finalV6.isNotBlank()) {
                                    return@withContext PublicIpInfo(
                                        ip = finalV6,
                                        location = location,
                                        country = country,
                                        local = local,
                                        ver4 = ver4,
                                        ver6 = ver6
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val fallbackIp = getPublicIpV6Fallback()
        return@withContext PublicIpInfo(ip = fallbackIp)
    }

    private suspend fun getPublicIpV4Fallback(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("https://api.ipify.org?format=json").build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    return@withContext json.optString("ip", "")
                }
            }
        } catch (_: Exception) {}
        return@withContext ""
    }

    private suspend fun getPublicIpV6Fallback(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("https://api64.ipify.org?format=json").build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val ip = json.optString("ip", "")
                    if (ip.contains(":")) return@withContext ip
                }
            }
        } catch (_: Exception) {}
        return@withContext ""
    }

    suspend fun getPublicIpV4(): String = getPublicIpV4Info().ip

    suspend fun getPublicIpV6(): String = getPublicIpV6Info().ip
}
