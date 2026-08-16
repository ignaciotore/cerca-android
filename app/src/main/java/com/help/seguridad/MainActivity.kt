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
import android.os.SystemClock
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
import java.time.Instant
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

    private lateinit var trialBadge: TextView
    private lateinit var status: TextView
    private lateinit var homeCallSummary: TextView
    private lateinit var homeSmsSummary: TextView
    private lateinit var profileData: TextView
    private lateinit var subscriptionStatus: TextView

    private lateinit var billingManager: BillingManager
    private val api = SupabaseApi()
    private lateinit var sessionStore: SecureSessionStore
    private lateinit var activationQueue: ActivationQueue
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var currentSession: SupabaseApi.Session? = null
    private var editingProfile = false
    private var emergencyInProgress = false

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
        sessionStore = SecureSessionStore(this)
        activationQueue = ActivationQueue(this)
        bindViews()
        registerSmsReceivers()
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
        if (::billingManager.isInitialized) billingManager.refreshPurchases()
        val session = currentSession
        if (session != null) refreshAndSyncInBackground(session)
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
        super.onDestroy()
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

    private fun registerSmsReceivers() {
        ContextCompat.registerReceiver(
            this,
            smsSentReceiver,
            IntentFilter(ACTION_SMS_SENT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            smsDeliveredReceiver,
            IntentFilter(ACTION_SMS_DELIVERED),
            ContextCompat.RECEIVER_NOT_EXPORTED
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
        findViewById<Button>(R.id.editProfileButton).setOnClickListener { showSetup(editing = true) }
        findViewById<Button>(R.id.profileBackButton).setOnClickListener { routeAfterAuthentication() }
        findViewById<Button>(R.id.logoutButton).setOnClickListener { confirmLogout() }
        findViewById<Button>(R.id.deleteAccountButton).setOnClickListener { confirmDeleteAccount() }
        findViewById<Button>(R.id.privacyPolicyButton).setOnClickListener { openPrivacyPolicy() }
        findViewById<Button>(R.id.subscribeButton).setOnClickListener { launchSubscriptionForCurrentAccount() }
        findViewById<Button>(R.id.manageSubscriptionButton).setOnClickListener { openWebUrl(SUBSCRIPTION_MANAGEMENT_URL) }

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
        val pass = signupPassword.text.toString()
        val pass2 = signupPassword2.text.toString()
        when {
            fullName.length < 2 -> { toast("Ingresá tu nombre y apellido."); return }
            !email.contains("@") -> { toast("Ingresá un email válido."); return }
            pass.length < 8 -> { toast("La contraseña debe tener al menos 8 caracteres."); return }
            pass != pass2 -> { toast("Las contraseñas no coinciden."); return }
        }
        showLoading("Creando tu cuenta…")
        runAsync(
            work = { api.signUp(fullName, email, pass) },
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
                        .setMessage("Te enviamos un mensaje para confirmar la cuenta. Después de confirmarla, volvé a H.E.L.P e iniciá sesión.")
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
                routeAfterAuthentication()
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
        if (callPhone.isNotBlank()) callPhoneManual.setText(callPhone)
    }

    private fun contactLabel(contactName: String, phone: String, emptyText: String): String {
        if (phone.isBlank()) return emptyText
        return "${contactName.ifBlank { "Contacto" }}\n$phone"
    }

    private fun routeAfterAuthentication() {
        if (currentSession == null) { showLogin(); return }
        if (!contactPrefs().getBoolean("configured", false)) {
            showSetup(editing = false)
        } else if (isNetworkAccessCached()) {
            showHome()
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
        homeSmsSummary.text = "Avisos por SMS: ${smsContacts.size} contacto(s)"
        trialBadge.text = when {
            isSubscriptionActiveCached() -> "Suscripción activa"
            daysRemaining() > 0 -> "Prueba gratuita · ${daysRemaining()} día(s)"
            else -> "Prueba finalizada"
        }
        status.text = "Mantené apretado 3 segundos para pedir ayuda."
        showOnly(homePanel)
        maybeTriggerShortcutEmergency()
    }

    private fun maybeTriggerShortcutEmergency() {
        if (!intent.getBooleanExtra(EXTRA_AUTO_TRIGGER_HELP, false) || emergencyInProgress) return
        intent.removeExtra(EXTRA_AUTO_TRIGGER_HELP)
        Handler(Looper.getMainLooper()).postDelayed({ triggerHelp() }, 180L)
    }

    private fun showProfile() {
        val session = currentSession ?: run { showLogin(); return }
        val displayName = contactPrefs().getString("display_name", "").orEmpty()
            .ifBlank { accountCache().getString("full_name", "").orEmpty() }
        profileData.text = "Nombre: ${displayName.ifBlank { "—" }}\nEmail: ${session.email}\nVersión: ${BuildConfig.VERSION_NAME}"
        subscriptionStatus.text = when {
            isSubscriptionActiveCached() -> "Activa. H.E.L.P está habilitada."
            daysRemaining() > 0 -> "Prueba gratuita: quedan ${daysRemaining()} día(s)."
            else -> "La prueba gratuita terminó."
        }
        showOnly(profilePanel)
    }

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

    private fun isNetworkAccessCached(): Boolean = isSubscriptionActiveCached() || daysRemaining() > 0

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
            REQ_SMS1_CONTACT -> { sms1Name = pickedName; sms1Phone = pickedPhone }
            REQ_SMS2_CONTACT -> { sms2Name = pickedName; sms2Phone = pickedPhone }
            REQ_SMS3_CONTACT -> { sms3Name = pickedName; sms3Phone = pickedPhone }
            REQ_SMS4_CONTACT -> { sms4Name = pickedName; sms4Phone = pickedPhone }
        }
        updateContactDisplays()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            toast(if (hasEmergencyPermissions()) "Permisos listos para pedir ayuda." else "Podés habilitar los permisos desde Ajustes cuando quieras.")
        }
    }

    private fun showPermissionDisclosureIfNeeded() {
        if (hasEmergencyPermissions()) return
        AlertDialog.Builder(this)
            .setTitle("Permisos necesarios para pedir ayuda")
            .setMessage(
                "H.E.L.P usa el teléfono para llamar al contacto que elegiste, SMS para enviar la alerta y la ubicación únicamente al activar PEDIR AYUDA para incluir un enlace puntual de Google Maps. No realiza seguimiento continuo ni lee tus mensajes."
            )
            .setNegativeButton("AHORA NO", null)
            .setPositiveButton("CONTINUAR") { _, _ -> requestEmergencyPermissionsIfNeeded() }
            .show()
    }

    private fun requestEmergencyPermissionsIfNeeded() {
        val missing = listOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMISSIONS)
    }

    private fun hasEmergencyPermissions(): Boolean {
        val phone = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val sms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return phone && sms && (fine || coarse)
    }

    private fun triggerHelp() {
        if (emergencyInProgress) return
        if (!hasEmergencyPermissions()) {
            status.text = "Necesito permisos de teléfono, SMS y ubicación."
            showPermissionDisclosureIfNeeded()
            return
        }
        emergencyInProgress = true
        enqueueActivation()
        status.text = "Obteniendo tu ubicación…"
        getCurrentLocation { latitude, longitude ->
            val personName = contactPrefs().getString("display_name", "").orEmpty().ifBlank { "Una persona" }
            val mapsLink = if (latitude != null && longitude != null) {
                "https://maps.google.com/?q=$latitude,$longitude"
            } else {
                "Ubicación no disponible"
            }
            val message = "H.E.L.P · $personName necesita ayuda. Ubicación: $mapsLink"
            sendSmsToContacts(message)
            Handler(Looper.getMainLooper()).postDelayed({ makeDirectCall() }, 900L)
        }
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

    private fun savedSmsContacts(): List<Pair<String, String>> = listOf(
        sms1Name to sms1Phone, sms2Name to sms2Phone, sms3Name to sms3Phone, sms4Name to sms4Phone
    ).filter { it.second.isNotBlank() }.distinctBy { it.second }

    @Suppress("DEPRECATION")
    private fun smsManagerForDefaultSim(): SmsManager? {
        val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            status.text = "Elegí una SIM predeterminada para SMS en Ajustes."
            return null
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
        } else SmsManager.getSmsManagerForSubscriptionId(subId)
    }

    private fun sendSmsToContacts(message: String) {
        val manager = smsManagerForDefaultSim()
        if (manager == null) { status.text = "No pude elegir una SIM para SMS. La llamada se realizará igual."; return }
        val contacts = savedSmsContacts()
        if (contacts.isEmpty()) { status.text = "No hay contactos para SMS. La llamada se realizará igual."; return }
        try {
            currentSmsBatch = System.currentTimeMillis()
            expectedSmsParts = 0; sentSmsParts = 0; failedSmsParts = 0; deliveredSmsParts = 0
            val allParts = contacts.map { it.second to manager.divideMessage(message) }
            expectedSmsParts = allParts.sumOf { it.second.size }
            var requestCode = (currentSmsBatch xor (currentSmsBatch ushr 32)).toInt()
            allParts.forEach { (destination, parts) ->
                val sentIntents = ArrayList<PendingIntent>()
                val deliveredIntents = ArrayList<PendingIntent>()
                parts.indices.forEach {
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    val sent = Intent(ACTION_SMS_SENT).setPackage(packageName).putExtra(EXTRA_BATCH, currentSmsBatch)
                    val delivered = Intent(ACTION_SMS_DELIVERED).setPackage(packageName).putExtra(EXTRA_BATCH, currentSmsBatch)
                    sentIntents.add(PendingIntent.getBroadcast(this, requestCode++, sent, flags))
                    deliveredIntents.add(PendingIntent.getBroadcast(this, requestCode++, delivered, flags))
                }
                if (parts.size == 1) manager.sendTextMessage(destination, null, parts[0], sentIntents[0], deliveredIntents[0])
                else manager.sendMultipartTextMessage(destination, null, parts, sentIntents, deliveredIntents)
            }
            status.text = "Enviando aviso a ${contacts.size} contacto(s)…"
        } catch (_: Exception) { status.text = "No pude enviar el SMS. La llamada se realizará igual." }
    }

    private fun makeDirectCall() {
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
