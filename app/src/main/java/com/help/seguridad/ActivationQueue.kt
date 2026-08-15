package com.help.seguridad

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class ActivationQueue(private val context: Context) {
    companion object { private const val PREFS = "help_activation_queue" }

    data class Event(
        val id: String,
        val userId: String,
        val activatedAt: String,
        val appVersion: String
    )

    fun enqueue(userId: String, appVersion: String): Event {
        val event = Event(UUID.randomUUID().toString(), userId, Instant.now().toString(), appVersion)
        val all = loadMutable()
        all.put(JSONObject()
            .put("id", event.id)
            .put("user_id", event.userId)
            .put("activated_at", event.activatedAt)
            .put("app_version", event.appVersion))
        save(all)
        return event
    }

    fun pendingFor(userId: String): List<Event> {
        val array = loadMutable()
        val out = mutableListOf<Event>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            if (o.optString("user_id") != userId) continue
            out += Event(
                id = o.optString("id"),
                userId = o.optString("user_id"),
                activatedAt = o.optString("activated_at"),
                appVersion = o.optString("app_version")
            )
        }
        return out
    }

    fun remove(ids: Set<String>) {
        if (ids.isEmpty()) return
        val old = loadMutable()
        val fresh = JSONArray()
        for (i in 0 until old.length()) {
            val o = old.optJSONObject(i) ?: continue
            if (!ids.contains(o.optString("id"))) fresh.put(o)
        }
        save(fresh)
    }

    fun clearFor(userId: String) {
        val old = loadMutable()
        val fresh = JSONArray()
        for (i in 0 until old.length()) {
            val o = old.optJSONObject(i) ?: continue
            if (o.optString("user_id") != userId) fresh.put(o)
        }
        save(fresh)
    }

    private fun loadMutable(): JSONArray {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("events", "[]") ?: "[]"
        return try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    }

    private fun save(array: JSONArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("events", array.toString()).apply()
    }
}
