package com.cerca.mvp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class LocationSharingService : Service() {

    companion object {
        const val ACTION_START = "com.cerca.mvp.START_LIVE_TRACKING"
        const val ACTION_STOP = "com.cerca.mvp.STOP_LIVE_TRACKING"
        const val EXTRA_TOPIC = "topic"
        const val EXTRA_NAME = "name"

        private const val CHANNEL_ID = "cerca_live_location"
        private const val NOTIFICATION_ID = 44
        private const val MAX_TRACKING_MS = 60L * 60L * 1000L
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val stopHandler = Handler(Looper.getMainLooper())

    private val client by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private var callback: LocationCallback? = null
    private var topic: String = ""
    private var personName: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                publishEnded()
                stopTracking()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                topic = intent.getStringExtra(EXTRA_TOPIC).orEmpty()
                personName = intent.getStringExtra(EXTRA_NAME).orEmpty()

                if (topic.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                createNotificationChannel()
                startForeground(NOTIFICATION_ID, buildNotification())
                startTracking()

                stopHandler.removeCallbacksAndMessages(null)
                stopHandler.postDelayed({
                    publishEnded()
                    stopTracking()
                    stopSelf()
                }, MAX_TRACKING_MS)

                return START_STICKY
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopHandler.removeCallbacksAndMessages(null)
        stopTracking()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun startTracking() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10_000L
        )
            .setMinUpdateIntervalMillis(5_000L)
            .setMaxUpdateDelayMillis(15_000L)
            .build()

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    publish(
                        JSONObject()
                            .put("lat", location.latitude)
                            .put("lon", location.longitude)
                            .put("accuracy", location.accuracy.toDouble())
                            .put("time", System.currentTimeMillis())
                            .put("name", personName)
                            .toString()
                    )
                }
            }
        }

        client.requestLocationUpdates(
            request,
            callback!!,
            Looper.getMainLooper()
        )
    }

    private fun stopTracking() {
        callback?.let {
            client.removeLocationUpdates(it)
        }
        callback = null

        getSharedPreferences("cerca", MODE_PRIVATE)
            .edit()
            .putBoolean("trackingActive", false)
            .remove("trackingTopic")
            .apply()
    }

    private fun publishEnded() {
        if (topic.isBlank()) return

        publish(
            JSONObject()
                .put("status", "ended")
                .put("name", personName)
                .put("time", System.currentTimeMillis())
                .toString()
        )
    }

    private fun publish(body: String) {
        val currentTopic = topic
        if (currentTopic.isBlank()) return

        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = URL("https://ntfy.sh/$currentTopic")
                    .openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")

                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                    it.write(body)
                }

                connection.responseCode
            } catch (_: Exception) {
                // Si no hay datos móviles, se vuelve a intentar con la próxima ubicación.
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ubicación en vivo de CERCA",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val openApp = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("CERCA · Ubicación en vivo")
            .setContentText("Tus contactos pueden seguir tu ubicación mientras la emergencia esté activa.")
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }
}
