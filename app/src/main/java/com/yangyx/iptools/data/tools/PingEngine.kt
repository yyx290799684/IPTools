package com.yangyx.iptools.data.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.util.regex.Pattern

data class PingPacketResult(
    val seq: Int,
    val bytes: Int = 64,
    val ip: String,
    val timeMs: Float,
    val ttl: Int = 64,
    val isSuccess: Boolean = true,
    val rawText: String = ""
)

data class PingSummary(
    val targetHost: String,
    val targetIp: String,
    val transmitted: Int,
    val received: Int,
    val packetLossPercentage: Float,
    val minRttMs: Float,
    val avgRttMs: Float,
    val maxRttMs: Float,
    val packets: List<PingPacketResult>
)

object PingEngine {

    fun executePing(
        host: String,
        count: Int = 4, // -1 for continuous / infinite
        size: Int = 56,
        timeoutMs: Int = 1000,
        intervalMs: Int = 1000,
        dontFragment: Boolean = false,
        ttl: Int = 64
    ): Flow<Pair<PingPacketResult?, PingSummary?>> = flow {
        val target = host.trim()
        if (target.isBlank()) return@flow

        var resolvedIp = target
        try {
            resolvedIp = InetAddress.getByName(target).hostAddress ?: target
        } catch (_: Exception) {}

        val isV6 = resolvedIp.contains(":")
        val pingCmd = if (isV6) "ping6" else "ping"

        val isContinuous = (count <= 0 || count == Int.MAX_VALUE)
        val targetCount = if (isContinuous) Int.MAX_VALUE else count

        val packets = mutableListOf<PingPacketResult>()
        var seqCounter = 1

        val timePattern = Pattern.compile("time=([0-9.]+)\\s*ms")
        val ttlPattern = Pattern.compile("ttl=([0-9]+)")
        val bytesPattern = Pattern.compile("([0-9]+)\\s*bytes")

        val timeoutSec = (timeoutMs / 1000).coerceAtLeast(1)
        val intervalSecStr = String.format("%.3f", (intervalMs.coerceAtLeast(1) / 1000.0))

        for (i in 1..targetCount) {
            val startTime = System.currentTimeMillis()
            var res: PingPacketResult? = null

            try {
                val command = mutableListOf<String>().apply {
                    add(pingCmd)
                    add("-c")
                    add("1")
                    add("-s")
                    add(size.toString())
                    add("-W")
                    add(timeoutSec.toString())
                    if (!isV6) {
                        add("-t")
                        add(ttl.toString())
                        if (dontFragment) {
                            add("-M")
                            add("do")
                        }
                    }
                    add(target)
                }

                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                var gotReply = false
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (currentLine.contains("bytes from") || currentLine.contains("64 bytes")) {
                        var timeVal = (System.currentTimeMillis() - startTime).toFloat()
                        var ttlVal = ttl
                        var bytesVal = size + 8

                        val timeMatcher = timePattern.matcher(currentLine)
                        if (timeMatcher.find()) {
                            timeVal = timeMatcher.group(1)?.toFloatOrNull() ?: timeVal
                        }
                        val ttlMatcher = ttlPattern.matcher(currentLine)
                        if (ttlMatcher.find()) {
                            ttlVal = ttlMatcher.group(1)?.toIntOrNull() ?: ttl
                        }
                        val bytesMatcher = bytesPattern.matcher(currentLine)
                        if (bytesMatcher.find()) {
                            bytesVal = bytesMatcher.group(1)?.toIntOrNull() ?: (size + 8)
                        }

                        res = PingPacketResult(
                            seq = seqCounter++,
                            bytes = bytesVal,
                            ip = resolvedIp,
                            timeMs = timeVal,
                            ttl = ttlVal,
                            isSuccess = true,
                            rawText = currentLine
                        )
                        gotReply = true
                        break
                    }
                }
                process.waitFor()

                if (!gotReply) {
                    res = PingPacketResult(
                        seq = seqCounter++,
                        bytes = 0,
                        ip = resolvedIp,
                        timeMs = 0f,
                        ttl = 0,
                        isSuccess = false,
                        rawText = "Request timed out."
                    )
                }
            } catch (_: Exception) {
                // Fallback socket check if system ping fails
                val socketStartTime = System.currentTimeMillis()
                val reachable = try {
                    InetAddress.getByName(target).isReachable(timeoutMs)
                } catch (_: Exception) { false }
                val rtt = (System.currentTimeMillis() - socketStartTime).toFloat()

                res = PingPacketResult(
                    seq = seqCounter++,
                    bytes = if (reachable) size + 8 else 0,
                    ip = resolvedIp,
                    timeMs = if (reachable) rtt else 0f,
                    ttl = ttl,
                    isSuccess = reachable,
                    rawText = if (reachable) "Reply from $resolvedIp: bytes=${size + 8} time=${rtt}ms TTL=$ttl" else "Request timed out."
                )
            }

            res?.let {
                packets.add(it)
                emit(Pair(it, null))
            }

            if (intervalMs > 0 && i < targetCount) {
                delay(intervalMs.toLong())
            }
        }

        val received = packets.count { it.isSuccess }
        val totalTransmitted = packets.size
        val lossPct = if (totalTransmitted > 0) ((totalTransmitted - received).toFloat() / totalTransmitted) * 100f else 100f
        val validTimes = packets.filter { it.isSuccess }.map { it.timeMs }
        val minRtt = if (validTimes.isNotEmpty()) validTimes.minOrNull() ?: 0f else 0f
        val maxRtt = if (validTimes.isNotEmpty()) validTimes.maxOrNull() ?: 0f else 0f
        val avgRtt = if (validTimes.isNotEmpty()) validTimes.average().toFloat() else 0f

        val summary = PingSummary(
            targetHost = target,
            targetIp = resolvedIp,
            transmitted = totalTransmitted,
            received = received,
            packetLossPercentage = lossPct,
            minRttMs = minRtt,
            avgRttMs = avgRtt,
            maxRttMs = maxRtt,
            packets = packets
        )

        emit(Pair(null, summary))
    }.flowOn(Dispatchers.IO)
}

