package com.help.seguridad

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class MedicationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MedicationReminderScheduler.scheduleNextFromIntent(context, intent)
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val name = intent.getStringExtra("med_name") ?: "tu medicación"
        val dose = intent.getStringExtra("med_dose").orEmpty()
        val time = intent.getStringExtra("med_time").orEmpty()
        val id = intent.getStringExtra("med_id").orEmpty()
        val slot = intent.getIntExtra("med_slot", 0)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Recordatorios de medicación", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisos de CERCA para tomar medicación"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            context,
            9100,
            Intent(context, MedicationActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val detail = listOf(dose.takeIf { it.isNotBlank() }, time.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Hora de tomar " + name)
            .setContentText(if (detail.isBlank()) "Recordatorio de CERCA" else detail)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify(MedicationReminderScheduler.requestCode(id, slot), notification)
    }

    companion object { private const val CHANNEL_ID = "cerca_medication_reminders_v1" }
}
