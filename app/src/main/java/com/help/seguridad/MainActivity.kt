package com.help.seguridad

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
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
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
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
import java.time.Instant
import java.text.Normalizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {

    companion object {
        private const val APP_PREFS = "help_prefs"
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val LOCATION_TIMEOUT_MS = 3500L
        private const val SUBSCRIPTION_MANAGEMENT_URL =
            "https://play.google.com/store/account/subscriptions?sku=help_monthly&package=com.help.seguridad"

        private const val REQ_CALL_CONTACT = 201
        private const val REQ_SMS1_CONTACT = 202
        private const val REQ_SMS2_CONTACT = 203
        private const val REQ_SMS3_CONTACT = 204
        private const val REQ_SMS4_CONTACT = 205
        private const val REQ_PERMISSIONS = 301
        private const val REQ_SMS_PERMISSION = 303
        private const val REQ_OTHER_PERMISSIONS = 304

        private const val ACTION_SMS_SENT = "com.help.seguridad.HELP_SMS_SENT"
        private const val ACTION_SMS_DELIVERED = "com.help.seguridad.HELP_SMS_DELIVERED"
        private const val EXTRA_BATCH = "batch"
        const val EXTRA_AUTO_TRIGGER_HELP = "com.help.seguridad.AUTO_TRIGGER_HELP"
    }

    private lateinit var loadingPanel: LinearLayout
    private lateinit var authPanel: LinearLayout
    private lateinit var signupPanel: LinearLayout
    private lateinit var setupPanel: LinearLayout
    private lateinit var homePanel: LinearLayout
    private lateinit var profilePanel: LinearLayout
    private lateinit var expiredPanel: LinearLayout
    private lateinit var loadingText: TextView

    private lateinit var loginEmail: EditText
    private lateinit var loginPassword: EditText
    private lateinit var signupName: EditText
    private lateinit var signupEmail: EditText
    private lateinit var signupPhone: EditText
    private lateinit var signupPassword: EditText
    private lateinit var signupPassword2: EditText

    private lateinit var setupTitle: TextView
    private lateinit var setupSubtitle: TextView
    private lateinit var name: EditText
    private lateinit var callPhoneManual: EditText
    private lateinit var callContactDisplay: TextView
    private lateinit var sms1Display: TextView
    private lateinit var sms2Display: TextView
    private lateinit var sms3Display: TextView
    private lateinit var sms4Display: TextView
    private lateinit var sms1Medical: CheckBox
    private lateinit var sms2Medical: CheckBox
    private lateinit var sms3Medical: CheckBox
    private lateinit var sms4Medical: CheckBox

    private lateinit var trialBadge: TextView
    private lateinit var status: TextView
    private lateinit var homeCallSummary: TextView
    private lateinit var homeSmsSummary: TextView
    private lateinit var profileData: TextView
    private lateinit var subscriptionStatus: TextView
    private lateinit var familyHomeButton: Button
    private lateinit var planTestCard: LinearLayout
    private lateinit var planTestStatus: TextView
    private lateinit var silentHelpButton: Button
    private lateinit var activeEmergencyButton: Button

    private lateinit var billingManager: BillingManager
    private val api = SupabaseApi()
    private lateinit var sessionStore: SecureSessionStore
    private lateinit var activationQueue: ActivationQueue
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var currentSession: SupabaseApi.Session? = null
    private var editingProfile = false
    private var emergencyInProgress = false
    private var emergencyCallPending = false

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
    private val locationHandler = Handler(Looper.getMainLooper())
    private var holdTriggered = false

    private var currentSmsBatch = -1L
    private var expectedSmsParts = 0
    private var sentSmsParts = 0
    private var failedSmsParts = 0
    private var deliveredSmsParts = 0

    private var pendingUpdateDownloadId = -1L
    private var pendingUpdateUrl: String? = null
    private var pendingUpdateName: String? = null

    private val updateDownloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id <= 0L || id != pendingUpdateDownloadId) return
            pendingUpdateDownloadId = -1L
            try {
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                val cursor = dm.query(DownloadManager.Query().setFilterById(id))
                val ok = cursor.use {
                    it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                }
                if (!ok) { toast("No se pudo descargar la actualización. Intentá nuevamente."); return }
                val apkUri = dm.getUriForDownloadedFile(id)
                if (apkUri == null) { toast("No pude abrir la actualización."); return }
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) { toast("No pude abrir la actualización.") }
        }
    }

    private val smsSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getLongExtra(EXTRA_BATCH, -1L) != currentSmsBatch) return
            if (resultCode == Activity.RESULT_OK) {
                sentSmsParts++
                appPrefs().edit().putString("last_sms_diag", "ENVIADO $sentSmsParts/$expectedSmsParts").apply()
            } else {
                failedSmsParts++
                val modemCode = intent?.getIntExtra("errorCode", -1) ?: -1
                val noDefault = intent?.getBooleanExtra("noDefault", false) ?: false
                status.text = "SMS rechazado. Código: $resultCode · módem: $modemCode · noDefault: $noDefault"
                appPrefs().edit().putString("last_sms_diag", status.text.toString()).apply()
                toast(status.text.toString())
            }
            if (sentSmsParts + failedSmsParts >= expectedSmsParts && expectedSmsParts > 0) {
                if (failedSmsParts == 0) {
                    status.text = if (emergencyCallPending) "SMS enviado. Iniciando llamada…" else "SMS enviado."
                    toast("SMS enviado correctamente.")
                } else {
                    status.text = "El SMS tuvo un problema. Iniciando llamada igual…"
                    toast("No pude confirmar el envío de uno o más SMS.")
                }
                Handler(Looper.getMainLooper()).postDelayed({ makeDirectCall() }, 450L)
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
                    appPrefs().edit().putString("last_sms_diag", "ENTREGADO $deliveredSmsParts/$expectedSmsParts").apply()
                    toast("SMS entregado al contacto.")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sessionStore = SecureSessionStore(this)
        activationQueue = ActivationQueue(this)
        bindViews()
        installAccountPhoneUi()
        installSmsMedicalOptions()
        installFamilyTestUi()
        registerSmsReceivers()
        ContextCompat.registerReceiver(
            this, updateDownloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
        setupActions()

        billingManager = BillingManager(
            activity = this,
            onEntitlementChanged = {
                runOnUiThread {
                    if (currentSession != null) {
                        if (isNetworkAccessCached()) routeAfterAuthentication() else showExpired()
                    }
                }
            },
            onPurchaseTokenAvailable = { token -> verifyPurchaseTokenAsync(token) },
            onMessage = { message -> runOnUiThread { toast(message) } }
        )
        billingManager.start()

        showLoading("Preparando tu red de ayuda…")
        val saved = sessionStore.load()
        if (saved == null) {
            showLogin()
        } else {
            currentSession = saved
            resumeSavedSession(saved)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_AUTO_TRIGGER_HELP, false) && currentSession != null) routeAfterAuthentication()
    }

    override fun onResume() {
        super.onResume()
        val waitingUrl = pendingUpdateUrl
        if (waitingUrl != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls())) {
            val waitingName = pendingUpdateName ?: "nueva"
            pendingUpdateUrl = null
            pendingUpdateName = null
            Handler(Looper.getMainLooper()).postDelayed({ startUpdateDownload(waitingUrl, waitingName) }, 250L)
        }
        checkForAppUpdate()
        if (::billingManager.isInitialized) billingManager.refreshPurchases()
        val session = currentSession
        if (session != null) {
            refreshAndSyncInBackground(session)
            registerPushIfAvailable(session)
            refreshNetworkEmergencyUiAsync(session)
        }
    }

    override fun onPause() {
        holdHandler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onDestroy() {
        holdHandler.removeCallbacksAndMessages(null)
        locationHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        if (::billingManager.isInitialized) billingManager.close()
        try { unregisterReceiver(smsSentReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(smsDeliveredReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(updateDownloadReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun checkForAppUpdate() {
        val prefs = appPrefs()
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_update_check_ms", 0L) < 60_000L) return
        prefs.edit().putLong("last_update_check_ms", now).apply()
        executor.execute {
            try {
                val url = java.net.URL(SupabaseApi.BASE_URL + "/functions/v1/cerca-app-version")
                val c = (url.openConnection() as java.net.HttpURLConnection).apply { requestMethod = "GET"; connectTimeout = 5000; readTimeout = 5000 }
                val body = c.inputStream.bufferedReader().use { it.readText() }; c.disconnect()
                val j = org.json.JSONObject(body)
                if (j.optInt("version_code", BuildConfig.VERSION_CODE) <= BuildConfig.VERSION_CODE) return@execute
                val name = j.optString("version_name", "nueva")
                val msg = j.optString("message", "Hay una nueva versión de CERCA disponible.")
                val dl = j.optString("download_url", "https://cerca-cuidarte.vercel.app")
                val mandatory = j.optBoolean("mandatory", false)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val b = AlertDialog.Builder(this)
                        .setTitle("Nueva versión de CERCA · $name")
                        .setMessage(msg)
                        .setPositiveButton("ACTUALIZAR AHORA") { _, _ -> beginInAppUpdate(dl, name) }
                    if (!mandatory) b.setNegativeButton("MÁS TARDE", null) else b.setCancelable(false)
                    b.show()
                }
            } catch (_: Exception) { }
        }
    }

    private fun beginInAppUpdate(downloadUrl: String, versionName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingUpdateUrl = downloadUrl
            pendingUpdateName = versionName
            toast("Android te pedirá habilitar una sola vez las actualizaciones desde CERCA.")
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                toast("No pude abrir el permiso de actualización.")
            }
            return
        }
        startUpdateDownload(downloadUrl, versionName)
    }

    private fun startUpdateDownload(downloadUrl: String, versionName: String) {
        try {
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("Actualizando CERCA")
                .setDescription("Descargando CERCA $versionName")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            pendingUpdateDownloadId = dm.enqueue(req)
            toast("Descargando CERCA $versionName…")
        } catch (_: Exception) {
            toast("No se pudo iniciar la actualización.")
        }
    }

    private fun bindViews() {
        loadingPanel = findViewById(R.id.loadingPanel)
        authPanel = findViewById(R.id.authPanel)
        signupPanel = findViewById(R.id.signupPanel)
        setupPanel = findViewById(R.id.setupPanel)
        homePanel = findViewById(R.id.homePanel)
        profilePanel = findViewById(R.id.profilePanel)
        expiredPanel = findViewById(R.id.expiredPanel)
        loadingText = findViewById(R.id.loadingText)

        loginEmail = findViewById(R.id.loginEmail)
        loginPassword = findViewById(R.id.loginPassword)
        signupName = findViewById(R.id.signupName)
        signupEmail = findViewById(R.id.signupEmail)
        signupPassword = findViewById(R.id.signupPassword)
        signupPassword2 = findViewById(R.id.signupPassword2)

        setupTitle = findViewById(R.id.setupTitle)
        setupSubtitle = findViewById(R.id.setupSubtitle)
        name = findViewById(R.id.name)
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

    private fun installAccountPhoneUi() {
        signupPhone = EditText(this).apply {
            hint = "Teléfono celular · ej. +54 9 11 1234 5678"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            textSize = 16f
            setBackgroundResource(R.drawable.input_bg)
        }
        val signupParent = signupEmail.parent as LinearLayout
        signupParent.addView(signupPhone, signupParent.indexOfChild(signupEmail) + 1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = familyDp(10) })
    }

    private fun showPhoneDialog() {
        val input = EditText(this).apply { hint = "+54 9 11 1234 5678"; inputType = android.text.InputType.TYPE_CLASS_PHONE; setText(accountCache().getString("phone_e164", "")) }
        AlertDialog.Builder(this).setTitle("Tu teléfono CERCA").setMessage("Permite detectar automáticamente si un contacto también tiene CERCA.").setView(input).setNegativeButton("CANCELAR", null).setPositiveButton("GUARDAR") { _, _ -> saveAccountPhone(input.text.toString()) }.show()
    }

    private fun saveAccountPhone(raw: String) {
        val session = currentSession ?: return
        if (normalizePhone(raw).length < 9) { toast("Ingresá un teléfono válido."); return }
        runAsync(work = { val fresh = ensureFreshSessionBlocking(session); Pair(fresh, api.setNetworkPhone(fresh, raw)) }, success = { (fresh, result) -> currentSession = fresh; sessionStore.save(fresh); accountCache().edit().putString("phone_e164", result.optString("phone_e164", raw)).apply(); toast("Teléfono guardado."); if (profilePanel.visibility == View.VISIBLE) showProfile() }, failure = { e -> toast(errorMessage(e)) })
    }

    private fun promptPhoneIfNeeded() {
        if (accountCache().getString("phone_e164", "").orEmpty().isNotBlank()) return
        val now = System.currentTimeMillis(); if (now - appPrefs().getLong("phone_prompt_ms", 0L) < 24L * 60L * 60L * 1000L) return
        appPrefs().edit().putLong("phone_prompt_ms", now).apply()
        AlertDialog.Builder(this).setTitle("Completá tu teléfono").setMessage("Ahora CERCA detecta automáticamente cuáles de tus contactos también usan la app. Para eso necesitamos tu número de celular.").setNegativeButton("MÁS TARDE", null).setPositiveButton("COMPLETAR") { _, _ -> showPhoneDialog() }.show()
    }

    private fun installSmsMedicalOptions() {
        sms1Medical = addSmsMedicalOption(1, R.id.pickSms1Button)
        sms2Medical = addSmsMedicalOption(2, R.id.pickSms2Button)
        sms3Medical = addSmsMedicalOption(3, R.id.pickSms3Button)
        sms4Medical = addSmsMedicalOption(4, R.id.pickSms4Button)
    }

    private fun addSmsMedicalOption(index: Int, anchorId: Int): CheckBox {
        val anchor = findViewById<Button>(anchorId)
        val parent = anchor.parent as LinearLayout
        val box = CheckBox(this).apply {
            text = "Enviar también mi ficha médica CERCA ID a este contacto"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#34454A"))
            visibility = View.GONE
            setPadding(2, 2, 2, 2)
        }
        parent.addView(
            box,
            parent.indexOfChild(anchor) + 1,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = familyDp(2)
            }
        )
        box.setOnCheckedChangeListener { _, checked ->
            contactPrefs().edit().putBoolean("sms" + index + "ShareMedical", checked).apply()
            if (checked && medicalShareUrl() == null) {
                toast("Quedó marcado. Para enviar la ficha, guardala y activá el acceso de emergencia en Ficha médica · CERCA ID.")
            }
        }
        return box
    }

    private fun refreshSmsMedicalOptions() {
        if (!::sms1Medical.isInitialized) return
        val boxes = listOf(sms1Medical, sms2Medical, sms3Medical, sms4Medical)
        val phones = listOf(sms1Phone, sms2Phone, sms3Phone, sms4Phone)
        boxes.forEachIndexed { i, box ->
            val hasContact = phones[i].isNotBlank()
            box.visibility = View.GONE
            val wanted = hasContact && contactPrefs().getBoolean("sms" + (i + 1) + "ShareMedical", false)
            if (box.isChecked != wanted) box.isChecked = wanted
        }
    }

    private fun medicalShareUrl(): String? {
        val prefs = getSharedPreferences("cerca_medical_nfc", MODE_PRIVATE)
        val enabled = prefs.getBoolean("share_enabled", false)
        val token = prefs.getString("public_token", "")?.trim().orEmpty()
        return if (enabled && token.isNotBlank()) {
            SupabaseApi.BASE_URL + "/functions/v1/cerca-medical-card?id=" + token
        } else null
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
        findViewById<Button>(R.id.loginButton).setOnClickListener { doLogin() }
        findViewById<Button>(R.id.createAccountNavButton).setOnClickListener { showSignup() }
        findViewById<Button>(R.id.backLoginButton).setOnClickListener { showLogin() }
        findViewById<Button>(R.id.signupButton).setOnClickListener { doSignup() }
        findViewById<Button>(R.id.forgotPasswordButton).setOnClickListener { showForgotPasswordDialog() }
        findViewById<Button>(R.id.authPrivacyButton).setOnClickListener { openPrivacyPolicy() }

        findViewById<Button>(R.id.pickCallContactButton).setOnClickListener { openPhoneContactPicker(REQ_CALL_CONTACT) }
        findViewById<Button>(R.id.pickSms1Button).setOnClickListener { openPhoneContactPicker(REQ_SMS1_CONTACT) }
        findViewById<Button>(R.id.pickSms2Button).setOnClickListener { openPhoneContactPicker(REQ_SMS2_CONTACT) }
        findViewById<Button>(R.id.pickSms3Button).setOnClickListener { openPhoneContactPicker(REQ_SMS3_CONTACT) }
        findViewById<Button>(R.id.pickSms4Button).setOnClickListener { openPhoneContactPicker(REQ_SMS4_CONTACT) }
        findViewById<Button>(R.id.clearSms1Button).setOnClickListener { clearSmsContact(1) }
        findViewById<Button>(R.id.clearSms2Button).setOnClickListener { clearSmsContact(2) }
        findViewById<Button>(R.id.clearSms3Button).setOnClickListener { clearSmsContact(3) }
        findViewById<Button>(R.id.clearSms4Button).setOnClickListener { clearSmsContact(4) }
        findViewById<Button>(R.id.saveSetupButton).setOnClickListener { if (saveSetup()) finishSavingSetup() }
        findViewById<Button>(R.id.cancelEditButton).setOnClickListener {
            editingProfile = false
            loadContactState()
            showProfile()
        }
        findViewById<Button>(R.id.demoDataButton).apply {
            visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
            setOnClickListener { loadDemoDataForScreenshots() }
        }

        findViewById<Button>(R.id.profileButton).setOnClickListener { showProfile() }
        findViewById<Button>(R.id.quickAccessButton).setOnClickListener { startActivity(Intent(this, QuickAccessSettingsActivity::class.java)) }
        findViewById<Button>(R.id.editProfileButton).visibility = View.GONE
        findViewById<Button>(R.id.medicalProfileButton).setOnClickListener { startActivity(Intent(this, MedicalProfileActivity::class.java)) }
        familyHomeButton.setOnClickListener { startActivity(Intent(this, FamilyCircleActivity::class.java)) }
        findViewById<Button>(R.id.profileBackButton).setOnClickListener { routeAfterAuthentication() }
        findViewById<Button>(R.id.logoutButton).setOnClickListener { confirmLogout() }
        findViewById<Button>(R.id.deleteAccountButton).setOnClickListener { confirmDeleteAccount() }
        findViewById<Button>(R.id.privacyPolicyButton).visibility = View.GONE
        findViewById<Button>(R.id.subscribeButton).setOnClickListener { launchSubscriptionForCurrentAccount() }
        findViewById<Button>(R.id.manageSubscriptionButton).visibility = View.GONE

        findViewById<Button>(R.id.expiredSubscribeButton).setOnClickListener { launchSubscriptionForCurrentAccount() }
        findViewById<Button>(R.id.expiredProfileButton).setOnClickListener { showProfile() }
        findViewById<Button>(R.id.expiredPrivacyButton).setOnClickListener { openPrivacyPolicy() }

        findViewById<Button>(R.id.helpButton).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isNetworkAccessCached()) {
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

    private fun appPrefs() = getSharedPreferences(APP_PREFS, MODE_PRIVATE)

    private fun contactPrefs() = getSharedPreferences(
        "help_contacts_${currentSession?.userId ?: "none"}", MODE_PRIVATE
    )

    private fun accountCache() = getSharedPreferences(
        "help_account_${currentSession?.userId ?: "none"}", MODE_PRIVATE
    )

    private fun showOnly(panel: View) {
        listOf(loadingPanel, authPanel, signupPanel, setupPanel, homePanel, profilePanel, expiredPanel)
            .forEach { it.visibility = View.GONE }
        panel.visibility = View.VISIBLE
    }

    private fun showLoading(message: String) {
        loadingText.text = message
        showOnly(loadingPanel)
    }

    private fun showLogin() {
        loginPassword.setText("")
        showOnly(authPanel)
    }

    private fun showSignup() {
        signupPassword.setText("")
        signupPassword2.setText("")
        showOnly(signupPanel)
    }

    private fun doLogin() {
        val email = loginEmail.text.toString().trim()
        val password = loginPassword.text.toString()
        if (!email.contains("@")) { toast("Ingresá un email válido."); return }
        if (password.isBlank()) { toast("Ingresá tu contraseña."); return }
        showLoading("Iniciando sesión…")
        runAsync(
            work = { api.signIn(email, password) },
            success = { session ->
                currentSession = session
                sessionStore.save(session)
                loginPassword.setText("")
                finishAuthenticatedStartup(session)
            },
            failure = { e -> showLogin(); toast(errorMessage(e)) }
        )
    }

    private fun doSignup() {
        val fullName = signupName.text.toString().trim()
        val email = signupEmail.text.toString().trim()
        val phone = signupPhone.text.toString().trim()
        val pass = signupPassword.text.toString()
        val pass2 = signupPassword2.text.toString()
        when {
            fullName.length < 2 -> { toast("Ingresá tu nombre y apellido."); return }
            !email.contains("@") -> { toast("Ingresá un email válido."); return }
            normalizePhone(phone).length < 9 -> { toast("Ingresá tu teléfono celular."); return }
            pass.length < 8 -> { toast("La contraseña debe tener al menos 8 caracteres."); return }
            pass != pass2 -> { toast("Las contraseñas no coinciden."); return }
        }
        showLoading("Creando tu cuenta…")
        runAsync(
            work = { api.signUp(fullName, email, pass, phone) },
            success = { result ->
                signupPassword.setText("")
                signupPassword2.setText("")
                if (result.session != null) {
                    currentSession = result.session
                    sessionStore.save(result.session)
                                finishAuthenticatedStartup(result.session)
                } else {
                    loginEmail.setText(email)
                    showLogin()
                    AlertDialog.Builder(this)
                        .setTitle("Revisá tu email")
                        .setMessage("Te enviamos un mensaje para confirmar la cuenta. Después de confirmarla, volvé a CERCA e iniciá sesión.")
                        .setPositiveButton("ENTENDIDO", null)
                        .show()
                }
            },
            failure = { e -> showSignup(); toast(errorMessage(e)) }
        )
    }

    private fun showForgotPasswordDialog() {
        val input = EditText(this).apply {
            hint = "Email"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(loginEmail.text.toString())
        }
        AlertDialog.Builder(this)
            .setTitle("Recuperar contraseña")
            .setMessage("Ingresá tu email y te enviaremos un enlace para elegir una nueva contraseña.")
            .setView(input)
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("ENVIAR") { _, _ ->
                val email = input.text.toString().trim()
                if (!email.contains("@")) { toast("Ingresá un email válido."); return@setPositiveButton }
                runAsync(
                    work = { api.requestPasswordReset(email); true },
                    success = { toast("Si existe una cuenta con ese email, vas a recibir el enlace de recuperación.") },
                    failure = { e -> toast(errorMessage(e)) }
                )
            }
            .show()
    }

    private fun resumeSavedSession(saved: SupabaseApi.Session) {
        if (!api.isSessionNearExpiry(saved)) {
            finishAuthenticatedStartup(saved)
            return
        }
        runAsync(
            work = { api.refreshSession(saved) },
            success = { fresh ->
                currentSession = fresh
                sessionStore.save(fresh)
                finishAuthenticatedStartup(fresh)
            },
            failure = { e ->
                if (e is SupabaseApi.ApiException && e.status in listOf(400, 401, 403)) {
                    clearSessionOnly()
                    showLogin()
                    toast("Tu sesión venció. Iniciá sesión nuevamente.")
                } else {
                    // Preserve cached access for emergency use if the device is temporarily offline.
                    currentSession = saved
                    loadContactState()
                    routeAfterAuthentication()
                }
            }
        )
    }

    private fun finishAuthenticatedStartup(session: SupabaseApi.Session) {
        currentSession = session
        migrateLegacyContactsIfNeeded()
        loadContactState()
        showLoading("Cargando tu cuenta…")
        runAsync(
            work = {
                val activeSession = ensureFreshSessionBlocking(session)
                val profile = api.fetchProfile(activeSession)
                val entitlement = api.fetchEntitlement(activeSession)
                Triple(activeSession, profile, entitlement)
            },
            success = { (fresh, profile, entitlement) ->
                currentSession = fresh
                sessionStore.save(fresh)
                cacheProfile(profile, entitlement)
                if (contactPrefs().getString("display_name", "").isNullOrBlank()) {
                    contactPrefs().edit().putString("display_name", profile.fullName).apply()
                }
                loadContactState()
                syncActivationsAsync(fresh)
                // No mostramos la interfaz individual antes de resolver empresa/rol.
                // El loading permanece visible hasta aplicar la marca empresarial y el rol maestro.
                EnterpriseUiController.prepareStartup(this, fresh) {
                    routeAfterAuthentication()
                }
            },
            failure = {
                loadContactState()
                routeAfterAuthentication()
                refreshAndSyncInBackground(session)
            }
        )
    }

    private fun ensureFreshSessionBlocking(session: SupabaseApi.Session): SupabaseApi.Session {
        return if (api.isSessionNearExpiry(session)) api.refreshSession(session) else session
    }

    private fun refreshAndSyncInBackground(session: SupabaseApi.Session) {
        runAsync(
            work = {
                val fresh = ensureFreshSessionBlocking(session)
                val localName = contactPrefs().getString("display_name", "").orEmpty()
                if (localName.isNotBlank()) api.updateProfileName(fresh, localName)
                val profile = api.fetchProfile(fresh)
                val entitlement = api.fetchEntitlement(fresh)
                Triple(fresh, profile, entitlement)
            },
            success = { (fresh, profile, entitlement) ->
                currentSession = fresh
                sessionStore.save(fresh)
                cacheProfile(profile, entitlement)
                syncActivationsAsync(fresh)
                if (homePanel.visibility == View.VISIBLE) showHome()
                if (profilePanel.visibility == View.VISIBLE) showProfile()
            },
            failure = { /* Offline is acceptable; emergency functionality uses the cached account state. */ }
        )
    }

    private fun cacheProfile(profile: SupabaseApi.Profile, entitlement: SupabaseApi.Entitlement) {
        val trialEnd = try { Instant.parse(profile.trialEndsAt).toEpochMilli() } catch (_: Exception) { 0L }
        val subscriptionExpiry = try {
            entitlement.expiresAt?.let { Instant.parse(it).toEpochMilli() } ?: 0L
        } catch (_: Exception) { 0L }
        val verifiedServerNow = maxOf(profile.serverEpochMs, entitlement.serverEpochMs)
            .takeIf { it > 0L } ?: System.currentTimeMillis()
        val serverActive = entitlement.subscriptionActive && subscriptionExpiry > verifiedServerNow
        accountCache().edit()
            .putString("full_name", profile.fullName)
            .putString("email", profile.email)
            .putString("phone_e164", profile.phoneE164)
            .putLong("trial_end_ms", trialEnd)
            .putBoolean("server_subscription_active", serverActive)
            .putLong("server_subscription_expires_ms", subscriptionExpiry)
            .putLong("verified_server_epoch_ms", verifiedServerNow)
            .putLong("verified_elapsed_ms", SystemClock.elapsedRealtime())
            .apply()
    }

    private fun migrateLegacyContactsIfNeeded() {
        val cp = contactPrefs()
        if (cp.getBoolean("legacy_migration_done", false)) return
        val old = appPrefs()
        val editor = cp.edit()
        if (old.getBoolean("registered_v5", false)) {
            editor.putString("display_name", old.getString("name", "") ?: "")
            editor.putString("callName", old.getString("callName", "") ?: "")
            editor.putString("callPhone", old.getString("callPhone", "") ?: "")
            editor.putString("sms1Name", old.getString("sms1Name", "") ?: "")
            editor.putString("sms1Phone", old.getString("sms1Phone", "") ?: "")
            editor.putString("sms2Name", old.getString("sms2Name", "") ?: "")
            editor.putString("sms2Phone", old.getString("sms2Phone", "") ?: "")
            editor.putString("sms3Name", old.getString("sms3Name", "") ?: "")
            editor.putString("sms3Phone", old.getString("sms3Phone", "") ?: "")
            editor.putString("sms4Name", old.getString("sms4Name", "") ?: "")
            editor.putString("sms4Phone", old.getString("sms4Phone", "") ?: "")
            editor.putBoolean("configured", (old.getString("callPhone", "") ?: "").isNotBlank() &&
                (old.getString("sms1Phone", "") ?: "").isNotBlank())
        }
        editor.putBoolean("legacy_migration_done", true).apply()
    }

    private fun loadContactState() {
        val p = contactPrefs()
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
        callContactDisplay.text = contactLabel(callName, callPhone, "Todavía no elegiste un contacto")
        sms1Display.text = contactLabel(sms1Name, sms1Phone, "Contacto 1 · Sin elegir")
        sms2Display.text = contactLabel(sms2Name, sms2Phone, "Contacto 2 · Opcional")
        sms3Display.text = contactLabel(sms3Name, sms3Phone, "Contacto 3 · Opcional")
        sms4Display.text = contactLabel(sms4Name, sms4Phone, "Contacto 4 · Opcional")

        val phones = listOf(sms1Phone, sms2Phone, sms3Phone, sms4Phone)
        val controls = listOf(
            Triple(R.id.pickSms1Button, R.id.clearSms1Button, 1),
            Triple(R.id.pickSms2Button, R.id.clearSms2Button, 2),
            Triple(R.id.pickSms3Button, R.id.clearSms3Button, 3),
            Triple(R.id.pickSms4Button, R.id.clearSms4Button, 4)
        )
        controls.forEachIndexed { i, control ->
            val hasContact = phones[i].isNotBlank()
            findViewById<Button>(control.first).text = if (hasContact) "CAMBIAR CONTACTO ${control.third}" else "ELEGIR CONTACTO ${control.third}"
            findViewById<Button>(control.second).visibility = if (hasContact) View.VISIBLE else View.GONE
        }

        refreshSmsMedicalOptions()
        if (callPhone.isNotBlank()) callPhoneManual.setText(callPhone)
    }

    private fun clearSmsContact(index: Int) {
        when (index) {
            1 -> { sms1Name = ""; sms1Phone = "" }
            2 -> { sms2Name = ""; sms2Phone = "" }
            3 -> { sms3Name = ""; sms3Phone = "" }
            4 -> { sms4Name = ""; sms4Phone = "" }
        }
        contactPrefs().edit().putBoolean("sms" + index + "ShareMedical", false).apply()
        updateContactDisplays()
    }

    private fun contactLabel(contactName: String, phone: String, emptyText: String): String {
        if (phone.isBlank()) return emptyText
        return "${contactName.ifBlank { "Contacto" }}\n$phone"
    }

    private fun routeAfterAuthentication() {
        if (currentSession == null) { showLogin(); return }
        if (isNetworkAccessCached()) {
            showHome()
            if (savedSmsContacts().isEmpty() && !contactPrefs().getBoolean("network_onboarding_shown", false)) {
                contactPrefs().edit().putBoolean("network_onboarding_shown", true).apply()
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isFinishing && !isDestroyed) startActivity(Intent(this, FamilyCircleActivity::class.java))
                }, 700L)
            }
        } else {
            showExpired()
        }
    }

    private fun showSetup(editing: Boolean) {
        if (currentSession == null) { showLogin(); return }
        editingProfile = editing
        loadContactState()
        val cachedName = contactPrefs().getString("display_name", "").orEmpty()
            .ifBlank { accountCache().getString("full_name", "").orEmpty() }
        name.setText(cachedName)
        callPhoneManual.setText(callPhone)
        setupTitle.text = if (editing) "Editar tu red de ayuda" else "Configurá tu red de ayuda"
        setupSubtitle.text = if (editing) {
            "Actualizá tu nombre o los contactos que recibirán el aviso."
        } else {
            "Elegí una vez tus contactos. Después, pedir ayuda es simple."
        }
        findViewById<Button>(R.id.cancelEditButton).visibility = if (editing) View.VISIBLE else View.GONE
        showOnly(setupPanel)
    }

    private fun saveSetup(): Boolean {
        val personName = name.text.toString().trim()
        val manualCall = normalizePhone(callPhoneManual.text.toString())
        if (personName.length < 2) { toast("Ingresá tu nombre."); return false }
        if (manualCall.isBlank()) { toast("Elegí o escribí un número para la llamada."); return false }
        if (sms1Phone.isBlank() && sms2Phone.isBlank() && sms3Phone.isBlank() && sms4Phone.isBlank()) {
            toast("Elegí al menos un contacto para recibir el SMS.")
            return false
        }
        if (manualCall != callPhone) {
            callPhone = manualCall
            if (callName.isBlank()) callName = "Contacto de llamada"
        }
        contactPrefs().edit()
            .putString("display_name", personName)
            .putString("callName", callName)
            .putString("callPhone", callPhone)
            .putString("sms1Name", sms1Name).putString("sms1Phone", sms1Phone)
            .putString("sms2Name", sms2Name).putString("sms2Phone", sms2Phone)
            .putString("sms3Name", sms3Name).putString("sms3Phone", sms3Phone)
            .putString("sms4Name", sms4Name).putString("sms4Phone", sms4Phone)
            .putBoolean("sms1ShareMedical", sms1Medical.isChecked)
            .putBoolean("sms2ShareMedical", sms2Medical.isChecked)
            .putBoolean("sms3ShareMedical", sms3Medical.isChecked)
            .putBoolean("sms4ShareMedical", sms4Medical.isChecked)
            .putBoolean("configured", true)
            .apply()
        accountCache().edit().putString("full_name", personName).apply()
        return true
    }

    private fun finishSavingSetup() {
        val session = currentSession ?: return
        val fullName = contactPrefs().getString("display_name", "").orEmpty()
        editingProfile = false
        showPermissionDisclosureIfNeeded()
        routeAfterAuthentication()
        runAsync(
            work = { api.updateProfileName(ensureFreshSessionBlocking(session), fullName); true },
            success = { },
            failure = { toast("Guardamos los contactos en el teléfono. El nombre se sincronizará cuando vuelva Internet.") }
        )
    }

    private fun showHome() {
        if (currentSession == null) { showLogin(); return }
        loadContactState()
        val callLabel = if (callPhone.isBlank()) "—" else "${callName.ifBlank { "Contacto" }} · $callPhone"
        val smsContacts = savedSmsContacts()
        homeCallSummary.text = "Llamada: $callLabel"
        homeSmsSummary.text = "Avisos por SMS: ${smsContacts.size} contacto(s) · Mi Red CERCA suma alertas dentro de la app"
        trialBadge.text = when {
            isSubscriptionActiveCached() -> "Suscripción activa"
            daysRemaining() > 0 -> "Prueba gratuita · ${daysRemaining()} día(s)"
            else -> "Prueba finalizada"
        }
        status.text = "Mantené apretado 3 segundos para pedir ayuda."
        applyFamilyTestUi()
        requestNetworkNotificationPermissionIfNeeded()
        showOnly(homePanel)
        promptPhoneIfNeeded()
        maybeTriggerShortcutEmergency()
    }

    private fun maybeTriggerShortcutEmergency() {
        if (!intent.getBooleanExtra(EXTRA_AUTO_TRIGGER_HELP, false) || emergencyInProgress) return
        intent.removeExtra(EXTRA_AUTO_TRIGGER_HELP)
        Handler(Looper.getMainLooper()).postDelayed({ triggerHelp() }, 180L)
    }

    private fun showProfile() {
        val session = currentSession ?: run { showLogin(); return }
        applyFamilyTestUi()
        val displayName = contactPrefs().getString("display_name", "").orEmpty()
            .ifBlank { accountCache().getString("full_name", "").orEmpty() }
        profileData.text = "Nombre: ${displayName.ifBlank { "—" }}\nEmail: ${session.email}\nTeléfono: ${accountCache().getString("phone_e164", "").orEmpty().ifBlank { "Falta completar" }}\nVersión: ${BuildConfig.VERSION_NAME}"
        subscriptionStatus.text = when {
            isSubscriptionActiveCached() -> "Activa. CERCA está habilitada."
            daysRemaining() > 0 -> "Prueba gratuita: quedan ${daysRemaining()} día(s)."
            else -> "La prueba gratuita terminó."
        }
        showOnly(profilePanel)
    }

    private fun installFamilyTestUi() {
        familyHomeButton = Button(this).apply {
            text = "MI RED CERCA"
            setTextColor(android.graphics.Color.parseColor("#0B5960"))
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#DDF2F0"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            visibility = View.GONE
        }
        silentHelpButton = Button(this).apply {
            text = "SOS SILENCIOSO"
            setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#B54F45"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Activar SOS silencioso")
                    .setMessage("Enviará el SMS, la ubicación y la alerta a tu Red CERCA, pero no realizará la llamada automática.")
                    .setNegativeButton("CANCELAR", null)
                    .setPositiveButton("ACTIVAR") { _, _ -> triggerHelp(true) }
                    .show()
            }
        }
        activeEmergencyButton = Button(this).apply {
            text = "ESTOY BIEN · FINALIZAR EMERGENCIA"
            setTextColor(android.graphics.Color.parseColor("#0B5960"))
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#DDF2F0"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            visibility = View.GONE
            setOnClickListener { resolveActiveNetworkEmergency() }
        }

        val quick = findViewById<Button>(R.id.quickAccessButton)
        val quickIndex = homePanel.indexOfChild(quick)
        homePanel.addView(familyHomeButton, if (quickIndex >= 0) quickIndex + 1 else homePanel.childCount,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, familyDp(58)).apply { setMargins(0, familyDp(10), 0, 0) })
        homePanel.addView(silentHelpButton, if (quickIndex >= 0) quickIndex + 2 else homePanel.childCount,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, familyDp(58)).apply { setMargins(0, familyDp(10), 0, 0) })
        homePanel.addView(activeEmergencyButton, if (quickIndex >= 0) quickIndex + 3 else homePanel.childCount,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, familyDp(58)).apply { setMargins(0, familyDp(10), 0, 0) })

        planTestCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(familyDp(18), familyDp(18), familyDp(18), familyDp(18))
            setBackgroundResource(R.drawable.card_bg)
            visibility = View.GONE
        }
        planTestCard.addView(TextView(this).apply {
            text = "Modo de prueba de planes"
            textSize = 19f
            setTextColor(android.graphics.Color.parseColor("#0B5960"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        planTestCard.addView(TextView(this).apply {
            text = "Testing interno: podés alternar entre Individual y Familiar sin cambiar ni cobrar tu suscripción de Google Play."
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#657579"))
            setPadding(0, familyDp(6), 0, familyDp(8))
        })
        planTestStatus = TextView(this).apply {
            textSize = 15f
            setTextColor(android.graphics.Color.parseColor("#0B5960"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, familyDp(3), 0, familyDp(8))
        }
        planTestCard.addView(planTestStatus)
        planTestCard.addView(Button(this).apply {
            text = "PROBAR CERCA INDIVIDUAL"
            setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#0B5960"))
            setOnClickListener { setFamilyTestPlanRemote("individual") }
        })
        planTestCard.addView(Button(this).apply {
            text = "PROBAR CERCA FAMILIAR"
            setTextColor(android.graphics.Color.parseColor("#0B5960"))
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#DDF2F0"))
            setOnClickListener { setFamilyTestPlanRemote("family") }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, familyDp(7), 0, 0) })
        planTestCard.addView(TextView(this).apply {
            text = "Individual: SOS, contactos, ficha médica y medicamentos.\n\nFamiliar: suma Mi Círculo CERCA, invitaciones y acceso autorizado a fichas médicas de la familia."
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#657579"))
            setPadding(0, familyDp(10), 0, 0)
        })
        val back = findViewById<Button>(R.id.profileBackButton)
        val backIndex = profilePanel.indexOfChild(back)
        profilePanel.addView(planTestCard, if (backIndex >= 0) backIndex else profilePanel.childCount,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, familyDp(14), 0, 0) })
        applyFamilyTestUi()
    }

    private fun cacheFamilyTestState(state: org.json.JSONObject) {
        accountCache().edit()
            .putBoolean("family_beta_enabled", state.optBoolean("beta_enabled", false))
            .putString("family_test_plan", state.optString("plan", "individual"))
            .putInt("family_invite_count", state.optJSONArray("invitations")?.length() ?: 0)
            .apply()
    }

    private fun refreshFamilyTestStateAsync(session: SupabaseApi.Session) {
        runAsync(
            work = {
                val fresh = ensureFreshSessionBlocking(session)
                Pair(fresh, api.fetchFamilyState(fresh))
            },
            success = { (fresh, state) ->
                currentSession = fresh
                sessionStore.save(fresh)
                cacheFamilyTestState(state)
                applyFamilyTestUi()
            },
            failure = { /* Conservamos el último plan de prueba conocido. */ }
        )
    }

    private fun setFamilyTestPlanRemote(plan: String) {
        val session = currentSession ?: return
        toast("Actualizando vista de prueba…")
        runAsync(
            work = {
                val fresh = ensureFreshSessionBlocking(session)
                Pair(fresh, api.setFamilyTestPlan(fresh, plan))
            },
            success = { (fresh, state) ->
                currentSession = fresh
                sessionStore.save(fresh)
                cacheFamilyTestState(state)
                applyFamilyTestUi()
                toast(if (plan == "family") "CERCA Familiar activado para pruebas." else "CERCA Individual activado para pruebas.")
                if (profilePanel.visibility == View.VISIBLE) showProfile()
            },
            failure = { e -> toast(errorMessage(e)) }
        )
    }

    private fun currentFamilyTestPlan(): String = accountCache().getString("family_test_plan", "individual") ?: "individual"

    private fun applyFamilyTestUi() {
        if (::familyHomeButton.isInitialized) familyHomeButton.visibility = View.VISIBLE
        if (::planTestCard.isInitialized) planTestCard.visibility = View.GONE
    }

    private fun familyDp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showExpired() = showOnly(expiredPanel)

    private fun isSubscriptionActiveCached(): Boolean {
        // Paid access is account-specific and becomes active only after server-side Google Play verification.
        val cache = accountCache()
        val expiry = cache.getLong("server_subscription_expires_ms", 0L)
        return cache.getBoolean("server_subscription_active", false) && expiry > trustedNowMs()
    }

    private fun trialEndMs(): Long = accountCache().getLong("trial_end_ms", 0L)

    private fun trustedNowMs(): Long {
        val cache = accountCache()
        val verifiedEpoch = cache.getLong("verified_server_epoch_ms", 0L)
        val verifiedElapsed = cache.getLong("verified_elapsed_ms", 0L)
        val elapsedNow = SystemClock.elapsedRealtime()
        if (verifiedEpoch > 0L && verifiedElapsed > 0L && elapsedNow >= verifiedElapsed) {
            return verifiedEpoch + (elapsedNow - verifiedElapsed)
        }
        // Después de un reinicio no podemos reconstruir elapsedRealtime; nunca retrocedemos antes
        // del último horario de servidor conocido y refrescamos al volver a tener Internet.
        return maxOf(verifiedEpoch, System.currentTimeMillis())
    }

    private fun daysRemaining(): Int {
        val diff = trialEndMs() - trustedNowMs()
        if (diff <= 0L) return 0
        return ceil(diff.toDouble() / DAY_MS.toDouble()).toInt()
    }

    private fun isNetworkAccessCached(): Boolean = isSubscriptionActiveCached() || daysRemaining() > 0 || accountCache().getBoolean("enterprise_access_active", false)

    private fun launchSubscriptionForCurrentAccount() {
        val session = currentSession ?: run { showLogin(); return }
        billingManager.launchSubscription(BillingManager.obfuscateAccountId(session.userId))
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("Tus contactos de emergencia quedarán guardados en este teléfono para esta cuenta.")
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("CERRAR SESIÓN") { _, _ ->
                clearSessionOnly()
                showLogin()
            }
            .show()
    }

    private fun clearSessionOnly() {
        sessionStore.clear()
        currentSession = null
        loginPassword.setText("")
    }

    private fun confirmDeleteAccount() {
        val session = currentSession ?: return
        AlertDialog.Builder(this)
            .setTitle("Eliminar mi cuenta")
            .setMessage("Se eliminarán permanentemente tu cuenta, perfil y registros de activación. Esta acción no se puede deshacer.")
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("ELIMINAR") { _, _ ->
                showLoading("Eliminando tu cuenta…")
                runAsync(
                    work = {
                        val fresh = ensureFreshSessionBlocking(session)
                        api.deleteAccount(fresh)
                        fresh.userId
                    },
                    success = { userId ->
                        clearLocalUserData(userId)
                        clearSessionOnly()
                        showLogin()
                        toast("Tu cuenta y sus datos fueron eliminados.")
                    },
                    failure = { e -> showProfile(); toast(errorMessage(e)) }
                )
            }
            .show()
    }

    private fun clearLocalUserData(userId: String) {
        getSharedPreferences("help_contacts_$userId", MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("help_account_$userId", MODE_PRIVATE).edit().clear().apply()
        activationQueue.clearFor(userId)
    }

    private fun openPhoneContactPicker(requestCode: Int) {
        try {
            startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), requestCode)
        } catch (_: Exception) { toast("No pude abrir tus contactos.") }
    }

    @Deprecated("Retained for compatibility")
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
                val n = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val p = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (n >= 0) pickedName = cursor.getString(n) ?: ""
                if (p >= 0) pickedPhone = normalizePhone(cursor.getString(p) ?: "")
            }
        } finally { cursor?.close() }
        if (pickedPhone.isBlank()) { toast("Ese contacto no tiene un teléfono disponible."); return }
        when (requestCode) {
            REQ_CALL_CONTACT -> { callName = pickedName; callPhone = pickedPhone; callPhoneManual.setText(pickedPhone) }
            REQ_SMS1_CONTACT -> { sms1Name = pickedName; sms1Phone = pickedPhone; contactPrefs().edit().putBoolean("sms1ShareMedical", false).apply() }
            REQ_SMS2_CONTACT -> { sms2Name = pickedName; sms2Phone = pickedPhone; contactPrefs().edit().putBoolean("sms2ShareMedical", false).apply() }
            REQ_SMS3_CONTACT -> { sms3Name = pickedName; sms3Phone = pickedPhone; contactPrefs().edit().putBoolean("sms3ShareMedical", false).apply() }
            REQ_SMS4_CONTACT -> { sms4Name = pickedName; sms4Phone = pickedPhone; contactPrefs().edit().putBoolean("sms4ShareMedical", false).apply() }
        }
        // Persistimos cada selección inmediatamente. Algunos selectores de contactos
        // recrean la Activity al volver y, si esperamos al botón Guardar, se puede perder.
        contactPrefs().edit()
            .putString("callName", callName).putString("callPhone", callPhone)
            .putString("sms1Name", sms1Name).putString("sms1Phone", sms1Phone)
            .putString("sms2Name", sms2Name).putString("sms2Phone", sms2Phone)
            .putString("sms3Name", sms3Name).putString("sms3Phone", sms3Phone)
            .putString("sms4Name", sms4Name).putString("sms4Phone", sms4Phone)
            .commit()
        updateContactDisplays()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SMS_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) requestRemainingEmergencyPermissions() else toast("CERCA necesita permiso para enviar el SMS de emergencia.")
        } else if (requestCode == REQ_OTHER_PERMISSIONS || requestCode == REQ_PERMISSIONS) {
            toast(if (hasEmergencyPermissions()) "Permisos listos para pedir ayuda." else "Podés habilitar los permisos desde Ajustes cuando quieras.")
        }
    }

    private fun showPermissionDisclosureIfNeeded() {
        if (hasEmergencyPermissions()) return
        AlertDialog.Builder(this)
            .setTitle("Permisos necesarios para pedir ayuda")
            .setMessage(
                "CERCA usa el teléfono para llamar al contacto que elegiste, SMS para enviar la alerta y la ubicación únicamente al activar PEDIR AYUDA para incluir un enlace puntual de Google Maps. No realiza seguimiento continuo ni lee tus mensajes."
            )
            .setNegativeButton("AHORA NO", null)
            .setPositiveButton("CONTINUAR") { _, _ -> requestSmsPermissionFirst() }
            .show()
    }

    private fun requestEmergencyPermissionsIfNeeded() { requestSmsPermissionFirst() }

    private fun requestSmsPermissionFirst() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), REQ_SMS_PERMISSION)
        } else requestRemainingEmergencyPermissions()
    }

    private fun requestRemainingEmergencyPermissions() {
        val missing = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_OTHER_PERMISSIONS)
        else toast("Permisos listos para pedir ayuda.")
    }

    private fun hasEmergencyPermissions(): Boolean {
        val phone = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val sms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return phone && sms && (fine || coarse)
    }

    private fun triggerHelp(silent: Boolean = false) {
        if (emergencyInProgress) return
        val permissionsReady = if (silent) hasSilentEmergencyPermissions() else hasEmergencyPermissions()
        if (!permissionsReady) {
            status.text = if (silent) {
                "Necesito permisos de SMS y ubicación."
            } else {
                "Necesito permisos de teléfono, SMS y ubicación."
            }
            showPermissionDisclosureIfNeeded()
            return
        }

        emergencyInProgress = true
        enqueueActivation()
        status.text = if (silent) "Activando SOS silencioso…" else "Obteniendo tu ubicación…"

        getCurrentLocation { latitude, longitude ->
            val personName = contactPrefs().getString("display_name", "").orEmpty().ifBlank { "Una persona" }
            val mapsLink = if (latitude != null && longitude != null) {
                val coords = String.format(java.util.Locale.US, "%.6f,%.6f", latitude, longitude)
                "https://maps.google.com/?q=$coords"
            } else {
                "Ubicacion no disponible"
            }
            val safeName = Normalizer.normalize(personName, Normalizer.Form.NFD)
                .replace("\\p{M}+".toRegex(), "")
                .replace(Regex("[^A-Za-z0-9 ._-]"), "")
                .trim().take(40).ifBlank { "Una persona" }

            val message = "CERCA - $safeName necesita ayuda. Ubicacion: $mapsLink"
            startNetworkEmergencyAsync(silent, latitude, longitude)

            emergencyCallPending = !silent
            val smsQueued = sendSmsToContacts(message)

            if (silent) {
                emergencyInProgress = false
                status.text = if (smsQueued) {
                    "SOS silencioso enviado. Tu Red CERCA fue alertada."
                } else {
                    "SOS silencioso activado. No pude confirmar el SMS."
                }
                return@getCurrentLocation
            }

            if (!smsQueued) {
                Handler(Looper.getMainLooper()).postDelayed({ makeDirectCall() }, 700L)
            } else {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (emergencyCallPending && sentSmsParts + failedSmsParts == 0) {
                        appPrefs().edit().putString("last_sms_diag", "SIN CALLBACK DEL MODEM").apply()
                        toast("El teléfono no confirmó el SMS; inicio la llamada igual.")
                    }
                    makeDirectCall()
                }, 8000L)
            }
        }
    }


    private fun startNetworkEmergencyAsync(silent: Boolean, latitude: Double?, longitude: Double?) {
        val session = currentSession ?: return
        runAsync(
            work = {
                val fresh = ensureFreshSessionBlocking(session)
                val result = api.startNetworkEmergency(fresh, silent, latitude, longitude)
                Pair(fresh, result)
            },
            success = { (fresh, result) ->
                currentSession = fresh
                sessionStore.save(fresh)
                val emergency = result.optJSONObject("emergency")
                val id = emergency?.optString("id", "").orEmpty()
                if (id.isNotBlank()) {
                    accountCache().edit().putString("active_network_emergency_id", id).apply()
                    try {
                        ContextCompat.startForegroundService(
                            this,
                            Intent(this, EmergencyLocationService::class.java).putExtra("emergency_id", id)
                        )
                    } catch (_: Exception) {}
                    refreshNetworkEmergencyUiAsync(fresh)
                }
            },
            failure = { /* El SMS y la llamada siguen funcionando aunque falle la red CERCA. */ }
        )
    }

    private fun refreshNetworkEmergencyUiAsync(session: SupabaseApi.Session) {
        runAsync(
            work = {
                val fresh = ensureFreshSessionBlocking(session)
                Pair(fresh, api.fetchNetworkState(fresh))
            },
            success = { (fresh, state) ->
                currentSession = fresh
                sessionStore.save(fresh)

                val own = state.optJSONObject("active_emergency")
                val ownId = own?.optString("id", "").orEmpty()
                accountCache().edit().putString("active_network_emergency_id", ownId).apply()
                if (::activeEmergencyButton.isInitialized) {
                    activeEmergencyButton.visibility = if (ownId.isNotBlank()) View.VISIBLE else View.GONE
                    if (ownId.isNotBlank()) {
                        val seen = own?.optJSONArray("seen_by")
                        activeEmergencyButton.text = if (seen != null && seen.length() > 0) {
                            "ESTOY BIEN · ${seen.length()} CONTACTO(S) YA VIERON"
                        } else {
                            "ESTOY BIEN · FINALIZAR EMERGENCIA"
                        }
                    }
                }

                val alerts = state.optJSONArray("incoming_alerts")
                if (alerts != null && alerts.length() > 0) {
                    val alert = alerts.optJSONObject(0)
                    if (alert != null) {
                        val id = alert.optString("id", "")
                        val lastShown = accountCache().getString("last_network_alert_shown", "").orEmpty()
                        if (id.isNotBlank() && id != lastShown) {
                            accountCache().edit().putString("last_network_alert_shown", id).apply()
                            val data = mapOf(
                                "event" to "active",
                                "emergency_id" to id,
                                "owner_user_id" to alert.optString("owner_user_id", ""),
                                "person_name" to alert.optString("person_name", "Contacto CERCA"),
                                "mode" to alert.optString("mode", "normal"),
                                "latitude" to alert.optString("latitude", ""),
                                "longitude" to alert.optString("longitude", ""),
                                "medical_access" to alert.optString("medical_access", "never")
                            )
                            CercaMessagingService.showEmergencyNotification(this, data)
                        }
                    }
                }
            },
            failure = { /* La app mantiene el SOS local aunque no haya Internet. */ }
        )
    }

    private fun resolveActiveNetworkEmergency() {
        val id = accountCache().getString("active_network_emergency_id", "").orEmpty()
        val session = currentSession ?: return
        if (id.isBlank()) {
            toast("No hay una emergencia CERCA activa.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("¿Estás bien?")
            .setMessage("Al finalizar, CERCA avisará a tu red que la emergencia terminó y dejará de actualizar tu ubicación.")
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("SÍ, ESTOY BIEN") { _, _ ->
                runAsync(
                    work = {
                        val fresh = ensureFreshSessionBlocking(session)
                        api.resolveNetworkEmergency(fresh, id)
                        fresh
                    },
                    success = { fresh ->
                        currentSession = fresh
                        sessionStore.save(fresh)
                        accountCache().edit().remove("active_network_emergency_id").apply()
                        try { stopService(Intent(this, EmergencyLocationService::class.java)) } catch (_: Exception) {}
                        if (::activeEmergencyButton.isInitialized) activeEmergencyButton.visibility = View.GONE
                        toast("Emergencia finalizada.")
                    },
                    failure = { e -> toast(errorMessage(e)) }
                )
            }.show()
    }

    private fun registerPushIfAvailable(session: SupabaseApi.Session) {
        if (accountCache().getBoolean("push_registering", false)) return
        accountCache().edit().putBoolean("push_registering", true).apply()
        runAsync(
            work = {
                val fresh = ensureFreshSessionBlocking(session)
                Pair(fresh, api.fetchPushConfig(fresh))
            },
            success = { (fresh, config) ->
                accountCache().edit().putBoolean("push_registering", false).apply()
                if (!config.optBoolean("enabled", false)) return@runAsync
                try {
                    var app = try { com.google.firebase.FirebaseApp.getInstance() } catch (_: Exception) { null }
                    if (app == null) {
                        val options = com.google.firebase.FirebaseOptions.Builder()
                            .setApiKey(config.optString("apiKey"))
                            .setApplicationId(config.optString("applicationId"))
                            .setProjectId(config.optString("projectId"))
                            .setGcmSenderId(config.optString("senderId"))
                            .build()
                        app = com.google.firebase.FirebaseApp.initializeApp(this, options)
                    }
                    if (app != null) {
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                            .addOnSuccessListener { token ->
                                if (token.isNotBlank()) {
                                    runAsync(
                                        work = {
                                            val active = ensureFreshSessionBlocking(fresh)
                                            api.registerNetworkDevice(active, token)
                                            active
                                        },
                                        success = { active ->
                                            currentSession = active
                                            sessionStore.save(active)
                                            accountCache().edit().putBoolean("push_registered", true).apply()
                                        },
                                        failure = { }
                                    )
                                }
                            }
                    }
                } catch (_: Exception) {}
            },
            failure = {
                accountCache().edit().putBoolean("push_registering", false).apply()
            }
        )
    }

    private fun requestNetworkNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        if (appPrefs().getBoolean("network_notification_prompted", false)) return
        appPrefs().edit().putBoolean("network_notification_prompted", true).apply()
        AlertDialog.Builder(this)
            .setTitle("Alertas de tu Red CERCA")
            .setMessage("Permití las notificaciones para recibir una alerta prioritaria cuando una persona de tu Red CERCA active un SOS.")
            .setNegativeButton("AHORA NO", null)
            .setPositiveButton("CONTINUAR") { _, _ ->
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 302)
            }.show()
    }

    private fun hasSilentEmergencyPermissions(): Boolean {
        val sms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return sms && (fine || coarse)
    }

    private fun enqueueActivation() {
        val session = currentSession ?: return
        activationQueue.enqueue(session.userId, BuildConfig.VERSION_NAME)
        syncActivationsAsync(session)
    }

    private fun verifyPurchaseTokenAsync(token: String) {
        val session = currentSession ?: return
        runAsync(
            work = {
                val fresh = ensureFreshSessionBlocking(session)
                api.verifyGooglePlayPurchase(fresh, token)
                val entitlement = api.fetchEntitlement(fresh)
                Pair(fresh, entitlement)
            },
            success = { (fresh, entitlement) ->
                currentSession = fresh
                sessionStore.save(fresh)
                val expiry = try { entitlement.expiresAt?.let { Instant.parse(it).toEpochMilli() } ?: 0L } catch (_: Exception) { 0L }
                val serverNow = entitlement.serverEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis()
                val now = serverNow
                val active = entitlement.subscriptionActive && expiry > now
                accountCache().edit()
                    .putBoolean("server_subscription_active", active)
                    .putLong("server_subscription_expires_ms", expiry)
                    .putLong("verified_server_epoch_ms", serverNow)
                    .putLong("verified_elapsed_ms", SystemClock.elapsedRealtime())
                    .apply()
                if (profilePanel.visibility == View.VISIBLE) showProfile()
                if (expiredPanel.visibility == View.VISIBLE && active) showHome()
            },
            failure = { /* Paid access remains unchanged until server-side Google Play verification succeeds. */ }
        )
    }

    private fun syncActivationsAsync(session: SupabaseApi.Session) {
        runAsync(
            work = {
                val fresh = ensureFreshSessionBlocking(session)
                val done = mutableSetOf<String>()
                for (event in activationQueue.pendingFor(fresh.userId)) {
                    api.insertActivation(fresh, event)
                    done += event.id
                }
                Pair(fresh, done)
            },
            success = { (fresh, done) ->
                currentSession = fresh
                sessionStore.save(fresh)
                activationQueue.remove(done)
            },
            failure = { /* Queue remains on-device and will retry on the next online resume. */ }
        )
    }

    private fun getCurrentLocation(callback: (Double?, Double?) -> Unit) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            callback(null, null); return
        }
        val client = LocationServices.getFusedLocationProviderClient(this)
        val token = CancellationTokenSource()
        val completed = AtomicBoolean(false)
        var cachedLatitude: Double? = null
        var cachedLongitude: Double? = null
        fun finish(latitude: Double?, longitude: Double?) {
            if (!completed.compareAndSet(false, true)) return
            token.cancel()
            locationHandler.removeCallbacksAndMessages(null)
            callback(latitude, longitude)
        }
        client.lastLocation.addOnSuccessListener { last ->
            if (last != null && !completed.get()) { cachedLatitude = last.latitude; cachedLongitude = last.longitude }
        }
        locationHandler.postDelayed({ finish(cachedLatitude, cachedLongitude) }, LOCATION_TIMEOUT_MS)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
            .addOnSuccessListener { loc -> if (loc != null) finish(loc.latitude, loc.longitude) else finish(cachedLatitude, cachedLongitude) }
            .addOnFailureListener { finish(cachedLatitude, cachedLongitude) }
    }

    private data class SmsRecipient(val name: String, val phone: String, val shareMedical: Boolean)

    private fun savedSmsContacts(): List<SmsRecipient> = listOf(
        SmsRecipient(sms1Name, sms1Phone, contactPrefs().getBoolean("sms1ShareMedical", false)),
        SmsRecipient(sms2Name, sms2Phone, contactPrefs().getBoolean("sms2ShareMedical", false)),
        SmsRecipient(sms3Name, sms3Phone, contactPrefs().getBoolean("sms3ShareMedical", false)),
        SmsRecipient(sms4Name, sms4Phone, contactPrefs().getBoolean("sms4ShareMedical", false))
    ).filter { it.phone.isNotBlank() }.distinctBy { it.phone }

    @Suppress("DEPRECATION")
    private fun smsManagerForDefaultSim(): SmsManager? {
        return try {
            // Más robusto en una sola SIM: si no hay preferencia explícita devuelve la SIM activa.
            var subId = SmsManager.getDefaultSmsSubscriptionId()
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                subId = SubscriptionManager.getDefaultSmsSubscriptionId()
            }
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                status.text = "No hay una SIM definida para SMS. Elegí una SIM predeterminada en Ajustes."
                toast(status.text.toString())
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
            } else {
                SmsManager.getSmsManagerForSubscriptionId(subId)
            }
        } catch (e: Exception) {
            status.text = "No pude acceder a la SIM para SMS: ${e.message ?: "sin detalle"}"
            toast(status.text.toString())
            null
        }
    }

    private fun sendSmsToContacts(message: String): Boolean {
        val manager = smsManagerForDefaultSim()
            ?: run { status.text = "No pude acceder al servicio de SMS. La llamada se realizará igual."; return false }
        val contacts = savedSmsContacts()
        if (contacts.isEmpty()) {
            status.text = "No hay contactos para SMS. La llamada se realizará igual."
            return false
        }
        try {
            currentSmsBatch = System.currentTimeMillis()
            expectedSmsParts = 0
            sentSmsParts = 0
            failedSmsParts = 0
            deliveredSmsParts = 0
            var requestCode = (currentSmsBatch xor (currentSmsBatch ushr 32)).toInt()

            contacts.forEach { recipient ->
                val destination = normalizePhone(recipient.phone)
                if (destination.isBlank()) return@forEach
                val recipientMessage = message
                val parts = manager.divideMessage(recipientMessage)
                expectedSmsParts += parts.size
                val sentIntents = ArrayList<PendingIntent>()
                val deliveredIntents = ArrayList<PendingIntent>()
                parts.indices.forEach {
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    val sent = Intent(ACTION_SMS_SENT).setPackage(packageName).putExtra(EXTRA_BATCH, currentSmsBatch)
                    val delivered = Intent(ACTION_SMS_DELIVERED).setPackage(packageName).putExtra(EXTRA_BATCH, currentSmsBatch)
                    sentIntents.add(PendingIntent.getBroadcast(this, requestCode++, sent, flags))
                    deliveredIntents.add(PendingIntent.getBroadcast(this, requestCode++, delivered, flags))
                }
                if (parts.size == 1) {
                    manager.sendTextMessage(destination, null, parts[0], sentIntents[0], deliveredIntents[0])
                } else {
                    manager.sendMultipartTextMessage(destination, null, parts, sentIntents, deliveredIntents)
                }
            }

            if (expectedSmsParts == 0) {
                status.text = "El número de SMS no es válido. La llamada se realizará igual."
                return false
            }
            appPrefs().edit().putString("last_sms_diag", "SOLICITADO ${contacts.size} contacto(s), $expectedSmsParts parte(s)").apply()
            status.text = "Enviando SMS a ${contacts.size} contacto(s)…"
            return true
        } catch (e: Exception) {
            val detail = e.message ?: e.javaClass.simpleName
            appPrefs().edit().putString("last_sms_diag", "EXCEPCION $detail").apply()
            status.text = "No pude enviar el SMS: $detail"
            toast(status.text.toString())
            return false
        }
    }

    private fun makeDirectCall() {
        if (!emergencyCallPending) return
        emergencyCallPending = false
        val phone = normalizePhone(callPhone)
        if (phone.isBlank()) { emergencyInProgress = false; status.text = "No hay un número configurado para llamada."; return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            emergencyInProgress = false; requestEmergencyPermissionsIfNeeded(); return
        }
        try {
            status.text = "Llamando a ${callName.ifBlank { "tu contacto" }}…"
            emergencyInProgress = false
            startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", phone, null)))
        } catch (_: Exception) { emergencyInProgress = false; status.text = "No pude iniciar la llamada." }
    }

    private fun openPrivacyPolicy() = openWebUrl(SupabaseApi.PRIVACY_URL)

    private fun openWebUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { toast("No pude abrir el enlace.") }
    }

    private fun loadDemoDataForScreenshots() {
        if (!BuildConfig.DEBUG) return
        name.setText("Persona de prueba")
        callName = "Contacto de confianza"; callPhone = "+541100000001"
        sms1Name = "Contacto 1"; sms1Phone = "+541100000002"
        sms2Name = "Contacto 2"; sms2Phone = "+541100000003"
        sms3Name = ""; sms3Phone = ""; sms4Name = ""; sms4Phone = ""
        callPhoneManual.setText(callPhone)
        updateContactDisplays()
        toast("Datos ficticios cargados para preparar capturas.")
    }

    private fun normalizePhone(raw: String): String {
        val value = raw.trim()
        return value.filterIndexed { index, c -> c.isDigit() || (c == '+' && index == 0) }
    }

    private fun <T> runAsync(work: () -> T, success: (T) -> Unit, failure: (Throwable) -> Unit) {
        executor.execute {
            try {
                val result = work()
                runOnUiThread { if (!isFinishing && !isDestroyed) success(result) }
            } catch (e: Throwable) {
                runOnUiThread { if (!isFinishing && !isDestroyed) failure(e) }
            }
        }
    }

    private fun errorMessage(e: Throwable): String = when (e) {
        is SupabaseApi.ApiException -> e.message
        else -> "No pudimos conectarnos. Revisá Internet y probá nuevamente."
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

// CI legacy marker: RECEIVER_NOT_EXPORTED
