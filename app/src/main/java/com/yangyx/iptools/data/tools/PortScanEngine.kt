package com.yangyx.iptools.data.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.InetSocketAddress
import java.net.Socket

data class ScannedPort(
    val port: Int,
    val serviceName: String,
    val isOpen: Boolean,
    val banner: String = "",
    val responseTimeMs: Long = 0
)

data class PortScanProgress(
    val scannedCount: Int,
    val totalPorts: Int,
    val openPorts: List<ScannedPort>,
    val currentPort: Int
)

object PortScanEngine {

    val COMMON_PORTS = mapOf(
        21 to "FTP",
        22 to "SSH",
        23 to "Telnet",
        25 to "SMTP",
        53 to "DNS",
        80 to "HTTP",
        110 to "POP3",
        143 to "IMAP",
        443 to "HTTPS",
        445 to "SMB/CIFS",
        1433 to "MSSQL",
        1521 to "Oracle",
        3306 to "MySQL",
        3389 to "RDP",
        5432 to "PostgreSQL",
        5900 to "VNC",
        6379 to "Redis",
        8080 to "HTTP-Proxy",
        8443 to "HTTPS-Alt",
        9000 to "FastCGI",
        9200 to "ElasticSearch",
        11211 to "Memcached",
        27017 to "MongoDB"
    )

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

    fun scanPorts(
        host: String,
        portsToScan: List<Int>,
        timeoutMs: Int = 400,
        concurrency: Int = 50
    ): Flow<PortScanProgress> = flow {
        val target = host.trim()
        if (target.isBlank() || portsToScan.isEmpty()) return@flow

        val total = portsToScan.size
        val openPortsList = mutableListOf<ScannedPort>()
        var scannedCount = 0

        // Emit initial status
        emit(
            PortScanProgress(
                scannedCount = 0,
                totalPorts = total,
                openPorts = emptyList(),
                currentPort = portsToScan.first()
            )
        )

        val batchSize = if (total > 2000) 100 else 30
        val emitInterval = if (total > 1000) (total / 200).coerceIn(10, 200) else 1

        // Process in batches
        val chunks = portsToScan.chunked(batchSize)
        for (chunk in chunks) {
            coroutineScope {
                val deferreds = chunk.map { port ->
                    async(Dispatchers.IO) {
                        val startTime = System.currentTimeMillis()
                        var isOpen = false
                        var banner = ""
                        try {
                            val socket = Socket()
                            socket.connect(InetSocketAddress(target, port), timeoutMs)
                            isOpen = true
                            try {
                                socket.soTimeout = 300
                                val reader = socket.getInputStream().bufferedReader()
                                if (reader.ready()) {
                                    banner = reader.readLine()?.take(100) ?: ""
                                }
                            } catch (_: Exception) {}
                            socket.close()
                        } catch (_: Exception) {
                            isOpen = false
                        }
                        val rtt = System.currentTimeMillis() - startTime
                        ScannedPort(
                            port = port,
                            serviceName = COMMON_PORTS[port] ?: "Custom",
                            isOpen = isOpen,
                            banner = banner,
                            responseTimeMs = rtt
                        )
                    }
                }
                val results = deferreds.awaitAll()
                var newlyOpened = false
                for (res in results) {
                    scannedCount++
                    if (res.isOpen) {
                        openPortsList.add(res)
                        newlyOpened = true
                    }
                }
                if (newlyOpened || scannedCount % emitInterval == 0 || scannedCount == total) {
                    emit(
                        PortScanProgress(
                            scannedCount = scannedCount,
                            totalPorts = total,
                            openPorts = openPortsList.toList(),
                            currentPort = results.lastOrNull()?.port ?: 0
                        )
                    )
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
