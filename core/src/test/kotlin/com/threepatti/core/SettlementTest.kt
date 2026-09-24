package com.threepatti.core

import com.threepatti.core.net.NetUtils
import com.threepatti.core.net.Wire
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettlementTest {
    private fun player(id: String, net: Int) = Player(id = id, name = id, balance = 250 + net, buyIn = 250)

    @Test
    fun losersPayWinners() {
        val players = listOf(player("a", 120), player("b", -70), player("c", -30), player("d", -20), player("e", 0))
        val transfers = Settlement.transfers(players)
        assertEquals(
            listOf(Transfer("b", "a", 70), Transfer("c", "a", 30), Transfer("d", "a", 20)),
            transfers,
        )
    }

    @Test
    fun everyoneEndsEven() {
        val players = listOf(player("a", 55), player("b", 45), player("c", -60), player("d", -40))
        val transfers = Settlement.transfers(players)
        val after = players.associate { it.id to it.net }.toMutableMap()
        for (t in transfers) {
            after[t.fromId] = after.getValue(t.fromId) + t.amount
            after[t.toId] = after.getValue(t.toId) - t.amount
        }
        assertTrue(after.values.all { it == 0 }, after.toString())
        assertTrue(transfers.size <= players.size - 1)
    }

    @Test
    fun noTransfersWhenEven() {
        assertEquals(emptyList(), Settlement.transfers(listOf(player("a", 0), player("b", 0))))
    }

    @Test
    fun parsesTypedAddresses() {
        assertEquals("192.168.1.5" to Wire.DEFAULT_PORT, NetUtils.parseAddress(" 192.168.1.5 "))
        assertEquals("192.168.43.1" to 5000, NetUtils.parseAddress("192.168.43.1:5000"))
        assertNull(NetUtils.parseAddress(""))
        assertNull(NetUtils.parseAddress("192.168.1.5:99999"))
        assertNull(NetUtils.parseAddress("192.168.1.5:abc"))
        assertNull(NetUtils.parseAddress("http://192.168.1.5"))
        assertNull(NetUtils.parseAddress("my phone"))
    }

    @Test
    fun settingsValidation() {
        assertNull(TableSettings().validationError())
        assertEquals("Boot can't be more than the starting chips", TableSettings(startingBalance = 10, bootAmount = 20).validationError())
        assertEquals("Pot limit must be more than 10", TableSettings(potLimit = 10).validationError())
        assertNull(TableSettings(maxSeenBet = 0, potLimit = 0).validationError())
        assertEquals("Boot ₹5 · chaal limit ₹80 · no pot limit", TableSettings().summary())
    }
}
