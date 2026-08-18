package com.help.seguridad

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalTime
import java.time.ZonedDateTime

object MedicationReminderScheduler {
    private const val PREFS = "cerca_medication_alarm_ids"
    private const val KEY_IDS = "ids"

    fun sync(context: Context) {
        cancelAll(context)
        val ids = mutableSetOf<String>()
        MedicationStore.load(context)
            .filter { it.reminderEnabled && it.times.isNotEmpty() }
            .forEach { item ->
                item.times.forEachIndexed { index, time ->
                    schedule(context, item.id, item.name, item.dose, item.days, time, index)
                    ids.add(requestCode(item.id, index).toString())
                }
            }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_IDS, ids).apply()
    }

    fun rescheduleAfterBoot(context: Context) = sync(context)

    fun scheduleNextFromIntent(context: Context, intent: Intent) {
        val id = intent.getStringExtra("med_id") ?: return
        val name = intent.getStringExtra("med_name") ?: return
        val dose = intent.getStringExtra("med_dose") ?: ""
        val days = intent.getStringExtra("med_days").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }
        val time = intent.getStringExtra("med_time") ?: return
        val slot = intent.getIntExtra("med_slot", 0)
        schedule(context, id, name, dose, if (days.isEmpty()) listOf(1,2,3,4,5,6,7) else days, time, slot)
    }

    private fun schedule(context: Context, id: String, name: String, dose: String, days: List<Int>, time: String, slot: Int) {
        val trigger = nextTrigger(days, time) ?: return
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MedicationReminderReceiver::class.java)
            .putExtra("med_id", id)
            .putExtra("med_name", name)
            .putExtra("med_dose", dose)
            .putExtra("med_days", days.joinToString(","))
            .putExtra("med_time", time)
            .putExtra("med_slot", slot)
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(id, slot),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
    }

    private fun nextTrigger(days: List<Int>, time: String): Long? {
        val localTime = runCatching { LocalTime.parse(time) }.getOrNull() ?: return null
        val now = ZonedDateTime.now()
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (date.dayOfWeek.value !in days) continue
            val candidate = ZonedDateTime.of(date, localTime, now.zone)
            if (candidate.isAfter(now.plusSeconds(20))) return candidate.toInstant().toEpochMilli()
        }
        return null
    }

    private fun cancelAll(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        stored.forEach { raw ->
            val code = raw.toIntOrNull() ?: return@forEach
            val pi = PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, MedicationReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) { alarm.cancel(pi); pi.cancel() }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_IDS).apply()
    }

    fun requestCode(id: String, slot: Int): Int = ((id + "|" + slot).hashCode() and 0x7fffffff)
}
