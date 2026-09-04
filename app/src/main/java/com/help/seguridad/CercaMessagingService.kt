package com.help.seguridad

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CercaMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["event"] == "resolved") {
            NotificationManagerCompat.from(this).cancel(notificationId(data["emergency_id"].orEmpty()))
            return
        }
        showEmergencyNotification(this, data)
    }

    companion object {
        private const val CHANNEL_ID = "cerca_emergencias"
        private const val CHANNEL_NAME = "Alertas CERCA"

        fun showEmergencyNotification(context: Context, data: Map<String, String>) {
            ensureChannel(context)
            val emergencyId = data["emergency_id"].orEmpty()
            val personName = data["person_name"].orEmpty().ifBlank { "Una persona de tu Red CERCA" }

            val alertIntent = Intent(context, EmergencyAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("emergency_id", emergencyId)
                putExtra("owner_user_id", data["owner_user_id"].orEmpty())
                putExtra("person_name", personName)
                putExtra("mode", data["mode"].orEmpty())
                putExtra("latitude", data["latitude"].orEmpty())
                putExtra("longitude", data["longitude"].orEmpty())
                putExtra("medical_access", data["medical_access"].orEmpty())
            }
            val pending = PendingIntent.getActivity(
                context,
                notificationId(emergencyId),
                alertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cerca_launcher)
                .setContentTitle("🚨 $personName necesita ayuda")
                .setContentText("Tocá para ver la alerta CERCA y su ubicación.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pending)
                .setFullScreenIntent(pending, true)

            if (Build.VERSION.SDK_INT < 33 ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context).notify(notificationId(emergencyId), builder.build())
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alertas prioritarias cuando una persona de tu Red CERCA necesita ayuda."
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
            }
        }

        private fun notificationId(emergencyId: String): Int {
            return 7300 + (emergencyId.hashCode() and 0x0FFF)
        }
    }
}
