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
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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

    private lateinit var mainPanel: LinearLayout
    private lateinit var settingsPanel: LinearLayout
    private lateinit var name: EditText
    private lateinit var emergencyPhone: EditText
    private lateinit var smsPhone1: EditText
    private lateinit var smsPhone2: EditText
    private lateinit var smsPhone3: EditText
    private lateinit var smsPhone4: EditText
    private lateinit var status: TextView

    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdTriggered = false

    private var currentSmsBatch = -1L
    private var expectedSmsParts = 0
    private var sentSmsParts = 0
    private var deliveredSmsParts = 0
    private var failedSmsParts = 0

    private val smsSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getLongExtra(EXTRA_BATCH, -1L) != currentSmsBatch) return

            if (resultCode == Activity.RESULT_OK) {
                sentSmsParts++
            } else {
                failedSmsParts++
            }

            if (sentSmsParts + failedSmsParts >= expectedSmsParts) {
                status.text = if (failedSmsParts == 0) {
                    "Alertas enviadas."
                } else {
                    "Se enviaron alertas, pero hubo $failedSmsParts error(es) de SMS."
                }
            }
        }
    }

    private val smsDeliveredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getLongExtra(EXTRA_BATCH, -1L) != currentSmsBatch) return
            if (resultCode == Activity.RESULT_OK) {
                deliveredSmsParts++
                if (deliveredSmsParts >= expectedSmsParts && failedSmsParts == 0) {
                    status.text = "Alertas entregadas a tus contactos."
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainPanel = findViewById(R.id.mainPanel)
        settingsPanel = findViewById(R.id.settingsPanel)
        name = findViewById(R.id.name)
        emergencyPhone = findViewById(R.id.emergencyPhone)
        smsPhone1 = findViewById(R.id.smsPhone1)
        smsPhone2 = findViewById(R.id.smsPhone2)
        smsPhone3 = findViewById(R.id.smsPhone3)
        smsPhone4 = findViewById(R.id.smsPhone4)
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

        migrateOldSettings()
        loadSettings()
        requestNeededPermissions()

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            loadSettings()
            showSettings()
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            if (saveSettings()) {
                Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
                showMain()
            }
        }

        findViewById<Button>(R.id.cancelSettingsButton).setOnClickListener {
            if (hasMinimumConfiguration()) showMain()
        }

        val help = findViewById<Button>(R.id.helpButton)
        help.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    holdTriggered = false
                    status.text = "Seguí apretando..."
                    holdHandler.postDelayed({
                        holdTriggered = true
                        triggerEmergency()
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

        if (hasMinimumConfiguration()) showMain() else showSettings()
    }

    override fun onDestroy() {
        try { unregisterReceiver(smsSentReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(smsDeliveredReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun prefs() = getSharedPreferences("cerca", MODE_PRIVATE)

    private fun migrateOldSettings() {
        val p = prefs()
        if (p.getString("smsPhone1", "").isNullOrBlank()) {
            val oldSms = p.getString("smsPhone", "") ?: ""
            if (oldSms.isNotBlank()) {
                p.edit().putString("smsPhone1", oldSms).apply()
            }
        }
    }

    private fun loadSettings() {
        val p = prefs()
        name.setText(p.getString("name", ""))
        emergencyPhone.setText(p.getString("emergencyPhone", ""))
        smsPhone1.setText(p.getString("smsPhone1", ""))
        smsPhone2.setText(p.getString("smsPhone2", ""))
        smsPhone3.setText(p.getString("smsPhone3", ""))
        smsPhone4.setText(p.getString("smsPhone4", ""))
    }

    private fun saveSettings(): Boolean {
        val person = name.text.toString().trim()
        val callPhone = normalizePhone(emergencyPhone.text.toString())
        val sms1 = normalizePhone(smsPhone1.text.toString())

        if (person.isBlank()) {
            Toast.makeText(this, "Ingresá el nombre", Toast.LENGTH_LONG).show()
            return false
        }
        if (callPhone.isBlank()) {
            Toast.makeText(this, "Ingresá el teléfono para llamada", Toast.LENGTH_LONG).show()
            return false
        }
        if (sms1.isBlank()) {
            Toast.makeText(this, "Ingresá al menos un contacto para SMS", Toast.LENGTH_LONG).show()
            return false
        }

        prefs().edit()
            .putString("name", person)
            .putString("emergencyPhone", callPhone)
            .putString("smsPhone1", sms1)
            .putString("smsPhone2", normalizePhone(smsPhone2.text.toString()))
            .putString("smsPhone3", normalizePhone(smsPhone3.text.toString()))
            .putString("smsPhone4", normalizePhone(smsPhone4.text.toString()))
            .apply()
        return true
    }

    private fun hasMinimumConfiguration(): Boolean {
        val p = prefs()
        return !p.getString("name", "").isNullOrBlank() &&
                !p.getString("emergencyPhone", "").isNullOrBlank() &&
                !p.getString("smsPhone1", "").isNullOrBlank()
    }

    private fun showMain() {
        settingsPanel.visibility = View.GONE
        mainPanel.visibility = View.VISIBLE
        status.text = "Mantené apretado 3 segundos para pedir ayuda."
    }

    private fun showSettings() {
        mainPanel.visibility = View.GONE
        settingsPanel.visibility = View.VISIBLE
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

    private fun triggerEmergency() {
        if (!hasMinimumConfiguration()) {
            showSettings()
            Toast.makeText(this, "Completá la configuración primero", Toast.LENGTH_LONG).show()
            return
        }

        if (!hasCorePermissions()) {
            status.text = "Necesito permisos de Teléfono, SMS y Ubicación."
            requestNeededPermissions()
            return
        }

        status.text = "Activando H.E.L.P...."

        getLocation { mapsLink ->
            val person = prefs().getString("name", "")?.ifBlank { "La persona" } ?: "La persona"
            val message = "$person necesita ayuda. Ubicacion: $mapsLink"
            sendSmsToSavedContacts(message)
        }

        makeEmergencyCall()
    }

    private fun savedSmsContacts(): List<String> {
        val p = prefs()
        return listOf(
            p.getString("smsPhone1", "") ?: "",
            p.getString("smsPhone2", "") ?: "",
            p.getString("smsPhone3", "") ?: "",
            p.getString("smsPhone4", "") ?: ""
        ).map { normalizePhone(it) }.filter { it.isNotBlank() }.distinct()
    }

    @Suppress("DEPRECATION")
    private fun smsManagerForDefaultSim(): SmsManager? {
        val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            status.text = "Elegí una SIM predeterminada para SMS en Ajustes."
            return null
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
    }

    private fun sendSmsToSavedContacts(message: String) {
        val smsManager = smsManagerForDefaultSim() ?: return
        val contacts = savedSmsContacts()
        if (contacts.isEmpty()) {
            status.text = "No hay contactos SMS configurados."
            return
        }

        try {
            currentSmsBatch = System.currentTimeMillis()
            expectedSmsParts = 0
            sentSmsParts = 0
            deliveredSmsParts = 0
            failedSmsParts = 0

            val allParts = contacts.map { destination ->
                destination to smsManager.divideMessage(message)
            }
            expectedSmsParts = allParts.sumOf { it.second.size }

            var requestCode = (currentSmsBatch xor (currentSmsBatch ushr 32)).toInt()

            allParts.forEach { (destination, parts) ->
                val sentIntents = ArrayList<PendingIntent>()
                val deliveryIntents = ArrayList<PendingIntent>()

                for (i in parts.indices) {
                    val sentIntent = Intent(ACTION_SMS_SENT)
                        .setPackage(packageName)
                        .putExtra(EXTRA_BATCH, currentSmsBatch)

                    val deliveredIntent = Intent(ACTION_SMS_DELIVERED)
                        .setPackage(packageName)
                        .putExtra(EXTRA_BATCH, currentSmsBatch)

                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

                    sentIntents.add(
                        PendingIntent.getBroadcast(
                            this,
                            requestCode++,
                            sentIntent,
                            flags
                        )
                    )
                    deliveryIntents.add(
                        PendingIntent.getBroadcast(
                            this,
                            requestCode++,
                            deliveredIntent,
                            flags
                        )
                    )
                }

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
            }

            status.text = "Alertando a ${contacts.size} contacto(s)..."
        } catch (e: Exception) {
            status.text = "No se pudieron enviar los SMS."
        }
    }

    private fun normalizePhone(raw: String): String {
        val value = raw.trim()
        return value.filterIndexed { index, c ->
            c.isDigit() || (c == '+' && index == 0)
        }
    }

    private fun makeEmergencyCall() {
        val phone = normalizePhone(prefs().getString("emergencyPhone", "") ?: "")
        if (phone.isEmpty()) return

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNeededPermissions()
            return
        }

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
                    callback("https://maps.google.com/?q=${loc.latitude},${loc.longitude}")
                } else {
                    client.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            callback("https://maps.google.com/?q=${last.latitude},${last.longitude}")
                        } else {
                            callback("Ubicacion no disponible")
                        }
                    }
                }
            }
            .addOnFailureListener {
                client.lastLocation.addOnSuccessListener { last ->
                    if (last != null) {
                        callback("https://maps.google.com/?q=${last.latitude},${last.longitude}")
                    } else {
                        callback("Ubicacion no disponible")
                    }
                }
            }
    }
}
