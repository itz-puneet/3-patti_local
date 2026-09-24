package com.threepatti.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class DiscoveredTable(
    val address: String,
    val port: Int,
    val tableName: String,
    val hostName: String,
    val players: Int,
    val protocol: Int,
    val game: String = "3 Patti",
) {
    val compatible: Boolean get() = protocol == Wire.PROTOCOL_VERSION
}

object Discovery {
    /** Broadcasts a discovery request and collects the tables that answer within [timeoutMs]. */
    suspend fun scan(
        targets: List<InetAddress> = NetUtils.broadcastAddresses(),
        discoveryPort: Int = Wire.DISCOVERY_PORT,
        timeoutMs: Long = 1_500,
    ): List<DiscoveredTable> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, DiscoveredTable>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 200
                val payload = Wire.DISCOVERY_REQUEST.toByteArray(Charsets.UTF_8)
                fun sendAll() = targets.forEach { target ->
                    runCatching { socket.send(DatagramPacket(payload, payload.size, target, discoveryPort)) }
                }
                sendAll()
                val start = System.currentTimeMillis()
                var resent = false
                val buffer = ByteArray(2048)
                while (isActive && System.currentTimeMillis() - start < timeoutMs) {
                    // UDP can drop packets, so ask a second time halfway through.
                    if (!resent && System.currentTimeMillis() - start > timeoutMs / 2) {
                        sendAll()
                        resent = true
                    }
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    val announcement = runCatching {
                        Wire.json.decodeFromString(TableAnnouncement.serializer(), text)
                    }.getOrNull() ?: continue
                    val address = packet.address?.hostAddress ?: continue
                    found["$address:${announcement.port}"] = DiscoveredTable(
                        address = address,
                        port = announcement.port,
                        tableName = announcement.tableName,
                        hostName = announcement.hostName,
                        players = announcement.players,
                        protocol = announcement.protocol,
                        game = announcement.game,
                    )
                }
            }
        }
        found.values.toList()
    }
}
