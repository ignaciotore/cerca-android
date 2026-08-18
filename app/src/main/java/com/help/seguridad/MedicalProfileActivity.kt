package com.help.seguridad

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MedicalProfileActivity : AppCompatActivity() {
    private val api = SupabaseApi()
    private lateinit var sessionStore: SecureSessionStore
    private val executor = Executors.newSingleThreadExecutor()
    private var session: SupabaseApi.Session? = null
    private var publicToken = ""

    private lateinit var fullName: EditText
    private lateinit var birthDate: EditText
    private lateinit var bloodType: EditText
    private lateinit var allergies: EditText
    private lateinit var medications: EditText
    private lateinit var conditions: EditText
    private lateinit var healthProvider: EditText
    private lateinit var memberNumber: EditText
    private lateinit var emergencyContactName: EditText
    private lateinit var emergencyContactPhone: EditText
    private lateinit var notes: EditText
    private lateinit var shareEnabled: CheckBox
    private lateinit var status: TextView
    private lateinit var nfcStatus: TextView
    private lateinit var saveButton: Button
    private lateinit var viewPublicButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medical_profile)
        sessionStore = SecureSessionStore(this)
        bindViews()
        findViewById<Button>(R.id.medicalBackButton).setOnClickListener { finish() }
        saveButton.setOnClickListener { saveProfile() }
        viewPublicButton.setOnClickListener { openPublicProfile() }
        findViewById<Button>(R.id.nfcPriorityButton).setOnClickListener { requestNfcPreference() }
        loadProfile()
        refreshNfcStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshNfcStatus()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun bindViews() {
        fullName = findViewById(R.id.medicalFullName)
        birthDate = findViewById(R.id.medicalBirthDate)
        bloodType = findViewById(R.id.medicalBloodType)
        allergies = findViewById(R.id.medicalAllergies)
        medications = findViewById(R.id.medicalMedications)
        conditions = findViewById(R.id.medicalConditions)
        healthProvider = findViewById(R.id.medicalHealthProvider)
        memberNumber = findViewById(R.id.medicalMemberNumber)
        emergencyContactName = findViewById(R.id.medicalEmergencyContactName)
        emergencyContactPhone = findViewById(R.id.medicalEmergencyContactPhone)
        notes = findViewById(R.id.medicalNotes)
        shareEnabled = findViewById(R.id.medicalShareEnabled)
        status = findViewById(R.id.medicalStatus)
        nfcStatus = findViewById(R.id.medicalNfcStatus)
        saveButton = findViewById(R.id.medicalSaveButton)
        viewPublicButton = findViewById(R.id.medicalViewPublicButton)
    }

    private fun loadProfile() {
        setBusy(true, "Cargando tu ficha médica…")
        executor.execute {
            try {
                var s = sessionStore.load() ?: throw IllegalStateException("Iniciá sesión nuevamente.")
                if (api.isSessionNearExpiry(s)) {
                    s = api.refreshSession(s)
                    sessionStore.save(s)
                }
                session = s
                val medical = api.fetchMedicalProfile(s)
                val accountName = if (medical == null) runCatching { api.fetchProfile(s).fullName }.getOrDefault("") else ""
                runOnUiThread {
                    if (medical != null) fill(medical) else fullName.setText(accountName)
                    setBusy(false, if (medical == null) "Completá la información que quieras mostrar en una emergencia." else "Ficha cargada.")
                    refreshNfcStatus()
                }
            } catch (e: Exception) {
                runOnUiThread { setBusy(false, e.message ?: "No pudimos cargar la ficha.") }
            }
        }
    }

    private fun fill(p: SupabaseApi.MedicalProfile) {
        fullName.setText(p.fullName)
        birthDate.setText(p.birthDate)
        bloodType.setText(p.bloodType)
        allergies.setText(p.allergies)
        medications.setText(p.medications)
        conditions.setText(p.conditions)
        healthProvider.setText(p.healthProvider)
        memberNumber.setText(p.memberNumber)
        emergencyContactName.setText(p.emergencyContactName)
        emergencyContactPhone.setText(p.emergencyContactPhone)
        notes.setText(p.notes)
        shareEnabled.isChecked = p.shareEnabled
        publicToken = p.publicToken
        syncNfcPrefs(p)
        viewPublicButton.visibility = if (p.publicToken.isBlank() || !p.shareEnabled) View.GONE else View.VISIBLE
    }

    private fun saveProfile() {
        val s = session ?: run { toast("Esperá a que termine de cargar tu cuenta."); return }
        val birth = birthDate.text.toString().trim()
        if (birth.isNotBlank() && !Regex("\\d{4}-\\d{2}-\\d{2}").matches(birth)) {
            toast("Usá la fecha con formato AAAA-MM-DD.")
            return
        }
        if (shareEnabled.isChecked && fullName.text.toString().trim().isBlank()) {
            toast("Ingresá el nombre para identificar la ficha.")
            return
        }
        val p = SupabaseApi.MedicalProfile(
            fullName = fullName.text.toString().trim(),
            birthDate = birth,
            bloodType = bloodType.text.toString().trim(),
            allergies = allergies.text.toString().trim(),
            medications = medications.text.toString().trim(),
            conditions = conditions.text.toString().trim(),
            healthProvider = healthProvider.text.toString().trim(),
            memberNumber = memberNumber.text.toString().trim(),
            emergencyContactName = emergencyContactName.text.toString().trim(),
            emergencyContactPhone = emergencyContactPhone.text.toString().trim(),
            notes = notes.text.toString().trim(),
            shareEnabled = shareEnabled.isChecked,
            publicToken = publicToken
        )
        setBusy(true, "Guardando ficha médica…")
        executor.execute {
            try {
                val saved = api.saveMedicalProfile(s, p)
                runOnUiThread {
                    fill(saved)
                    setBusy(false, if (saved.shareEnabled) "Ficha guardada y preparada para CERCA ID." else "Ficha guardada. El acceso NFC está desactivado.")
                    toast("Ficha médica guardada.")
                    refreshNfcStatus()
                }
            } catch (e: Exception) {
                runOnUiThread { setBusy(false, e.message ?: "No pudimos guardar la ficha.") }
            }
        }
    }

    private fun syncNfcPrefs(p: SupabaseApi.MedicalProfile) {
        val enabled = p.shareEnabled && p.publicToken.isNotBlank()
        getSharedPreferences("cerca_medical_nfc", MODE_PRIVATE).edit()
            .putBoolean("share_enabled", enabled)
            .putString("public_token", if (enabled) p.publicToken else "")
            .apply()
    }

    private fun openPublicProfile() {
        if (publicToken.isBlank() || !shareEnabled.isChecked) {
            toast("Guardá y activá el acceso de emergencia primero.")
            return
        }
        val url = SupabaseApi.BASE_URL + "/functions/v1/cerca-medical-card?id=" + Uri.encode(publicToken)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    @Suppress("DEPRECATION")
    private fun requestNfcPreference() {
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null) { toast("Este teléfono no tiene NFC."); return }
        if (!adapter.isEnabled) { toast("Activá NFC en Ajustes y volvé a probar."); return }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
            toast("Este teléfono no admite emulación NFC HCE.")
            return
        }
        val component = ComponentName(this, CercaNfcCardService::class.java)
        val intent = Intent(CardEmulation.ACTION_CHANGE_DEFAULT)
            .putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_OTHER)
            .putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT, component)
        try {
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
            else toast("Android no permite elegir una app NFC predeterminada en este equipo.")
        } catch (_: Exception) {
            toast("Este equipo no permite fijar CERCA como servicio NFC predeterminado.")
        }
    }

    private fun refreshNfcStatus() {
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null) { nfcStatus.text = "NFC: no disponible en este teléfono."; return }
        val hce = packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        if (!adapter.isEnabled) { nfcStatus.text = "NFC: apagado. Activá NFC para usar CERCA ID."; return }
        if (!hce) { nfcStatus.text = "NFC activo, pero este teléfono no admite HCE."; return }
        val component = ComponentName(this, CercaNfcCardService::class.java)
        val card = CardEmulation.getInstance(adapter)
        val preferred = runCatching { card.isDefaultServiceForAid(component, NDEF_AID) }.getOrDefault(false)
        val ready = getSharedPreferences("cerca_medical_nfc", MODE_PRIVATE).getBoolean("share_enabled", false)
        nfcStatus.text = buildString {
            append(if (ready) "Ficha NFC preparada. " else "Guardá la ficha y activá el acceso NFC. ")
            append(if (preferred) "CERCA tiene prioridad para el canal NFC." else "Android puede pedir elegir CERCA si otro servicio usa el mismo canal NFC.")
        }
    }

    private fun setBusy(busy: Boolean, message: String) {
        saveButton.isEnabled = !busy
        status.text = message
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object { private const val NDEF_AID = "D2760000850101" }
}
