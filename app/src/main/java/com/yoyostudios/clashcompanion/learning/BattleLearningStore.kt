package com.yoyostudios.clashcompanion.learning

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Registra jogadas do jogador para análise futura.
 * Não classifica jogadas como boas ou ruins sem dados confiáveis.
 */
object BattleLearningStore {
    private const val TAG = "BattleLearning"
    private const val PREFS = "battle_learning_v1"
    private const val KEY_EVENTS = "play_events"
    private const val MAX_EVENTS = 300

    data class PlayEvent(
        val timestamp: Long,
        val card: String,
        val zone: String,
        val hand: List<String>,
        val source: String
    )

    @Synchronized
    fun recordPlay(
        context: Context,
        card: String,
        zone: String,
        hand: Map<Int, String>,
        source: String
    ) {
        try {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            val events = JSONArray(
                prefs.getString(KEY_EVENTS, "[]") ?: "[]"
            )

            val event = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("card", card)
                put("zone", zone)
                put("hand", JSONArray(hand.toSortedMap().values.toList()))
                put("source", source)
            }

            events.put(event)

            val trimmed = JSONArray()
            val start = (events.length() - MAX_EVENTS).coerceAtLeast(0)

            for (i in start until events.length()) {
                trimmed.put(events.getJSONObject(i))
            }

            prefs.edit()
                .putString(KEY_EVENTS, trimmed.toString())
                .apply()

        } catch (e: Exception) {
            Log.w(TAG, "Could not save play event: ${e.message}")
        }
    }

    fun recentPlays(
        context: Context,
        limit: Int = 30
    ): List<PlayEvent> {
        val result = mutableListOf<PlayEvent>()

        try {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            val events = JSONArray(
                prefs.getString(KEY_EVENTS, "[]") ?: "[]"
            )

            val start = (
                events.length() - limit.coerceAtLeast(1)
            ).coerceAtLeast(0)

            for (i in start until events.length()) {
                val item = events.getJSONObject(i)
                val handJson = item.optJSONArray("hand") ?: JSONArray()

                val hand = (0 until handJson.length())
                    .mapNotNull {
                        handJson.optString(it).takeIf(String::isNotBlank)
                    }

                result += PlayEvent(
                    timestamp = item.optLong("timestamp"),
                    card = item.optString("card"),
                    zone = item.optString("zone"),
                    hand = hand,
                    source = item.optString("source", "unknown")
                )
            }

        } catch (e: Exception) {
            Log.w(TAG, "Could not read play history: ${e.message}")
        }

        return result
    }

    fun playCountsByCard(context: Context): Map<String, Int> =
        recentPlays(context, MAX_EVENTS)
            .groupingBy { it.card }
            .eachCount()
}
