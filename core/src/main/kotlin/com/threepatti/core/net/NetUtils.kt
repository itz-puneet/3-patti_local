package com.threepatti.core.net

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

data class LocalAddress(val interfaceName: String, val ip: String)

object NetUtils {
    private val ignoredInterfaces = listOf("rmnet", "ccmni", "v4-rmnet", "dummy", "tun", "lo")

    /** IPv4 addresses of this device on local networks (WiFi, hotspot, ethernet), best first. */
    fun localAddresses(): List<LocalAddress> = lanInterfaces()
        .flatMap { nif ->
            nif.inetAddresses.toList()
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .mapNotNull { addr -> addr.hostAddress?.let { LocalAddress(nif.name, it) } }
        }
        .sortedBy { rank(it) }

    /** Where to send discovery broadcasts: every LAN interface's broadcast address plus the global one. */
    fun broadcastAddresses(): List<InetAddress> {
        val result = LinkedHashSet<InetAddress>()
        for (nif in lanInterfaces()) {
            for (ia in nif.interfaceAddresses) {
                ia.broadcast?.let { result += it }
            }
        }
        result += InetAddress.getByName("255.255.255.255")
        return result.toList()
    }

    /** Parses "192.168.1.5" or "192.168.1.5:47474". Returns null when the text is not an address. */
    fun parseAddress(input: String): Pair<String, Int>? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val colon = text.lastIndexOf(':')
        val (host, port) = if (colon > 0 && text.count { it == ':' } == 1) {
            val port = text.substring(colon + 1).toIntOrNull() ?: return null
            text.substring(0, colon) to port
        } else {
            text to Wire.DEFAULT_PORT
        }
        if (port !in 1..65535 || host.isEmpty()) return null
        if (!host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return null
        return host to port
    }

    private fun lanInterfaces(): List<NetworkInterface> = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().filter { nif ->
            runCatching { nif.isUp && !nif.isLoopback && !nif.isPointToPoint }.getOrDefault(false) &&
                ignoredInterfaces.none { nif.name.startsWith(it) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun rank(address: LocalAddress): Int {
        val name = address.interfaceName
        val preferredName = name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan") ||
            name.startsWith("eth") || name.startsWith("en") || name.startsWith("wl")
        val privateRange = address.ip.startsWith("192.168.") || address.ip.startsWith("10.") ||
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(address.ip)
        return (if (preferredName) 0 else 2) + (if (privateRange) 0 else 1)
    }
}
