package com.help.seguridad

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.security.MessageDigest
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "help_prefs"
        private const val TRIAL_DAYS = 30L
        private const val DAY_MS = 24L * 60L * 60L * 1000L

        private const val REQ_CALL_CONTACT = 201
        private const val REQ_SMS1_CONTACT = 202
        private const val REQ_SMS2_CONTACT = 203
        private const val REQ_SMS3_CONTACT = 204
        private const val REQ_SMS4_CONTACT = 205
        private const val REQ_PERMISSIONS = 301

        private const val ACTION_SMS_SENT = "com.help.seguridad.HELP_SMS_SENT"
        private const val ACTION_SMS_DELIVERED = "com.help.seguridad.HELP_SMS_DELIVERED"
        private const val EXTRA_BATCH = "batch"
    }

    private lateinit var setupPanel: LinearLayout
    private lateinit var homePanel: LinearLayout
    private lateinit var profilePanel: LinearLayout
    private lateinit var expiredPanel: LinearLayout

    private lateinit var setupTitle: TextView
    private lateinit var setupSubtitle: TextView
    private lateinit var name: EditText
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var ownPhone: EditText
    private lateinit var callPhoneManual: EditText

    private lateinit var callContactDisplay: TextView
    private lateinit var sms1Display: TextView
    private lateinit var sms2Display: TextView
    private lateinit var sms3Display: TextView
    private lateinit var sms4Display: TextView

    private lateinit var trialBadge: TextView
    private lateinit var status: TextView
    private lateinit var homeCallSummary: TextView
    private lateinit var homeSmsSummary: TextView
    private lateinit var profileData: TextView
    private lateinit var subscriptionStatus: TextView

    private lateinit var billingManager: BillingManager

    private var editingProfile = false

    private var callName = ""
    private var callPhone = ""
    private var sms1Name = ""
    private var sms1Phone = ""
    private var sms2Name = ""
    private var sms2Phone = ""
    private var sms3Name = ""
    private var sms3Phone = ""
    private var sms4Name = ""
    private var sms4Phone = ""

    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdTriggered = false

    private var currentSmsBatch = -1L
    private var expectedSmsParts = 0
    private var sentSmsParts = 0
    private var failedSmsParts = 0
    private var deliveredSmsParts = 0

    private val smsSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getLongExtra(EXTRA_BATCH, -1L) != currentSmsBatch) return
            if (resultCode == Activity.RESULT_OK) sentSmsParts++ else failedSmsParts++

            if (sentSmsParts + failedSmsParts >= expectedSmsParts && expectedSmsParts > 0) {
                status.text = if (failedSmsParts == 0) {
                    "Aviso enviado. Iniciando llamada…"
                } else {
                    "La llamada se realizará, pero hubo un problema con uno o más SMS."
                }
            }
        }
    }

    private val smsDeliveredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getLongExtra(EXTRA_BATCH, -1L) != currentSmsBatch) return
            if (resultCode == Activity.RESULT_OK) {
                deliveredSmsParts++
                if (deliveredSmsParts >= expectedSmsParts && expectedSmsParts > 0 && failedSmsParts == 0) {
                    status.text = "Tus contactos recibieron el aviso."
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        billingManager = BillingManager(
            activity = this,
            onEntitlementChanged = {
                runOnUiThread {
                    if (prefs().getBoolean("registered_v5", false)) {
                        if (canUseHelp()) showHome() else showExpired()
                    }
                }
            },
            onMessage = { message ->
                runOnUiThread { toast(message) }
            }
        )
        billingManager.start()
        migrateLegacyData()
        loadContactState()
        registerSmsReceivers()
        setupActions()
        showInitialScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::billingManager.isInitialized) billingManager.refreshPurchases()
    }

    override fun onDestroy() {
        if (::billingManager.isInitialized) billingManager.close()
        try { unregisterReceiver(smsSentReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(smsDeliveredReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun bindViews() {
        setupPanel = findViewById(R.id.setupPanel)
        homePanel = findViewById(R.id.homePanel)
        profilePanel = findViewById(R.id.profilePanel)
        expiredPanel = findViewById(R.id.expiredPanel)

        setupTitle = findViewById(R.id.setupTitle)
        setupSubtitle = findViewById(R.id.setupSubtitle)
        name = findViewById(R.id.name)
        email = findViewById(R.id.email)
        password = findViewById(R.id.password)
        ownPhone = findViewById(R.id.ownPhone)
        callPhoneManual = findViewById(R.id.callPhoneManual)

        callContactDisplay = findViewById(R.id.callContactDisplay)
        sms1Display = findViewById(R.id.sms1Display)
        sms2Display = findViewById(R.id.sms2Display)
        sms3Display = findViewById(R.id.sms3Display)
        sms4Display = findViewById(R.id.sms4Display)

        trialBadge = findViewById(R.id.trialBadge)
        status = findViewById(R.id.status)
        homeCallSummary = findViewById(R.id.homeCallSummary)
        homeSmsSummary = findViewById(R.id.homeSmsSummary)
        profileData = findViewById(R.id.profileData)
        subscriptionStatus = findViewById(R.id.subscriptionStatus)
    }

    private fun registerSmsReceivers() {
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
    }

    private fun setupActions() {
        findViewById<Button>(R.id.pickCallContactButton).setOnClickListener {
            openPhoneContactPicker(REQ_CALL_CONTACT)
        }
        findViewById<Button>(R.id.pickSms1Button).setOnClickListener {
            openPhoneContactPicker(REQ_SMS1_CONTACT)
        }
        findViewById<Button>(R.id.pickSms2Button).setOnClickListener {
            openPhoneContactPicker(REQ_SMS2_CONTACT)
        }
        findViewById<Button>(R.id.pickSms3Button).setOnClickListener {
            openPhoneContactPicker(REQ_SMS3_CONTACT)
        }
        findViewById<Button>(R.id.pickSms4Button).setOnClickListener {
            openPhoneContactPicker(REQ_SMS4_CONTACT)
        }

        findViewById<Button>(R.id.saveSetupButton).setOnClickListener {
            if (saveProfile()) {
                editingProfile = false
                showPermissionDisclosureIfNeeded()
                showHome()
            }
        }

        findViewById<Button>(R.id.cancelEditButton).setOnClickListener {
            editingProfile = false
            loadContactState()
            showProfile()
        }

        findViewById<Button>(R.id.profileButton).setOnClickListener {
            showProfile()
        }

        findViewById<Button>(R.id.editProfileButton).setOnClickListener {
            showSetup(editing = true)
        }

        findViewById<Button>(R.id.profileBackButton).setOnClickListener {
            if (canUseHelp()) showHome() else showExpired()
        }

        findViewById<Button>(R.id.subscribeButton).setOnClickListener {
            billingManager.launchSubscription()
        }

        findViewById<Button>(R.id.expiredSubscribeButton).setOnClickListener {
            billingManager.launchSubscription()
        }

        findViewById<Button>(R.id.expiredProfileButton).setOnClickListener {
            showProfile()
        }

        val helpButton = findViewById<Button>(R.id.helpButton)
        helpButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!canUseHelp()) {
                        showExpired()
                        return@setOnTouchListener true
                    }
                    holdTriggered = false
                    status.text = "Seguí apretando…"
                    holdHandler.postDelayed({
                        holdTriggered = true
                        triggerHelp()
                    }, 3000L)
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

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun migrateLegacyData() {
        val legacy = getSharedPreferences("cerca", MODE_PRIVATE)
        val p = prefs()

        if (p.getBoolean("migration_done", false)) return

        val editor = p.edit()
        val oldName = legacy.getString("name", "") ?: ""
        val oldEmail = legacy.getString("email", "") ?: ""
        val oldOwn = legacy.getString("ownPhone", "") ?: ""
        val oldCall = legacy.getString("emergencyPhone", "") ?: ""
        val oldSms1 = legacy.getString("smsPhone1", legacy.getString("smsPhone", "")) ?: ""
        val oldSms2 = legacy.getString("smsPhone2", "") ?: ""
        val oldSms3 = legacy.getString("smsPhone3", "") ?: ""
        val oldSms4 = legacy.getString("smsPhone4", "") ?: ""

        if (oldName.isNotBlank()) editor.putString("name", oldName)
        if (oldEmail.isNotBlank()) editor.putString("email", oldEmail)
        if (oldOwn.isNotBlank()) editor.putString("ownPhone", oldOwn)
        if (oldCall.isNotBlank()) {
            editor.putString("callPhone", normalizePhone(oldCall))
            editor.putString("callName", "Contacto guardado")
        }
        if (oldSms1.isNotBlank()) {
            editor.putString("sms1Phone", normalizePhone(oldSms1))
            editor.putString("sms1Name", "Contacto guardado")
        }
        if (oldSms2.isNotBlank()) {
            editor.putString("sms2Phone", normalizePhone(oldSms2))
            editor.putString("sms2Name", "Contacto guardado")
        }
        if (oldSms3.isNotBlank()) {
            editor.putString("sms3Phone", normalizePhone(oldSms3))
            editor.putString("sms3Name", "Contacto guardado")
        }
        if (oldSms4.isNotBlank()) {
            editor.putString("sms4Phone", normalizePhone(oldSms4))
            editor.putString("sms4Name", "Contacto guardado")
        }

        editor.putBoolean("migration_done", true).apply()
    }

    private fun loadContactState() {
        val p = prefs()

        callName = p.getString("callName", "") ?: ""
        callPhone = p.getString("callPhone", "") ?: ""
        sms1Name = p.getString("sms1Name", "") ?: ""
        sms1Phone = p.getString("sms1Phone", "") ?: ""
        sms2Name = p.getString("sms2Name", "") ?: ""
        sms2Phone = p.getString("sms2Phone", "") ?: ""
        sms3Name = p.getString("sms3Name", "") ?: ""
        sms3Phone = p.getString("sms3Phone", "") ?: ""
        sms4Name = p.getString("sms4Name", "") ?: ""
        sms4Phone = p.getString("sms4Phone", "") ?: ""

        updateContactDisplays()
    }

    private fun updateContactDisplays() {
        callContactDisplay.text = contactLabel(
            callName,
            callPhone,
            "Todavía no elegiste un contacto"
        )
        sms1Display.text = contactLabel(sms1Name, sms1Phone, "Contacto 1 · Sin elegir")
        sms2Display.text = contactLabel(sms2Name, sms2Phone, "Contacto 2 · Opcional")
        sms3Display.text = contactLabel(sms3Name, sms3Phone, "Contacto 3 · Opcional")
        sms4Display.text = contactLabel(sms4Name, sms4Phone, "Contacto 4 · Opcional")

        if (callPhone.isNotBlank()) callPhoneManual.setText(callPhone)
    }

    private fun contactLabel(contactName: String, phone: String, emptyText: String): String {
        return if (phone.isBlank()) {
            emptyText
        } else {
            val displayName = contactName.ifBlank { "Contacto" }
            "$displayName\n$phone"
        }
    }

    private fun openPhoneContactPicker(requestCode: Int) {
        try {
            val intent = Intent(
                Intent.ACTION_PICK,
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            )
            startActivityForResult(intent, requestCode)
        } catch (_: Exception) {
            toast("No pude abrir tus contactos.")
        }
    }

    @Deprecated("Deprecated in Android API, retained for compatibility with this MVP")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return

        var pickedName = ""
        var pickedPhone = ""

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )
                val numberIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )

                if (nameIndex >= 0) pickedName = cursor.getString(nameIndex) ?: ""
                if (numberIndex >= 0) pickedPhone = normalizePhone(cursor.getString(numberIndex) ?: "")
            }
        } finally {
            cursor?.close()
        }

        if (pickedPhone.isBlank()) {
            toast("Ese contacto no tiene un teléfono disponible.")
            return
        }

        when (requestCode) {
            REQ_CALL_CONTACT -> {
                callName = pickedName
                callPhone = pickedPhone
                callPhoneManual.setText(pickedPhone)
            }
            REQ_SMS1_CONTACT -> {
                sms1Name = pickedName
                sms1Phone = pickedPhone
            }
            REQ_SMS2_CONTACT -> {
                sms2Name = pickedName
                sms2Phone = pickedPhone
            }
            REQ_SMS3_CONTACT -> {
                sms3Name = pickedName
                sms3Phone = pickedPhone
            }
            REQ_SMS4_CONTACT -> {
                sms4Name = pickedName
                sms4Phone = pickedPhone
            }
        }

        updateContactDisplays()
    }

    private fun showInitialScreen() {
        if (!prefs().getBoolean("registered_v5", false)) {
            showSetup(editing = false)
        } else if (canUseHelp()) {
            showHome()
        } else {
            showExpired()
        }
    }

    private fun showOnly(panel: View) {
        setupPanel.visibility = View.GONE
        homePanel.visibility = View.GONE
        profilePanel.visibility = View.GONE
        expiredPanel.visibility = View.GONE
        panel.visibility = View.VISIBLE
    }

    private fun showSetup(editing: Boolean) {
        editingProfile = editing
        val p = prefs()

        name.setText(p.getString("name", ""))
        email.setText(p.getString("email", ""))
        ownPhone.setText(p.getString("ownPhone", ""))
        password.setText("")
        callPhoneManual.setText(callPhone)
        updateContactDisplays()

        if (editing) {
            setupTitle.text = "Editar perfil y contactos"
            setupSubtitle.text = "Actualizá tu información. Los cambios se guardan al continuar."
            password.hint = "Nueva contraseña (opcional)"
            findViewById<Button>(R.id.saveSetupButton).text = "GUARDAR CAMBIOS"
            findViewById<Button>(R.id.cancelEditButton).visibility = View.VISIBLE
        } else {
            setupTitle.text = "Tu red de ayuda, siempre cerca"
            setupSubtitle.text = "Configurá una vez tus datos y contactos. Después, pedir ayuda es simple."
            password.hint = "Contraseña"
            findViewById<Button>(R.id.saveSetupButton).text = "GUARDAR Y CONTINUAR"
            findViewById<Button>(R.id.cancelEditButton).visibility = View.GONE
        }

        showOnly(setupPanel)
    }

    private fun saveProfile(): Boolean {
        val personName = name.text.toString().trim()
        val userEmail = email.text.toString().trim()
        val userPhone = normalizePhone(ownPhone.text.toString())
        val typedCallPhone = normalizePhone(callPhoneManual.text.toString())
        val pass = password.text.toString()

        if (personName.isBlank()) {
            toast("Ingresá tu nombre.")
            return false
        }
        if (!userEmail.contains("@") || !userEmail.contains(".")) {
            toast("Ingresá un email válido.")
            return false
        }
        if (!editingProfile && pass.length < 4) {
            toast("La contraseña debe tener al menos 4 caracteres.")
            return false
        }
        if (userPhone.isBlank()) {
            toast("Ingresá tu teléfono.")
            return false
        }

        if (typedCallPhone.isNotBlank() && typedCallPhone != callPhone) {
            callPhone = typedCallPhone
            callName = "Número manual"
        }

        if (callPhone.isBlank()) {
            toast("Elegí el contacto para la llamada de ayuda.")
            return false
        }
        if (sms1Phone.isBlank()) {
            toast("Elegí al menos un contacto para recibir el SMS.")
            return false
        }

        val p = prefs()
        val editor = p.edit()
            .putString("name", personName)
            .putString("email", userEmail)
            .putString("ownPhone", userPhone)
            .putString("callName", callName)
            .putString("callPhone", callPhone)
            .putString("sms1Name", sms1Name)
            .putString("sms1Phone", sms1Phone)
            .putString("sms2Name", sms2Name)
            .putString("sms2Phone", sms2Phone)
            .putString("sms3Name", sms3Name)
            .putString("sms3Phone", sms3Phone)
            .putString("sms4Name", sms4Name)
            .putString("sms4Phone", sms4Phone)

        if (pass.isNotBlank()) {
            editor.putString("passwordHash", sha256(pass))
        }

        if (!p.getBoolean("registered_v5", false)) {
            editor
                .putBoolean("registered_v5", true)
                .putLong("trialStartMillis", System.currentTimeMillis())
        }

        editor.apply()
        toast(if (editingProfile) "Datos actualizados." else "Listo. H.E.L.P quedó configurada.")
        return true
    }

    private fun showHome() {
        if (!canUseHelp()) {
            showExpired()
            return
        }

        loadContactState()
        val contacts = savedSmsContacts()

        trialBadge.text = if (isSubscriptionActive()) {
            "Suscripción activa"
        } else {
            "Prueba gratuita · ${daysRemaining()} día(s)"
        }

        homeCallSummary.text = "Llamada: ${callName.ifBlank { "Contacto" }} · $callPhone"
        homeSmsSummary.text = "Avisos por SMS: ${contacts.size} contacto(s)"
        status.text = "Mantené apretado 3 segundos para pedir ayuda."

        showOnly(homePanel)
    }

    private fun showProfile() {
        loadContactState()

        val p = prefs()
        val contacts = listOf(
            sms1Name to sms1Phone,
            sms2Name to sms2Phone,
            sms3Name to sms3Phone,
            sms4Name to sms4Phone
        ).filter { it.second.isNotBlank() }

        profileData.text = buildString {
            append("Nombre\n${p.getString("name", "")}\n\n")
            append("Email\n${p.getString("email", "")}\n\n")
            append("Tu teléfono\n${p.getString("ownPhone", "")}\n\n")
            append("Llamada de ayuda\n${callName.ifBlank { "Contacto" }} · $callPhone\n\n")
            append("Contactos SMS\n")
            contacts.forEachIndexed { index, pair ->
                append("${index + 1}. ${pair.first.ifBlank { "Contacto" }} · ${pair.second}")
                if (index < contacts.lastIndex) append("\n")
            }
        }

        subscriptionStatus.text = when {
            isSubscriptionActive() -> "Estado: activa."
            isTrialValid() -> "Prueba gratuita activa. Te quedan ${daysRemaining()} día(s)."
            else -> "Tu prueba gratuita terminó. Necesitás una suscripción para utilizar el botón de ayuda."
        }

        showOnly(profilePanel)
    }

    private fun showExpired() {
        showOnly(expiredPanel)
    }

    private fun showSubscriptionPendingMessage() {
        toast("La suscripción se conectará con Google Play Billing al crear el producto en Play Console.")
    }

    private fun isSubscriptionActive(): Boolean =
        prefs().getBoolean("subscriptionActive", false)

    private fun trialStart(): Long = prefs().getLong("trialStartMillis", 0L)

    private fun isTrialValid(): Boolean {
        val start = trialStart()
        if (start <= 0L) return false
        return System.currentTimeMillis() < start + TRIAL_DAYS * DAY_MS
    }

    private fun daysRemaining(): Int {
        val end = trialStart() + TRIAL_DAYS * DAY_MS
        val diff = end - System.currentTimeMillis()
        if (diff <= 0L) return 0
        return ceil(diff.toDouble() / DAY_MS.toDouble()).toInt()
    }

    private fun canUseHelp(): Boolean = isSubscriptionActive() || isTrialValid()

    private fun showPermissionDisclosureIfNeeded() {
        if (hasEmergencyPermissions()) return

        AlertDialog.Builder(this)
            .setTitle("Permisos necesarios para pedir ayuda")
            .setMessage(
                "H.E.L.P usa el permiso de teléfono para llamar al contacto que elegiste, " +
                "el permiso de SMS para enviar el aviso de emergencia a tus contactos y " +
                "la ubicación únicamente cuando activás PEDIR AYUDA, para incluir un enlace puntual de Google Maps. " +
                "No realiza seguimiento de ubicación en tiempo real ni lee tus mensajes."
            )
            .setNegativeButton("AHORA NO", null)
            .setPositiveButton("CONTINUAR") { _, _ -> requestEmergencyPermissionsIfNeeded() }
            .show()
    }

    private fun requestEmergencyPermissionsIfNeeded() {
        val permissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                REQ_PERMISSIONS
            )
        }
    }

    private fun hasEmergencyPermissions(): Boolean {
        val phone = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val sms = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return phone && sms && (fine || coarse)
    }

    private fun triggerHelp() {
        if (!hasEmergencyPermissions()) {
            status.text = "Necesito permisos de teléfono, SMS y ubicación."
            showPermissionDisclosureIfNeeded()
            return
        }

        status.text = "Obteniendo tu ubicación…"

        getCurrentLocation { latitude, longitude ->
            val personName = prefs().getString("name", "")?.ifBlank { "Una persona" } ?: "Una persona"
            val mapsLink = if (latitude != null && longitude != null) {
                "https://maps.google.com/?q=$latitude,$longitude"
            } else {
                "Ubicación no disponible"
            }

            val message = "H.E.L.P · $personName necesita ayuda. Ubicación: $mapsLink"
            sendSmsToContacts(message)

            Handler(Looper.getMainLooper()).postDelayed({
                makeDirectCall()
            }, 900L)
        }
    }

    private fun getCurrentLocation(callback: (Double?, Double?) -> Unit) {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (fine != PackageManager.PERMISSION_GRANTED &&
            coarse != PackageManager.PERMISSION_GRANTED
        ) {
            callback(null, null)
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(this)
        val token = CancellationTokenSource()

        client.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            token.token
        )
            .addOnSuccessListener { location ->
                if (location != null) {
                    callback(location.latitude, location.longitude)
                } else {
                    client.lastLocation
                        .addOnSuccessListener { last ->
                            callback(last?.latitude, last?.longitude)
                        }
                        .addOnFailureListener {
                            callback(null, null)
                        }
                }
            }
            .addOnFailureListener {
                client.lastLocation
                    .addOnSuccessListener { last ->
                        callback(last?.latitude, last?.longitude)
                    }
                    .addOnFailureListener {
                        callback(null, null)
                    }
            }
    }

    private fun savedSmsContacts(): List<Pair<String, String>> {
        return listOf(
            sms1Name to sms1Phone,
            sms2Name to sms2Phone,
            sms3Name to sms3Phone,
            sms4Name to sms4Phone
        )
            .filter { it.second.isNotBlank() }
            .distinctBy { it.second }
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

    private fun sendSmsToContacts(message: String) {
        val manager = smsManagerForDefaultSim() ?: return
        val contacts = savedSmsContacts()

        if (contacts.isEmpty()) {
            status.text = "No hay contactos configurados para SMS."
            return
        }

        try {
            currentSmsBatch = System.currentTimeMillis()
            expectedSmsParts = 0
            sentSmsParts = 0
            failedSmsParts = 0
            deliveredSmsParts = 0

            val allParts = contacts.map { pair ->
                pair.second to manager.divideMessage(message)
            }

            expectedSmsParts = allParts.sumOf { it.second.size }
            var requestCode = (
                currentSmsBatch xor (currentSmsBatch ushr 32)
            ).toInt()

            allParts.forEach { (destination, parts) ->
                val sentIntents = ArrayList<PendingIntent>()
                val deliveredIntents = ArrayList<PendingIntent>()

                parts.indices.forEach {
                    val sentIntent = Intent(ACTION_SMS_SENT)
                        .setPackage(packageName)
                        .putExtra(EXTRA_BATCH, currentSmsBatch)

                    val deliveredIntent = Intent(ACTION_SMS_DELIVERED)
                        .setPackage(packageName)
                        .putExtra(EXTRA_BATCH, currentSmsBatch)

                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE

                    sentIntents.add(
                        PendingIntent.getBroadcast(
                            this,
                            requestCode++,
                            sentIntent,
                            flags
                        )
                    )

                    deliveredIntents.add(
                        PendingIntent.getBroadcast(
                            this,
                            requestCode++,
                            deliveredIntent,
                            flags
                        )
                    )
                }

                if (parts.size == 1) {
                    manager.sendTextMessage(
                        destination,
                        null,
                        parts[0],
                        sentIntents[0],
                        deliveredIntents[0]
                    )
                } else {
                    manager.sendMultipartTextMessage(
                        destination,
                        null,
                        parts,
                        sentIntents,
                        deliveredIntents
                    )
                }
            }

            status.text = "Enviando aviso a ${contacts.size} contacto(s)…"
        } catch (e: Exception) {
            status.text = "No pude enviar el SMS. La llamada se realizará igual."
        }
    }

    private fun makeDirectCall() {
        val phone = normalizePhone(callPhone)
        if (phone.isBlank()) {
            status.text = "No hay un número configurado para llamada."
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestEmergencyPermissionsIfNeeded()
            return
        }

        try {
            status.text = "Llamando a ${callName.ifBlank { "tu contacto" }}…"
            val intent = Intent(
                Intent.ACTION_CALL,
                Uri.fromParts("tel", phone, null)
            )
            startActivity(intent)
        } catch (_: Exception) {
            status.text = "No pude iniciar la llamada."
        }
    }

    private fun normalizePhone(raw: String): String {
        val value = raw.trim()
        return value.filterIndexed { index, char ->
            char.isDigit() || (char == '+' && index == 0)
        }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())

        return bytes.joinToString("") {
            "%02x".format(it)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
