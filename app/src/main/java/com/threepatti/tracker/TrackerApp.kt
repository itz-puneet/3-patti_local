package com.threepatti.tracker

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID

class TrackerApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var prefs: Prefs
        private set

    lateinit var sessions: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        sessions = SessionManager(this, prefs, scope)
    }
}

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("tracker", Context.MODE_PRIVATE)

    /** Stable id for this phone so the host gives it the same seat when it reconnects. */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }

    var playerName: String
        get() = prefs.getString(KEY_NAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var lastHostAddress: String
        get() = prefs.getString(KEY_LAST_HOST, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_HOST, value).apply()

    /** Buzz when it becomes this player's turn. */
    var vibrateOnTurn: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

    /** Open the card picker when this player sees their 3 Patti cards. */
    var askForCards: Boolean
        get() = prefs.getBoolean(KEY_ASK_FOR_CARDS, true)
        set(value) = prefs.edit().putBoolean(KEY_ASK_FOR_CARDS, value).apply()

    /** The Table tab shows a drawn table (true) or a list of players (false). */
    var tableView: Boolean
        get() = prefs.getBoolean(KEY_TABLE_VIEW, true)
        set(value) = prefs.edit().putBoolean(KEY_TABLE_VIEW, value).apply()

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_NAME = "name"
        const val KEY_LAST_HOST = "last_host"
        const val KEY_TABLE_VIEW = "table_view"
        const val KEY_VIBRATE = "vibrate_on_turn"
        const val KEY_ASK_FOR_CARDS = "ask_for_cards"
    }
}
