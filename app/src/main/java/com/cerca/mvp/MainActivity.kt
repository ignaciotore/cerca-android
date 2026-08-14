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
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {

    companion object {
        private const val ACTION_SMS_SENT = "com.cerca.mvp.SMS_SENT"
        private const val ACTION_SMS_DELIVERED = "com.cerca.mvp.SMS_DELIVERED"
        private const val EXTRA_BATCH = "batch"

        private const val PREFS = "cerca"
        private const val TRIAL_DAYS = 30L
        private const val DAY_MS = 24L * 60L * 60L * 1000L

        private const val TRACKER_URL = "https://cerca-live-tracker.vercel.app/track"
        private const val SUBSCRIBE_URL = "https://cerca-live-tracker.vercel.app/subscribe"
    }

    private val permissionRequestCode = 99
    private val notificationPermissionRequestCode = 100

    private lateinit var registerPanel: LinearLayout
    private lateinit var mainPanel: LinearLayout
    private lateinit var profilePanel: LinearLayout
    private lateinit var expiredPanel: LinearLayout

    private lateinit var registerTitle: TextView
    private lateinit var registerSubtitle: TextView
    private lateinit var registerButton: Button
    private lateinit var cancelEditButton: Button

    private lateinit var name: EditText
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var ownPhone: EditText
    private lateinit var emergencyPhone: EditText
    private lateinit var smsPhone1: EditText
    private lateinit var smsPhone2: EditText
    private lateinit var smsPhone3: EditText
    private lateinit var smsPhone4: EditText

    private lateinit var trialBadge: TextView
    private lateinit var status: TextView
    private lateinit var stopTrackingButton: Button

    private lateinit var profileData: TextView
    private lateinit var subscriptionStatus: TextView

    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdTriggered = false
    private var editingProfile = false

    private var currentSmsBatch = -1L
    private var expectedSmsParts = 0
    private var sentSmsParts = 0
    private var deliveredSmsParts = 0
    private var failedSmsParts = 0

    private val smsSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getLongExtra(EXTRA_BATCH, -1L) != currentSmsBatch) return

            if (resultCode == Activity.RESULT_OK) sentSmsParts++ else failedSmsParts++

            if (sentSmsParts + failedSmsParts >= expectedSmsParts && expectedSmsParts > 0) {
                status.text = if (failedSmsParts == 0) {
                    "Alerta enviada. La ubicación en vivo está activa."
                } else {
                    "La emergencia se activó, pero hubo $failedSmsParts error(es) de SMS."
                }
            }
        }
    }

    private val smsDeliveredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getLongExtra(EXTRA_BATCH, -1L) != currentSmsBatch) return

            if (resultCode == Activity.RESULT_OK) {
                deliveredSmsParts++
                if (deliveredSmsParts >= expectedSmsParts && failedSmsParts == 0 && expectedSmsParts > 0) {
                    status.text = "Alerta entregada. Ubicación en vivo activa."
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()

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

        migrateLegacyValues()
        requestNeededPermissions()
        handleSubscriptionIntent(intent)
        setupActions()
        showCorrectInitialScreen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSubscriptionIntent(intent)
        showCorrectInitialScreen()
    }

    override fun onResume() {
        super.onResume()
        if (isRegistered()) {
            updateTrialUi()
            updateTrackingUi()
            if (!canUseEmergency() && currentVisiblePanel() == "main") {
                showExpired()
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(smsSentReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(smsDeliveredReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun bindViews() {
        registerPanel = findViewById(R.id.registerPanel)
        mainPanel = findViewById(R.id.mainPanel)
        profilePanel = findViewById(R.id.profilePanel)
        expiredPanel = findViewById(R.id.expiredPanel)

        registerTitle = findViewById(R.id.registerTitle)
        registerSubtitle = findViewById(R.id.registerSubtitle)
        registerButton = findViewById(R.id.registerButton)
        cancelEditButton = findViewById(R.id.cancelEditButton)

        name = findViewById(R.id.name)
        email = findViewById(R.id.email)
        password = findViewById(R.id.password)
        ownPhone = findViewById(R.id.ownPhone)
        emergencyPhone = findViewById(R.id.emergencyPhone)
        smsPhone1 = findViewById(R.id.smsPhone1)
        smsPhone2 = findViewById(R.id.smsPhone2)
        smsPhone3 = findViewById(R.id.smsPhone3)
        smsPhone4 = findViewById(R.id.smsPhone4)

        trialBadge = findViewById(R.id.trialBadge)
        status = findViewById(R.id.status)
        stopTrackingButton = findViewById(R.id.stopTrackingButton)

        profileData = findViewById(R.id.profileData)
        subscriptionStatus = findViewById(R.id.subscriptionStatus)
    }

    private fun setupActions() {
        registerButton.setOnClickListener {
            if (saveRegistrationOrProfile()) {
                if (editingProfile) {
                    editingProfile = false
                    showProfile()
                } else {
                    showMain()
                }
            }
        }

        cancelEditButton.setOnClickListener {
            editingProfile = false
            showProfile()
        }

        findViewById<Button>(R.id.profileButton).setOnClickListener {
            showProfile()
        }

        findViewById<Button>(R.id.editProfileButton).setOnClickListener {
            showRegistration(editing = true)
        }

        findViewById<Button>(R.id.profileBackButton).setOnClickListener {
            if (canUseEmergency()) showMain() else showExpired()
        }

        findViewById<Button>(R.id.mercadoPagoButton).setOnClickListener {
            openSubscription("mercadopago")
        }

        findViewById<Button>(R.id.cardButton).setOnClickListener {
            openSubscription("card")
        }

        findViewById<Button>(R.id.expiredSubscribeButton).setOnClickListener {
            openSubscription("mercadopago")
        }

        findViewById<Button>(R.id.expiredProfileButton).setOnClickListener {
            showProfile()
        }

        stopTrackingButton.setOnClickListener {
            stopLiveTracking()
        }

        val helpButton = findViewById<Button>(R.id.helpButton)
        helpButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!canUseEmergency()) {
                        showExpired()
                        return@setOnTouchListener true
                    }

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
    }

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun migrateLegacyValues() {
        val p = prefs()
        val editor = p.edit()

        if (p.getString("smsPhone1", "").isNullOrBlank()) {
            val oldSms = p.getString("smsPhone", "") ?: ""
            if (oldSms.isNotBlank()) editor.putString("smsPhone1", oldSms)
        }

        editor.apply()
    }

    private fun isRegistered(): Boolean = prefs().getBoolean("registered_v4", false)

    private fun isSubscribed(): Boolean = prefs().getBoolean("subscribed", false)

    private fun trialStart(): Long = prefs().getLong("trialStartMillis", 0L)

    private fun trialEnd(): Long {
        val start = trialStart()
        return if (start <= 0L) 0L else start + TRIAL_DAYS * DAY_MS
    }

    private fun isTrialValid(): Boolean {
        val end = trialEnd()
        return end > System.currentTimeMillis()
    }

    private fun canUseEmergency(): Boolean = isSubscribed() || isTrialValid()

    private fun daysRemaining(): Int {
        val diff = trialEnd() - System.currentTimeMillis()
        if (diff <= 0L) return 0
        return ceil(diff.toDouble() / DAY_MS.toDouble()).toInt()
    }

    private fun showCorrectInitialScreen() {
        if (!isRegistered()) {
            showRegistration(editing = false)
            return
        }

        if (canUseEmergency()) showMain() else showExpired()
    }

    private fun showOnly(panel: LinearLayout) {
        registerPanel.visibility = View.GONE
        mainPanel.visibility = View.GONE
        profilePanel.visibility = View.GONE
        expiredPanel.visibility = View.GONE
        panel.visibility = View.VISIBLE
    }

    private fun currentVisiblePanel(): String {
        return when {
            mainPanel.visibility == View.VISIBLE -> "main"
            profilePanel.visibility == View.VISIBLE -> "profile"
            expiredPanel.visibility == View.VISIBLE -> "expired"
            else -> "register"
        }
    }

    private fun showRegistration(editing: Boolean) {
        editingProfile = editing
        loadFormValues()

        if (editing) {
            registerTitle.text = "Editar mis datos"
            registerSubtitle.text = "Actualizá tu información y contactos."
            registerButton.text = "GUARDAR CAMBIOS"
            cancelEditButton.visibility = View.VISIBLE
            password.setText("")
            password.hint = "Nueva contraseña (opcional)"
        } else {
            registerTitle.text = "Crear tu cuenta"
            registerSubtitle.text = "Completá tus datos y activá 30 días gratis."
            registerButton.text = "CREAR CUENTA Y ACTIVAR 30 DÍAS"
            cancelEditButton.visibility = View.GONE
            password.hint = "Contraseña"
        }

        showOnly(registerPanel)
    }

    private fun loadFormValues() {
        val p = prefs()
        name.setText(p.getString("name", ""))
        email.setText(p.getString("email", ""))
        ownPhone.setText(p.getString("ownPhone", ""))
        emergencyPhone.setText(p.getString("emergencyPhone", ""))
        smsPhone1.setText(p.getString("smsPhone1", ""))
        smsPhone2.setText(p.getString("smsPhone2", ""))
        smsPhone3.setText(p.getString("smsPhone3", ""))
        smsPhone4.setText(p.getString("smsPhone4", ""))
        password.setText("")
    }

    private fun saveRegistrationOrProfile(): Boolean {
        val person = name.text.toString().trim()
        val userEmail = email.text.toString().trim()
        val own = normalizePhone(ownPhone.text.toString())
        val callPhone = normalizePhone(emergencyPhone.text.toString())
        val sms1 = normalizePhone(smsPhone1.text.toString())
        val pass = password.text.toString()

        if (person.isBlank()) {
            toast("Ingresá tu nombre")
            return false
        }
        if (!userEmail.contains("@") || !userEmail.contains(".")) {
            toast("Ingresá un email válido")
            return false
        }
        if (!editingProfile && pass.length < 4) {
            toast("La contraseña debe tener al menos 4 caracteres")
            return false
        }
        if (own.isBlank()) {
            toast("Ingresá tu teléfono")
            return false
        }
        if (callPhone.isBlank()) {
            toast("Ingresá el teléfono para llamada")
            return false
        }
        if (sms1.isBlank()) {
            toast("Ingresá al menos un contacto para SMS")
            return false
        }

        val p = prefs()
        val edit = p.edit()
            .putString("name", person)
            .putString("email", userEmail)
            .putString("ownPhone", own)
            .putString("emergencyPhone", callPhone)
            .putString("smsPhone1", sms1)
            .putString("smsPhone2", normalizePhone(smsPhone2.text.toString()))
            .putString("smsPhone3", normalizePhone(smsPhone3.text.toString()))
            .putString("smsPhone4", normalizePhone(smsPhone4.text.toString()))

        if (pass.isNotBlank()) {
            edit.putString("passwordHash", sha256(pass))
        }

        if (!editingProfile && !isRegistered()) {
            edit.putBoolean("registered_v4", true)
            edit.putLong("trialStartMillis", System.currentTimeMillis())
            edit.putBoolean("subscribed", false)
        }

        edit.apply()
        toast(if (editingProfile) "Datos actualizados" else "Cuenta creada. Tenés 30 días gratis.")
        return true
    }

    private fun showMain() {
        if (!canUseEmergency()) {
            showExpired()
            return
        }

        showOnly(mainPanel)
        updateTrialUi()
        updateTrackingUi()

        if (!prefs().getBoolean("trackingActive", false)) {
            status.text = "Mantené apretado 3 segundos para pedir ayuda."
        }
    }

    private fun updateTrialUi() {
        trialBadge.text = when {
            isSubscribed() -> "Suscripción activa"
            isTrialValid() -> "Prueba gratuita · ${daysRemaining()} día(s) restante(s)"
            else -> "Prueba finalizada"
        }
    }

    private fun showProfile() {
        val p = prefs()
        val contacts = savedSmsContacts()
        val contactsText = if (contacts.isEmpty()) "Sin contactos" else contacts.joinToString("\n")

        profileData.text = buildString {
            append("Nombre: ${p.getString("name", "")}\n")
            append("Email: ${p.getString("email", "")}\n")
            append("Tu teléfono: ${p.getString("ownPhone", "")}\n")
            append("Llamada de emergencia: ${p.getString("emergencyPhone", "")}\n\n")
            append("Contactos de alerta:\n$contactsText")
        }

        subscriptionStatus.text = when {
            isSubscribed() -> "Estado: SUSCRIPCIÓN ACTIVA"
            isTrialValid() -> "Estado: PRUEBA GRATUITA\nQuedan ${daysRemaining()} día(s). Al finalizar, el botón de emergencia quedará bloqueado hasta que te suscribas."
            else -> "Estado: PRUEBA FINALIZADA\nSuscribite para volver a utilizar CERCA."
        }

        showOnly(profilePanel)
    }

    private fun showExpired() {
        showOnly(expiredPanel)
    }

    private fun openSubscription(method: String) {
        val userEmail = Uri.encode(prefs().getString("email", "") ?: "")
        val url = "$SUBSCRIBE_URL?method=$method&email=$userEmail"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun handleSubscriptionIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "cerca" && data.host == "subscription-success") {
            prefs().edit().putBoolean("subscribed", true).apply()
            toast("Suscripción activada")
        }
    }

    private fun requestNeededPermissions() {
        val core = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val missing = core.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                permissionRequestCode
            )
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                notificationPermissionRequestCode
            )
        }
    }

    private fun hasCorePermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
            (
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            )
    }

    private fun triggerEmergency() {
        if (!canUseEmergency()) {
            showExpired()
            return
        }

        if (!hasCorePermissions()) {
            status.text = "CERCA necesita permisos de teléfono, SMS y ubicación."
            requestNeededPermissions()
            return
        }

        status.text = "Activando emergencia y ubicación en vivo..."

        val topic = "cerca_" + UUID.randomUUID().toString().replace("-", "")
        val person = prefs().getString("name", "")?.ifBlank { "La persona" } ?: "La persona"

        getLocation { location ->
            val encodedName = URLEncoder.encode(person, "UTF-8")
            val initial = if (location != null) {
                "&lat=${location.latitude}&lon=${location.longitude}"
            } else {
                ""
            }

            val liveLink = "$TRACKER_URL?t=$topic&name=$encodedName$initial"
            val message = "$person necesita ayuda. Seguí su ubicación en tiempo real: $liveLink"

            startLiveTracking(topic, person)
            sendSmsToSavedContacts(message)

            Handler(Looper.getMainLooper()).postDelayed({
                makeEmergencyCall()
            }, 700L)
        }
    }

    private fun startLiveTracking(topic: String, person: String) {
        prefs().edit()
            .putBoolean("trackingActive", true)
            .putString("trackingTopic", topic)
            .apply()

        val intent = Intent(this, LocationSharingService::class.java).apply {
            action = LocationSharingService.ACTION_START
            putExtra(LocationSharingService.EXTRA_TOPIC, topic)
            putExtra(LocationSharingService.EXTRA_NAME, person)
        }
        ContextCompat.startForegroundService(this, intent)
        updateTrackingUi()
    }

    private fun stopLiveTracking() {
        val intent = Intent(this, LocationSharingService::class.java).apply {
            action = LocationSharingService.ACTION_STOP
        }
        startService(intent)

        prefs().edit()
            .putBoolean("trackingActive", false)
            .remove("trackingTopic")
            .apply()

        status.text = "Ubicación en vivo finalizada."
        updateTrackingUi()
    }

    private fun updateTrackingUi() {
        val active = prefs().getBoolean("trackingActive", false)
        stopTrackingButton.visibility = if (active) View.VISIBLE else View.GONE
        if (active && mainPanel.visibility == View.VISIBLE) {
            status.text = "Emergencia activa · compartiendo ubicación en vivo."
        }
    }

    private fun savedSmsContacts(): List<String> {
        val p = prefs()
        return listOf(
            p.getString("smsPhone1", "") ?: "",
            p.getString("smsPhone2", "") ?: "",
            p.getString("smsPhone3", "") ?: "",
            p.getString("smsPhone4", "") ?: ""
        )
            .map { normalizePhone(it) }
            .filter { it.isNotBlank() }
            .distinct()
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
        val manager = smsManagerForDefaultSim() ?: return
        val contacts = savedSmsContacts()

        if (contacts.isEmpty()) {
            status.text = "No hay contactos de SMS configurados."
            return
        }

        try {
            currentSmsBatch = System.currentTimeMillis()
            expectedSmsParts = 0
            sentSmsParts = 0
            deliveredSmsParts = 0
            failedSmsParts = 0

            val allParts = contacts.map { destination ->
                destination to manager.divideMessage(message)
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
                    manager.sendTextMessage(
                        destination,
                        null,
                        parts[0],
                        sentIntents[0],
                        deliveryIntents[0]
                    )
                } else {
                    manager.sendMultipartTextMessage(
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

    private fun makeEmergencyCall() {
        val phone = normalizePhone(prefs().getString("emergencyPhone", "") ?: "")
        if (phone.isBlank()) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestNeededPermissions()
            return
        }

        startActivity(
            Intent(
                Intent.ACTION_CALL,
                Uri.fromParts("tel", phone, null)
            )
        )
    }

    private fun getLocation(callback: (android.location.Location?) -> Unit) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            callback(null)
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(this)
        val token = CancellationTokenSource()

        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    callback(loc)
                } else {
                    client.lastLocation
                        .addOnSuccessListener { callback(it) }
                        .addOnFailureListener { callback(null) }
                }
            }
            .addOnFailureListener {
                client.lastLocation
                    .addOnSuccessListener { callback(it) }
                    .addOnFailureListener { callback(null) }
            }
    }

    private fun normalizePhone(raw: String): String {
        val value = raw.trim()
        return value.filterIndexed { index, c ->
            c.isDigit() || (c == '+' && index == 0)
        }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
