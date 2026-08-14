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

data class NetworkOverview(
    val lanIpV4: String = "未连接",
    val lanIpV6: String = "未配置",
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

    fun getNetworkOverview(context: Context): NetworkOverview {
        var lanV4 = "未连接"
        var lanV6 = "未配置"
        var ifaceName = "none"

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val candidateV4 = mutableListOf<Pair<String, String>>() // name to ip
            val candidateV6 = mutableListOf<Pair<String, String>>() // name to ip

            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        candidateV4.add(iface.name to ip)
                    } else if (addr is Inet6Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val ip = addr.hostAddress ?: continue
                        candidateV6.add(iface.name to ip)
                    }
                }
            }

            // Prioritize Wi-Fi interfaces (wlan, ap, p2p) over cellular (rmnet, ccmnni, pdp, tun) for IPv4
            fun isWifiIface(name: String) = name.contains("wlan", ignoreCase = true) ||
                    name.contains("ap", ignoreCase = true) ||
                    name.contains("p2p", ignoreCase = true)

            fun isCellularIface(name: String) = name.contains("rmnet", ignoreCase = true) ||
                    name.contains("pdp", ignoreCase = true) ||
                    name.contains("ccmnni", ignoreCase = true)

            val wifiCandidateV4 = candidateV4.firstOrNull { isWifiIface(it.first) }
            if (wifiCandidateV4 != null) {
                lanV4 = wifiCandidateV4.second
                ifaceName = wifiCandidateV4.first
            } else {
                val nonCellularCandidateV4 = candidateV4.firstOrNull { !isCellularIface(it.first) }
                if (nonCellularCandidateV4 != null) {
                    lanV4 = nonCellularCandidateV4.second
                    ifaceName = nonCellularCandidateV4.first
                } else if (candidateV4.isNotEmpty()) {
                    lanV4 = candidateV4.first().second
                    ifaceName = candidateV4.first().first
                }
            }

            // Prioritize Wi-Fi interfaces for IPv6 as well
            val wifiCandidateV6 = candidateV6.firstOrNull { isWifiIface(it.first) }
            if (wifiCandidateV6 != null) {
                lanV6 = wifiCandidateV6.second
            } else {
                val nonCellularCandidateV6 = candidateV6.firstOrNull { !isCellularIface(it.first) }
                if (nonCellularCandidateV6 != null) {
                    lanV6 = nonCellularCandidateV6.second
                } else if (candidateV6.isNotEmpty()) {
                    lanV6 = candidateV6.first().second
                }
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

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

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
