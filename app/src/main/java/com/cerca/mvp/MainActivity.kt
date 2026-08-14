package com.cerca.mvp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class MainActivity : AppCompatActivity() {

    private val permissionRequestCode = 99
    private val permissions = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private lateinit var name: EditText
    private lateinit var emergencyPhone: EditText
    private lateinit var smsPhone: EditText
    private lateinit var status: TextView
    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdTriggered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        name = findViewById(R.id.name)
        emergencyPhone = findViewById(R.id.emergencyPhone)
        smsPhone = findViewById(R.id.smsPhone)
        status = findViewById(R.id.status)

        loadSettings()
        requestNeededPermissions()

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Datos guardados", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.testSms).setOnClickListener {
            saveSettings()
            sendAlertSmsOnly()
        }

        findViewById<Button>(R.id.testCall).setOnClickListener {
            saveSettings()
            makeEmergencyCall()
        }

        val help = findViewById<Button>(R.id.helpButton)
        help.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    holdTriggered = false
                    status.text = "Seguí apretando..."
                    holdHandler.postDelayed({
                        holdTriggered = true
                        saveSettings()
                        triggerFullEmergency()
                    }, 3000)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!holdTriggered) {
                        holdHandler.removeCallbacksAndMessages(null)
                        status.text = "Mantené apretado 3 segundos para pedir ayuda."
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun prefs() = getSharedPreferences("cerca", MODE_PRIVATE)

    private fun loadSettings() {
        name.setText(prefs().getString("name", ""))
        emergencyPhone.setText(prefs().getString("emergencyPhone", ""))
        smsPhone.setText(prefs().getString("smsPhone", ""))
    }

    private fun saveSettings() {
        prefs().edit()
            .putString("name", name.text.toString().trim())
            .putString("emergencyPhone", emergencyPhone.text.toString().trim())
            .putString("smsPhone", smsPhone.text.toString().trim())
            .apply()
    }

    private fun requestNeededPermissions() {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), permissionRequestCode)
        }
    }

    private fun triggerFullEmergency() {
        if (!hasCorePermissions()) {
            status.text = "Necesito permisos de Teléfono, SMS y Ubicación."
            requestNeededPermissions()
            return
        }

        status.text = "Obteniendo ubicación..."
        getLocation { mapsLink ->
            val person = name.text.toString().trim().ifEmpty { "La persona" }
            val message = "$person pidió ayuda. Ubicación actual: $mapsLink"
            val destination = smsPhone.text.toString().trim()

            if (destination.isNotEmpty()) {
                try {
                    SmsManager.getDefault().sendTextMessage(destination, null, message, null, null)
                    status.text = "SMS enviado. Iniciando llamada..."
                } catch (e: Exception) {
                    status.text = "No se pudo enviar el SMS. Iniciando llamada..."
                }
            } else {
                status.text = "No hay teléfono para SMS. Iniciando llamada..."
            }

            Handler(Looper.getMainLooper()).postDelayed({
                makeEmergencyCall()
            }, 1000)
        }
    }

    private fun sendAlertSmsOnly() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestNeededPermissions()
            return
        }

        status.text = "Obteniendo ubicación..."
        getLocation { mapsLink ->
            val person = name.text.toString().trim().ifEmpty { "La persona" }
            val destination = smsPhone.text.toString().trim()

            if (destination.isEmpty()) {
                Toast.makeText(this, "Falta el teléfono para SMS", Toast.LENGTH_LONG).show()
                return@getLocation
            }

            val message = "$person pidió ayuda. Ubicación actual: $mapsLink"
            try {
                SmsManager.getDefault().sendTextMessage(destination, null, message, null, null)
                status.text = "SMS enviado."
                Toast.makeText(this, "SMS enviado", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                status.text = "Error enviando SMS."
            }
        }
    }

    private fun makeEmergencyCall() {
        val phone = emergencyPhone.text.toString().trim()
        if (phone.isEmpty()) {
            Toast.makeText(this, "Falta el teléfono para llamada", Toast.LENGTH_LONG).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestNeededPermissions()
            return
        }

        status.text = "Llamando..."
        val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", phone, null))
        startActivity(intent)
    }

    private fun hasCorePermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                 ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    private fun getLocation(callback: (String) -> Unit) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            callback("Ubicación no disponible")
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(this)
        val cancellation = CancellationTokenSource()

        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    callback("https://maps.google.com/?q=${loc.latitude},${loc.longitude}")
                } else {
                    client.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            callback("https://maps.google.com/?q=${last.latitude},${last.longitude}")
                        } else {
                            callback("Ubicación no disponible")
                        }
                    }
                }
            }
            .addOnFailureListener {
                client.lastLocation.addOnSuccessListener { last ->
                    if (last != null) {
                        callback("https://maps.google.com/?q=${last.latitude},${last.longitude}")
                    } else {
                        callback("Ubicación no disponible")
                    }
                }
            }
    }
}
