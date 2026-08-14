package com.cerca.mvp

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
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

    companion object {
        private const val ACTION_SMS_SENT = "com.cerca.mvp.SMS_SENT"
        private const val ACTION_SMS_DELIVERED = "com.cerca.mvp.SMS_DELIVERED"
        private const val EXTRA_BATCH = "batch"
    }

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

    private var currentSmsBatch = -1L
    private var expectedSmsParts = 0
    private var sentSmsParts = 0
    private var deliveredSmsParts = 0
    private var smsFailed = false

    private val smsSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getLongExtra(EXTRA_BATCH, -1L) != currentSmsBatch) return
            if (smsFailed) return

            if (resultCode == Activity.RESULT_OK) {
                sentSmsParts++
                if (sentSmsParts >= expectedSmsParts) {
                    status.text = "SMS enviado por la red."
                }
            } else {
                smsFailed = true
                status.text = "SMS NO enviado: ${smsErrorText(resultCode)}"
            }
        }
    }

    private val smsDeliveredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getLongExtra(EXTRA_BATCH, -1L) != currentSmsBatch) return
            if (smsFailed) return

            if (resultCode == Activity.RESULT_OK) {
                deliveredSmsParts++
                if (deliveredSmsParts >= expectedSmsParts) {
                    status.text = "SMS entregado al contacto."
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        name = findViewById(R.id.name)
        emergencyPhone = findViewById(R.id.emergencyPhone)
        smsPhone = findViewById(R.id.smsPhone)
        status = findViewById(R.id.status)

        ContextCompat.registerReceiver(
            this,
            smsSentReceiver,
            IntentFilter(ACTION_SMS_SENT),
            ContextCompat.RECEIVER_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            smsDeliveredReceiver,
            IntentFilter(ACTION_SMS_DELIVERED),
            ContextCompat.RECEIVER_EXPORTED
        )

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

    override fun onDestroy() {
        try {
            unregisterReceiver(smsSentReceiver)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(smsDeliveredReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
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
            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                permissionRequestCode
            )
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
            val destination = normalizePhone(smsPhone.text.toString())
            val message = "$person necesita ayuda. Ubicacion: $mapsLink"

            if (destination.isNotEmpty()) {
                sendSmsWithRealStatus(destination, message)
            } else {
                status.text = "No hay teléfono para SMS. Iniciando llamada..."
            }

            Handler(Looper.getMainLooper()).postDelayed({
                makeEmergencyCall()
            }, 1200)
        }
    }

    private fun sendAlertSmsOnly() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNeededPermissions()
            return
        }

        val destination = normalizePhone(smsPhone.text.toString())
        if (destination.isEmpty()) {
            Toast.makeText(
                this,
                "Falta el teléfono para SMS",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        status.text = "Obteniendo ubicación..."
        getLocation { mapsLink ->
            val person = name.text.toString().trim().ifEmpty { "La persona" }
            val message = "$person necesita ayuda. Ubicacion: $mapsLink"
            sendSmsWithRealStatus(destination, message)
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManagerForDefaultSim(): SmsManager? {
        val subId = SubscriptionManager.getDefaultSmsSubscriptionId()

        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            status.text =
                "No hay una SIM predeterminada para SMS. Elegila en Ajustes > SIMs > SMS."
            return null
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
    }

    private fun sendSmsWithRealStatus(destination: String, message: String) {
        val smsManager = smsManagerForDefaultSim() ?: return

        try {
            val parts = smsManager.divideMessage(message)
            currentSmsBatch = System.currentTimeMillis()
            expectedSmsParts = parts.size
            sentSmsParts = 0
            deliveredSmsParts = 0
            smsFailed = false

            val sentIntents = ArrayList<PendingIntent>()
            val deliveryIntents = ArrayList<PendingIntent>()

            val baseRequestCode =
                (currentSmsBatch xor (currentSmsBatch ushr 32)).toInt()

            for (i in parts.indices) {
                val sentIntent = Intent(ACTION_SMS_SENT)
                    .setPackage(packageName)
                    .putExtra(EXTRA_BATCH, currentSmsBatch)

                val deliveredIntent = Intent(ACTION_SMS_DELIVERED)
                    .setPackage(packageName)
                    .putExtra(EXTRA_BATCH, currentSmsBatch)

                val flags =
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

                sentIntents.add(
                    PendingIntent.getBroadcast(
                        this,
                        baseRequestCode + (i * 2),
                        sentIntent,
                        flags
                    )
                )

                deliveryIntents.add(
                    PendingIntent.getBroadcast(
                        this,
                        baseRequestCode + (i * 2) + 1,
                        deliveredIntent,
                        flags
                    )
                )
            }

            status.text = "Enviando SMS..."

            if (parts.size == 1) {
                smsManager.sendTextMessage(
                    destination,
                    null,
                    parts[0],
                    sentIntents[0],
                    deliveryIntents[0]
                )
            } else {
                smsManager.sendMultipartTextMessage(
                    destination,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            }
        } catch (e: Exception) {
            smsFailed = true
            status.text =
                "Error al solicitar SMS: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun smsErrorText(code: Int): String {
        return when (code) {
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "fallo de la red"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "radio apagada"
            SmsManager.RESULT_ERROR_NULL_PDU -> "error interno de SMS"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "sin servicio móvil"
            else -> "código $code"
        }
    }

    private fun normalizePhone(raw: String): String {
        val value = raw.trim()
        return value.filterIndexed { index, c ->
            c.isDigit() || (c == '+' && index == 0)
        }
    }

    private fun makeEmergencyCall() {
        val phone = normalizePhone(emergencyPhone.text.toString())

        if (phone.isEmpty()) {
            Toast.makeText(
                this,
                "Falta el teléfono para llamada",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNeededPermissions()
            return
        }

        status.text = "Llamando..."
        val intent = Intent(
            Intent.ACTION_CALL,
            Uri.fromParts("tel", phone, null)
        )
        startActivity(intent)
    }

    private fun hasCorePermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED &&
                (
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                    )
    }

    private fun getLocation(callback: (String) -> Unit) {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (
            fine != PackageManager.PERMISSION_GRANTED &&
            coarse != PackageManager.PERMISSION_GRANTED
        ) {
            callback("Ubicacion no disponible")
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(this)
        val cancellation = CancellationTokenSource()

        client.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellation.token
        )
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    callback(
                        "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                    )
                } else {
                    client.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            callback(
                                "https://maps.google.com/?q=${last.latitude},${last.longitude}"
                            )
                        } else {
                            callback("Ubicacion no disponible")
                        }
                    }
                }
            }
            .addOnFailureListener {
                client.lastLocation.addOnSuccessListener { last ->
                    if (last != null) {
                        callback(
                            "https://maps.google.com/?q=${last.latitude},${last.longitude}"
                        )
                    } else {
                        callback("Ubicacion no disponible")
                    }
                }
            }
    }
}
