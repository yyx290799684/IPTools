package com.yangyx.iptools.data.tools

import com.yangyx.iptools.data.geo.IpGeoEngine
import com.yangyx.iptools.data.geo.IpGeoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.regex.Pattern

data class TraceHop(
    val hopNumber: Int,
    val ip: String = "*",
    val hostname: String = "",
    val timeMs: Float = 0f,
    val isReached: Boolean = false,
    val geoInfo: IpGeoInfo? = null
)

object TraceEngine {

    fun executeTraceroute(
        targetHost: String,
        maxHops: Int = 30,
        timeoutMs: Int = 1500,
        mode: String = "ICMP" // ICMP or UDP
    ): Flow<TraceHop> = flow {
        val target = targetHost.trim()
        if (target.isBlank()) return@flow

        var destinationIp = target
        try {
            destinationIp = InetAddress.getByName(target).hostAddress ?: target
        } catch (_: Exception) {}

        val isV6 = destinationIp.contains(":")
        val pingCmd = if (isV6) "ping6" else "ping"
        var destinationReached = false

        val ipPattern = Pattern.compile("(?i)(?:from\\s+)?([a-fA-F0-9.:_-]+)")
        val timePattern = Pattern.compile("time=([0-9.]+)\\s*ms")

        for (hop in 1..maxHops) {
            if (destinationReached) break

            var hopIp = "*"
            var rtt = 0f
            var success = false

            val startTime = System.currentTimeMillis()

            if (mode == "UDP") {
                // UDP Mode Traceroute
                try {
                    val udpSocket = DatagramSocket()
                    udpSocket.soTimeout = timeoutMs
                    val destAddr = InetAddress.getByName(destinationIp)
                    val udpData = "IPTools Traceroute Probe".toByteArray()
                    val packet = DatagramPacket(udpData, udpData.size, destAddr, 33434 + hop)

                    udpSocket.send(packet)

                    val recvBuf = ByteArray(512)
                    val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
                    udpSocket.receive(recvPacket)

                    rtt = (System.currentTimeMillis() - startTime).toFloat()
                    hopIp = recvPacket.address.hostAddress ?: "*"
                    success = true
                    if (hopIp == destinationIp) {
                        destinationReached = true
                    }
                    udpSocket.close()
                } catch (_: Exception) {
                    // UDP socket probe timeout or TTL ICMP unreachable handled below
                }
            }

            if (!success && (mode == "ICMP" || mode == "UDP")) {
                // ICMP Mode Process Ping
                try {
                    val timeoutSec = (timeoutMs / 1000).coerceAtLeast(1)
                    val command = listOf(
                        pingCmd,
                        "-c", "1",
                        "-W", timeoutSec.toString(),
                        if (!isV6) "-t" else "-h", hop.toString(),
                        target
                    )
                    val process = ProcessBuilder(command).redirectErrorStream(true).start()
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        val l = line?.trim() ?: continue
                        if (l.contains("Time to live exceeded", ignoreCase = true) || l.contains("Time Exceeded", ignoreCase = true) || l.contains("From", ignoreCase = true)) {
                            // Extracted intermediate hop IP from From x.x.x.x
                            val parts = l.split(" ").filter { it.isNotBlank() }
                            for (p in parts) {
                                val clean = p.replace(":", "").replace("(", "").replace(")", "").trim()
                                if (clean.matches(Regex("^[0-9a-fA-F.:_-]+$")) && clean.contains(".")) {
                                    hopIp = clean
                                    success = true
                                    break
                                }
                            }
                            val timeMatcher = timePattern.matcher(l)
                            if (timeMatcher.find()) {
                                rtt = timeMatcher.group(1)?.toFloatOrNull() ?: (System.currentTimeMillis() - startTime).toFloat()
                            } else {
                                rtt = (System.currentTimeMillis() - startTime).toFloat()
                            }
                        } else if (l.contains("bytes from", ignoreCase = true) || l.contains("64 bytes", ignoreCase = true)) {
                            hopIp = destinationIp
                            val timeMatcher = timePattern.matcher(l)
                            if (timeMatcher.find()) {
                                rtt = timeMatcher.group(1)?.toFloatOrNull() ?: (System.currentTimeMillis() - startTime).toFloat()
                            } else {
                                rtt = (System.currentTimeMillis() - startTime).toFloat()
                            }
                            success = true
                            destinationReached = true
                        }
                    }
                    process.waitFor()
                } catch (_: Exception) {
                }
            }

            var geo: IpGeoInfo? = null
            var resolvedHost = ""
            if (success && hopIp != "*") {
                try {
                    resolvedHost = InetAddress.getByName(hopIp).canonicalHostName
                } catch (_: Exception) {}
                geo = IpGeoEngine.lookup(hopIp)
                if (hopIp == destinationIp) {
                    destinationReached = true
                }
            }

            val resultHop = TraceHop(
                hopNumber = hop,
                ip = hopIp,
                hostname = resolvedHost,
                timeMs = if (rtt > 0f) rtt else (System.currentTimeMillis() - startTime).toFloat(),
                isReached = success && hopIp != "*",
                geoInfo = geo
            )

            emit(resultHop)
        }
    }.flowOn(Dispatchers.IO)
}

