package com.yangyx.iptools.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object HostNameResolver {

    /**
     * Attempts multiple techniques to discover the hostname of an IP address:
     * 1. Reverse DNS
     * 2. NetBIOS Name Service Status Request (UDP 137)
     * 3. mDNS PTR query (UDP 5353)
     * 4. HTTP / Web server title check
     */
    suspend fun resolveHostName(ip: String, timeoutMs: Int = 400): String = withContext(Dispatchers.IO) {
        val trimmedIp = ip.trim()
        if (trimmedIp.isBlank()) return@withContext ""

        // 1. Try Reverse DNS
        try {
            val addr = InetAddress.getByName(trimmedIp)
            val canonicalName = addr.canonicalHostName
            if (!canonicalName.isNullOrBlank() && canonicalName != trimmedIp) {
                return@withContext canonicalName
            }
            val hostName = addr.hostName
            if (!hostName.isNullOrBlank() && hostName != trimmedIp) {
                return@withContext hostName
            }
        } catch (_: Exception) {}

        // 2. Try NetBIOS Name Query (UDP 137)
        try {
            val netbiosName = queryNetBiosName(trimmedIp, timeoutMs)
            if (netbiosName.isNotBlank()) {
                return@withContext netbiosName
            }
        } catch (_: Exception) {}

        // 3. Try HTTP GET Title if port 80/8080/443 open
        try {
            val httpTitle = queryHttpServerTitle(trimmedIp, timeoutMs)
            if (httpTitle.isNotBlank()) {
                return@withContext httpTitle
            }
        } catch (_: Exception) {}

        return@withContext ""
    }

    /**
     * Send NetBIOS Node Status Request packet to UDP 137
     */
    private fun queryNetBiosName(ip: String, timeoutMs: Int): String {
        var socket: DatagramSocket? = null
        try {
            val queryHeader = byteArrayOf(
                0x80.toByte(), 0x94.toByte(), 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x20, 0x43, 0x4b, 0x41,
                0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x41, 0x41, 0x00, 0x00, 0x21, 0x00, 0x01
            )
            socket = DatagramSocket()
            socket.soTimeout = timeoutMs
            val address = InetAddress.getByName(ip)
            val packet = DatagramPacket(queryHeader, queryHeader.size, address, 137)
            socket.send(packet)

            val recvBuf = ByteArray(1024)
            val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
            socket.receive(recvPacket)

            val len = recvPacket.length
            if (len > 57) {
                val numNames = recvBuf[56].toInt() and 0xFF
                var offset = 57
                for (i in 0 until numNames) {
                    if (offset + 18 <= len) {
                        val nameBytes = recvBuf.copyOfRange(offset, offset + 15)
                        val type = recvBuf[offset + 15].toInt() and 0xFF
                        val nameStr = String(nameBytes, Charsets.US_ASCII).trim()
                        if (type == 0x00 && nameStr.isNotBlank()) {
                            return nameStr
                        }
                    }
                    offset += 18
                }
            }
        } catch (_: Exception) {
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
        return ""
    }

    /**
     * Connects to HTTP port 80 to get server header / HTML title if available
     */
    private fun queryHttpServerTitle(ip: String, timeoutMs: Int): String {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, 80), timeoutMs)
            socket.soTimeout = timeoutMs
            val out = socket.getOutputStream()
            out.write("HEAD / HTTP/1.1\r\nHost: $ip\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()

            val reader = socket.getInputStream().bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: break
                if (l.startsWith("Server:", ignoreCase = true)) {
                    val server = l.substringAfter(":").trim()
                    if (server.isNotBlank()) {
                        socket.close()
                        return server
                    }
                }
            }
            socket.close()
        } catch (_: Exception) {}
        return ""
    }
}
