package com.threepatti.core.net

import com.threepatti.core.GameAction
import com.threepatti.core.GameState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Phones talk to the host over TCP with one JSON message per line.
 * The host answers UDP broadcasts so players can find the table without typing an address.
 */
object Wire {
    /** Bump when messages change in a way older app versions can't read. */
    const val PROTOCOL_VERSION = 1
    const val DEFAULT_PORT = 47474
    const val DISCOVERY_PORT = 47475
    const val DISCOVERY_REQUEST = "3PATTI_TRACKER_DISCOVER_V1"

    const val PING_INTERVAL_MS = 5_000L
    const val READ_TIMEOUT_MS = 20_000
    const val CONNECT_TIMEOUT_MS = 5_000

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(message: ClientMessage): String = json.encodeToString(ClientMessage.serializer(), message)

    fun encode(message: ServerMessage): String = json.encodeToString(ServerMessage.serializer(), message)

    fun decodeClient(line: String): ClientMessage? =
        runCatching { json.decodeFromString(ClientMessage.serializer(), line) }.getOrNull()

    fun decodeServer(line: String): ServerMessage? =
        runCatching { json.decodeFromString(ServerMessage.serializer(), line) }.getOrNull()
}

@Serializable
sealed class ClientMessage {
    @Serializable
    @SerialName("hello")
    data class Hello(val protocol: Int, val deviceId: String, val name: String) : ClientMessage()

    @Serializable
    @SerialName("action")
    data class Act(val action: GameAction) : ClientMessage()

    @Serializable
    @SerialName("ping")
    data object Ping : ClientMessage()
}

@Serializable
sealed class ServerMessage {
    @Serializable
    @SerialName("welcome")
    data class Welcome(val playerId: String) : ServerMessage()

    @Serializable
    @SerialName("state")
    data class State(val state: GameState) : ServerMessage()

    /** An action was refused. The connection stays open. */
    @Serializable
    @SerialName("error")
    data class Error(val message: String) : ServerMessage()

    /** The host is ending this connection for good (table closed, player removed, wrong app version). */
    @Serializable
    @SerialName("goodbye")
    data class Goodbye(val message: String) : ServerMessage()

    @Serializable
    @SerialName("ping")
    data object Ping : ServerMessage()
}

/** Reply to a discovery broadcast. */
@Serializable
data class TableAnnouncement(
    val protocol: Int,
    val tableName: String,
    val hostName: String,
    val port: Int,
    val players: Int,
)
