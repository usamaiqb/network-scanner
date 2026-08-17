package com.networkscanner.app.util

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.Charset

/**
 * Minimal DNS client used to run reverse (PTR) lookups against a specific
 * server — typically the local gateway, whose built-in resolver knows the
 * DHCP-registered names for devices on the LAN. The system resolver can miss
 * these when the network hands out a public DNS server (e.g. 8.8.8.8).
 *
 * [queryPtr] returns:
 *  - the hostname (trailing dot stripped) on success,
 *  - an empty string when the server responded but had no PTR record,
 *  - null when the server did not respond (timeout/error).
 */
object DnsLookup {

    private const val DNS_PORT = 53
    private const val TYPE_PTR = 12
    private const val TYPE_CNAME = 5

    private val charset = Charset.forName("ISO-8859-1")

    /**
     * Build a DNS PTR query packet for `ipAddress` (in-addr.arpa reverse lookup).
     */
    fun buildPtrQuery(ipAddress: String): ByteArray {
        val octets = ipAddress.split(".")
        if (octets.size != 4 || octets.any { it.toIntOrNull() == null }) return ByteArray(0)

        val reverse = "${octets[3]}.${octets[2]}.${octets[1]}.${octets[0]}.in-addr.arpa"
        val qname = reverse.split(".").flatMap { label ->
            listOf(label.length.toByte()) + label.toByteArray(charset).toList()
        }.toByteArray() + 0x00.toByte()

        val header = byteArrayOf(
            0x12, 0x34,                         // Transaction ID
            0x01, 0x00,                         // Flags: standard query, RD
            0x00, 0x01,                         // QDCOUNT = 1
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00  // AN/NS/ARCOUNT = 0
        )
        val question = byteArrayOf(0x00, 0x0C, 0x00, 0x01) // QTYPE=PTR, QCLASS=IN
        return header + qname + question
    }

    /**
     * Query `server` on port 53 for the PTR record of `ipAddress`.
     */
    fun queryPtr(server: String, ipAddress: String, timeoutMs: Int = 500): String? {
        val query = buildPtrQuery(ipAddress)
        if (query.isEmpty()) return null
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val serverAddr = InetAddress.getByName(server)
                socket.send(DatagramPacket(query, query.size, serverAddr, DNS_PORT))
                val buffer = ByteArray(512)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                parsePtrResponse(buffer, response.length) ?: ""
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePtrResponse(data: ByteArray, length: Int): String? {
        if (length < 12) return null
        val qdCount = u16(data, 4)
        val anCount = u16(data, 6)
        val nsCount = u16(data, 8)
        val arCount = u16(data, 10)

        var index = 12
        repeat(qdCount) {
            index = skipName(data, index)
            index += 4 // QTYPE + QCLASS
        }

        repeat(anCount + nsCount + arCount) {
            if (index >= length) return null
            index = skipName(data, index)
            if (index + 10 > length) return null
            val type = u16(data, index); index += 2
            index += 2 // class
            index += 4 // ttl
            val rdLength = u16(data, index); index += 2
            if (type == TYPE_PTR || type == TYPE_CNAME) {
                val name = readName(data, index) ?: return null
                return name.trimEnd('.')
            }
            index += rdLength
        }
        return null
    }

    private fun u16(data: ByteArray, i: Int): Int =
        ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)

    private fun skipName(data: ByteArray, start: Int): Int {
        var i = start
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            if (b == 0) return i + 1
            if (b and 0xC0 == 0xC0) return i + 2
            i += b + 1
        }
        return i
    }

    private fun readName(data: ByteArray, start: Int): String? {
        val parts = mutableListOf<String>()
        var i = start
        var iterations = 0
        while (i < data.size && iterations < 64) {
            iterations++
            val b = data[i].toInt() and 0xFF
            if (b == 0) break
            if (b and 0xC0 == 0xC0) {
                val next = data.getOrNull(i + 1)?.toInt()?.and(0xFF) ?: return null
                i = ((b and 0x3F) shl 8) or next
                continue
            }
            val len = b
            if (i + 1 + len > data.size) return null
            parts.add(String(data, i + 1, len, charset))
            i += len + 1
        }
        return if (parts.isEmpty()) null else parts.joinToString(".")
    }
}
