package com.help.seguridad

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
import java.util.concurrent.Executors

class EmergencyLocationService : Service() {
    private val api = SupabaseApi()
    private lateinit var sessionStore: SecureSessionStore
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var emergencyId = ""

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val id = emergencyId
            if (id.isBlank()) return
            executor.execute {
                try {
                    var session = sessionStore.load() ?: return@execute
                    if (api.isSessionNearExpiry(session)) {
                        session = api.refreshSession(session)
                        sessionStore.save(session)
                    }
                    api.updateNetworkEmergency(session, id, location.latitude, location.longitude)
                } catch (_: Exception) {
                    // Se reintenta con la próxima ubicación. El SMS inicial ya fue enviado.
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionStore = SecureSessionStore(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        emergencyId = intent?.getStringExtra("emergency_id").orEmpty()
        if (emergencyId.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cerca_launcher)
            .setContentTitle("CERCA")
            .setContentText("Protección de emergencia activa")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(7302, notification)

        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            stopSelf()
            return START_NOT_STICKY
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .setMaxUpdateDelayMillis(12_000L)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            stopSelf()
        }

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopSelf() }, 30L * 60L * 1000L)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { client.removeLocationUpdates(callback) } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Emergencia CERCA activa",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Permite actualizar la ubicación mientras una emergencia CERCA está activa."
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "cerca_emergencia_activa"
    }
}
