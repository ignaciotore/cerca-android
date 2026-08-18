package com.help.seguridad

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MedicationItem(
    val id: String,
    val name: String,
    val dose: String,
    val days: List<Int>,
    val times: List<String>,
    val reminderEnabled: Boolean
)

object MedicationStore {
    private const val KEY = "items"

    private fun prefs(context: Context) = context.getSharedPreferences(
        "cerca_medications_" + (SecureSessionStore(context).load()?.userId ?: "device"),
        Context.MODE_PRIVATE
    )

    fun load(context: Context): MutableList<MedicationItem> {
        val raw = prefs(context).getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { index ->
                val o = arr.getJSONObject(index)
                MedicationItem(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    dose = o.optString("dose"),
                    days = jsonInts(o.optJSONArray("days")).ifEmpty { listOf(1,2,3,4,5,6,7) },
                    times = jsonStrings(o.optJSONArray("times")).distinct().sorted(),
                    reminderEnabled = o.optBoolean("reminder_enabled", true)
                )
            }
        } catch (_: Exception) { mutableListOf() }
    }

    fun upsert(context: Context, item: MedicationItem) {
        val items = load(context)
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) items[index] = item else items.add(item)
        save(context, items)
    }

    fun delete(context: Context, id: String) {
        save(context, load(context).filterNot { it.id == id })
    }

    private fun save(context: Context, items: List<MedicationItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject()
                .put("id", item.id)
                .put("name", item.name)
                .put("dose", item.dose)
                .put("days", JSONArray().apply { item.days.forEach { put(it) } })
                .put("times", JSONArray().apply { item.times.forEach { put(it) } })
                .put("reminder_enabled", item.reminderEnabled))
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun jsonInts(arr: JSONArray?): List<Int> {
        if (arr == null) return emptyList()
        return List(arr.length()) { arr.optInt(it) }.filter { it in 1..7 }.distinct().sorted()
    }

    private fun jsonStrings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return List(arr.length()) { arr.optString(it) }.filter { Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(it) }
    }
}
