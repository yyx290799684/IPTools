package com.yangyx.iptools.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.Locale

data class NetworkOverview(
    val lanIpV4: String = "未连接",
    val rawLanIpV4: String = "",
    val lanSubnetMask: String = "",
    val lanPrefixLength: Int = 0,
    val lanBroadcast: String = "",
    val lanGateway: String = "",
    val lanNetworkCidr: String = "",
    val lanIpV6: String = "未配置",
    val rawLanIpV6: String = "",
    val lanPrefixLengthV6: Int = 0,
    val wifiSsid: String = "未连接",
    val wifiBssid: String = "未连接",
    val wifiRssi: Int = 0, // dBm
    val wifiLinkSpeed: Int = 0, // Mbps
    val wifiFrequency: Int = 0, // MHz
    val wifiSignalLevel: Int = 0, // 0-4
    val cellularOperator: String = "无卡 / 未连接",
    val cellularNetworkType: String = "未知",
    val cellularRssi: Int = 0, // dBm or arbitrary unit
    val cellularSignalLevel: Int = 0, // 0-4
    val interfaceName: String = "none",
    val isWifiActive: Boolean = false,
    val isCellularActive: Boolean = false
)

data class NetworkSpeed(
    val downloadSpeedBytesPerSec: Long = 0,
    val uploadSpeedBytesPerSec: Long = 0,
    val totalReceivedBytes: Long = 0,
    val totalSentBytes: Long = 0
)

data class DiscoveredDevice(
    val ip: String,
    val mac: String = "未知 MAC",
    val hostname: String = "未知 Host",
    val responseTimeMs: Long = 0,
    val isReachable: Boolean = true
)

object NetworkInfoScanner {

    private data class CandidateV4(
        val ifaceName: String,
        val ip: String,
        val prefixLength: Int,
        val broadcast: String
    )

    private data class CandidateV6(
        val ifaceName: String,
        val ip: String,
        val prefixLength: Int
    )

    fun prefixLengthToSubnetMask(prefix: Int): String {
        if (prefix <= 0 || prefix > 32) return ""
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        return String.format(
            Locale.US,
            "%d.%d.%d.%d",
            (mask shr 24) and 0xFF,
            (mask shr 16) and 0xFF,
            (mask shr 8) and 0xFF,
            mask and 0xFF
        )
    }

    fun calculateSubnetCidr(ip: String, prefix: Int): String {
        if (prefix <= 0 || prefix > 32) return ip
        return try {
            val parts = ip.split(".").map { it.toInt() }
            if (parts.size != 4) return "$ip/$prefix"
            var ipInt = 0L
            for (p in parts) {
                ipInt = (ipInt shl 8) or (p.toLong() and 0xFF)
            }
            val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            val netInt = ipInt and mask
            String.format(
                Locale.US,
                "%d.%d.%d.%d/%d",
                (netInt shr 24) and 0xFF,
                (netInt shr 16) and 0xFF,
                (netInt shr 8) and 0xFF,
                netInt and 0xFF,
                prefix
            )
        } catch (_: Exception) {
            "$ip/$prefix"
        }
    }

    fun calculateBroadcast(ip: String, prefix: Int): String {
        if (prefix <= 0 || prefix >= 31) return ""
        return try {
            val parts = ip.split(".").map { it.toInt() }
            if (parts.size != 4) return ""
            var ipInt = 0L
            for (p in parts) {
                ipInt = (ipInt shl 8) or (p.toLong() and 0xFF)
            }
            val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            val bcastInt = ipInt or (mask.inv() and 0xFFFFFFFFL)
            String.format(
                Locale.US,
                "%d.%d.%d.%d",
                (bcastInt shr 24) and 0xFF,
                (bcastInt shr 16) and 0xFF,
                (bcastInt shr 8) and 0xFF,
                bcastInt and 0xFF
            )
        } catch (_: Exception) {
            ""
        }
    }

    fun getNetworkOverview(context: Context): NetworkOverview {
        var rawLanV4 = ""
        var lanV4 = "未连接"
        var lanSubnetMask = ""
        var lanPrefixLength = 0
        var lanBroadcast = ""
        var lanGateway = ""
        var lanNetworkCidr = ""

        var rawLanV6 = ""
        var lanV6 = "未配置"
        var lanPrefixLengthV6 = 0
        var ifaceName = "none"

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val linkProps = cm?.getLinkProperties(activeNet)
        val caps = cm?.getNetworkCapabilities(activeNet)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        // Try to get default gateway from LinkProperties
        if (linkProps != null) {
            try {
                for (route in linkProps.routes) {
                    if (route.isDefaultRoute && route.hasGateway()) {
                        val gw = route.gateway?.hostAddress
                        if (!gw.isNullOrBlank()) {
                            lanGateway = gw
                            break
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val candidateV4 = mutableListOf<CandidateV4>()
            val candidateV6 = mutableListOf<CandidateV6>()

            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (ifaceAddr in iface.interfaceAddresses) {
                    val addr = ifaceAddr.address
                    val prefix = ifaceAddr.networkPrefixLength.toInt()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val bcast = ifaceAddr.broadcast?.hostAddress ?: ""
                        candidateV4.add(CandidateV4(iface.name, ip, prefix, bcast))
                    } else if (addr is Inet6Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val ip = addr.hostAddress ?: continue
                        candidateV6.add(CandidateV6(iface.name, ip, prefix))
                    }
                }
            }

            // Check if active network's LinkProperties directly has an IPv4 LinkAddress
            var chosenV4: CandidateV4? = null
            if (linkProps != null) {
                for (la in linkProps.linkAddresses) {
                    val addr = la.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val prefix = la.prefixLength
                        val bcast = candidateV4.firstOrNull { it.ip == ip }?.broadcast ?: ""
                        chosenV4 = CandidateV4(linkProps.interfaceName ?: "active", ip, prefix, bcast)
                        break
                    }
                }
            }

            // If not found in LinkProperties, pick the best interface candidate
            if (chosenV4 == null) {
                fun isWifiIface(name: String) = name.contains("wlan", ignoreCase = true) ||
                        name.contains("ap", ignoreCase = true) ||
                        name.contains("p2p", ignoreCase = true)

                fun isCellularIface(name: String) = name.contains("rmnet", ignoreCase = true) ||
                        name.contains("pdp", ignoreCase = true) ||
                        name.contains("ccmnni", ignoreCase = true)

                chosenV4 = candidateV4.firstOrNull { isWifiIface(it.ifaceName) }
                    ?: candidateV4.firstOrNull { !isCellularIface(it.ifaceName) }
                    ?: candidateV4.firstOrNull()
            }

            if (chosenV4 != null) {
                rawLanV4 = chosenV4.ip
                lanPrefixLength = chosenV4.prefixLength
                lanV4 = if (lanPrefixLength in 1..32) "${chosenV4.ip}/$lanPrefixLength" else chosenV4.ip
                lanSubnetMask = prefixLengthToSubnetMask(lanPrefixLength)
                lanNetworkCidr = calculateSubnetCidr(chosenV4.ip, lanPrefixLength)
                lanBroadcast = chosenV4.broadcast.ifEmpty { calculateBroadcast(chosenV4.ip, lanPrefixLength) }
                ifaceName = chosenV4.ifaceName
            }

            // Check IPv6 from linkProps or candidate list
            var chosenV6: CandidateV6? = null
            if (linkProps != null) {
                for (la in linkProps.linkAddresses) {
                    val addr = la.address
                    if (addr is Inet6Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val ip = addr.hostAddress ?: continue
                        chosenV6 = CandidateV6(linkProps.interfaceName ?: "active", ip, la.prefixLength)
                        break
                    }
                }
            }

            if (chosenV6 == null) {
                fun isWifiIface(name: String) = name.contains("wlan", ignoreCase = true) ||
                        name.contains("ap", ignoreCase = true) ||
                        name.contains("p2p", ignoreCase = true)

                fun isCellularIface(name: String) = name.contains("rmnet", ignoreCase = true) ||
                        name.contains("pdp", ignoreCase = true) ||
                        name.contains("ccmnni", ignoreCase = true)

                chosenV6 = candidateV6.firstOrNull { isWifiIface(it.ifaceName) }
                    ?: candidateV6.firstOrNull { !isCellularIface(it.ifaceName) }
                    ?: candidateV6.firstOrNull()
            }

            if (chosenV6 != null) {
                rawLanV6 = chosenV6.ip
                lanPrefixLengthV6 = chosenV6.prefixLength
                lanV6 = if (lanPrefixLengthV6 in 1..128) "${chosenV6.ip}/$lanPrefixLengthV6" else chosenV6.ip
                if (ifaceName == "none") ifaceName = chosenV6.ifaceName
            }
        } catch (_: Exception) {}

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo
        var wifiSsid = "未连接"
        var wifiBssid = "未连接"
        var wifiRssi = 0
        var wifiLinkSpeed = 0
        var wifiFreq = 0
        var wifiLevel = 0

        if (wifiInfo != null && wifiInfo.networkId != -1) {
            val rawSsid = wifiInfo.ssid
            wifiSsid = if (rawSsid != null && rawSsid != "<unknown ssid>") rawSsid.replace("\"", "") else "已连接 Wi-Fi"
            wifiBssid = wifiInfo.bssid ?: "00:00:00:00:00:00"
            wifiRssi = wifiInfo.rssi
            wifiLinkSpeed = wifiInfo.linkSpeed
            wifiFreq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) wifiInfo.frequency else 0
            wifiLevel = WifiManager.calculateSignalLevel(wifiRssi, 5)
        }

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        var cellularOperator = telephonyManager?.networkOperatorName.takeIf { !it.isNullOrBlank() } ?: "无蜂窝数据"
        var cellularType = "未知"
        var cellularRssi = -110
        var cellularLevel = 0

        if (telephonyManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val cellInfos = telephonyManager.allCellInfo
                    if (!cellInfos.isNullOrEmpty()) {
                        for (info in cellInfos) {
                            if (info.isRegistered) {
                                when (info) {
                                    is CellInfoLte -> {
                                        cellularType = "4G LTE"
                                        cellularRssi = info.cellSignalStrength.dbm
                                        cellularLevel = info.cellSignalStrength.level
                                    }
                                    is CellInfoNr -> {
                                        cellularType = "5G NR"
                                        cellularRssi = info.cellSignalStrength.dbm
                                        cellularLevel = info.cellSignalStrength.level
                                    }
                                    is CellInfoWcdma -> {
                                        cellularType = "3G WCDMA"
                                        cellularRssi = info.cellSignalStrength.dbm
                                        cellularLevel = info.cellSignalStrength.level
                                    }
                                    is CellInfoGsm -> {
                                        cellularType = "2G GSM"
                                        cellularRssi = info.cellSignalStrength.dbm
                                        cellularLevel = info.cellSignalStrength.level
                                    }
                                }
                                break
                            }
                        }
                    }
                }
            } catch (_: SecurityException) {
                // Location permission needed for cell info on Android 10+
            }
        }

        return NetworkOverview(
            lanIpV4 = lanV4,
            lanIpV6 = lanV6,
            wifiSsid = wifiSsid,
            wifiBssid = wifiBssid,
            wifiRssi = wifiRssi,
            wifiLinkSpeed = wifiLinkSpeed,
            wifiFrequency = wifiFreq,
            wifiSignalLevel = wifiLevel,
            cellularOperator = cellularOperator,
            cellularNetworkType = cellularType,
            cellularRssi = cellularRssi,
            cellularSignalLevel = cellularLevel,
            interfaceName = ifaceName,
            isWifiActive = isWifi,
            isCellularActive = isCell
        )
    }

    fun getActiveDnsServer(context: Context): String {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork
            if (activeNet != null) {
                val linkProps = cm.getLinkProperties(activeNet)
                val dnsServers = linkProps?.dnsServers?.mapNotNull { it.hostAddress }
                if (!dnsServers.isNullOrEmpty()) {
                    val firstDns = dnsServers.first()
                    if (firstDns.isNotBlank()) return firstDns
                }
            }
        } catch (_: Exception) {}
        return "223.5.5.5"
    }

    fun getSpeedTrafficFlow(): Flow<NetworkSpeed> = flow {
        var lastRx = TrafficStats.getTotalRxBytes()
        var lastTx = TrafficStats.getTotalTxBytes()
        var lastTime = System.currentTimeMillis()

        while (true) {
            delay(1000)
            val currentRx = TrafficStats.getTotalRxBytes()
            val currentTx = TrafficStats.getTotalTxBytes()
            val currentTime = System.currentTimeMillis()

            val timeDiffSec = (currentTime - lastTime) / 1000.0
            if (timeDiffSec > 0) {
                val rxDiff = if (currentRx >= lastRx && lastRx != TrafficStats.UNSUPPORTED.toLong()) currentRx - lastRx else 0
                val txDiff = if (currentTx >= lastTx && lastTx != TrafficStats.UNSUPPORTED.toLong()) currentTx - lastTx else 0

                val rxRate = (rxDiff / timeDiffSec).toLong()
                val txRate = (txDiff / timeDiffSec).toLong()

                emit(
                    NetworkSpeed(
                        downloadSpeedBytesPerSec = rxRate,
                        uploadSpeedBytesPerSec = txRate,
                        totalReceivedBytes = currentRx,
                        totalSentBytes = currentTx
                    )
                )

                lastRx = currentRx
                lastTx = currentTx
                lastTime = currentTime
            }
        }
    }.flowOn(Dispatchers.IO)
}
