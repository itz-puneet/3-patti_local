package com.threepatti.core

data class Transfer(val fromId: String, val toId: String, val amount: Int)

object Settlement {
    /**
     * Who pays whom at the end of the session so that everyone ends up even,
     * using as few payments as a simple greedy match allows.
     */
    fun transfers(players: List<Player>): List<Transfer> {
        val debtors = players.filter { it.net < 0 }.map { it.id to -it.net }.toMutableList()
        val creditors = players.filter { it.net > 0 }.map { it.id to it.net }.toMutableList()
        val result = mutableListOf<Transfer>()
        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            debtors.sortByDescending { it.second }
            creditors.sortByDescending { it.second }
            val (debtor, owes) = debtors[0]
            val (creditor, due) = creditors[0]
            val amount = minOf(owes, due)
            result += Transfer(debtor, creditor, amount)
            if (owes == amount) debtors.removeAt(0) else debtors[0] = debtor to owes - amount
            if (due == amount) creditors.removeAt(0) else creditors[0] = creditor to due - amount
        }
        return result
    }
}
