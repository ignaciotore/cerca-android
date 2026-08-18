package com.help.seguridad

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.Executors

class MedicalProfileActivity : AppCompatActivity() {
    private val api = SupabaseApi()
    private lateinit var sessionStore: SecureSessionStore
    private val executor = Executors.newSingleThreadExecutor()
    private var session: SupabaseApi.Session? = null
    private var publicToken = ""
    private var birthDateIso = ""
    private var emergencyContactName = ""
    private var emergencyContactPhone = ""
    private var legacyMedications = ""

    private lateinit var fullName: EditText
    private lateinit var birthDate: EditText
    private lateinit var bloodType: EditText
    private lateinit var allergies: EditText
    private lateinit var conditions: EditText
    private lateinit var healthProvider: EditText
    private lateinit var memberNumber: EditText
    private lateinit var emergencyContactDisplay: TextView
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
        birthDate.isFocusable = false
        birthDate.isClickable = true
        birthDate.setOnClickListener { showBirthDatePicker() }
        findViewById<Button>(R.id.medicalPickContactButton).setOnClickListener { pickEmergencyContact() }
        findViewById<Button>(R.id.medicalMedicationsButton).setOnClickListener { startActivity(Intent(this, MedicationActivity::class.java)) }
        findViewById<Button>(R.id.medicalBackButton).setOnClickListener { finish() }
        saveButton.setOnClickListener { saveProfile() }
        viewPublicButton.setOnClickListener { openPublicProfile() }
        findViewById<Button>(R.id.nfcPriorityButton).setOnClickListener {
            if (publicToken.isBlank() || !shareEnabled.isChecked) {
                toast("Guardá la ficha y activá el acceso de emergencia primero.")
            } else {
                startActivity(Intent(this, CercaIdTapActivity::class.java))
            }
        }
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
        conditions = findViewById(R.id.medicalConditions)
        healthProvider = findViewById(R.id.medicalHealthProvider)
        memberNumber = findViewById(R.id.medicalMemberNumber)
        emergencyContactDisplay = findViewById(R.id.medicalEmergencyContactDisplay)
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
        birthDateIso = p.birthDate
        birthDate.setText(formatBirthDateForDisplay(p.birthDate))
        bloodType.setText(p.bloodType)
        allergies.setText(p.allergies)
        conditions.setText(p.conditions)
        healthProvider.setText(p.healthProvider)
        memberNumber.setText(p.memberNumber)
        emergencyContactName = p.emergencyContactName
        emergencyContactPhone = p.emergencyContactPhone
        legacyMedications = p.medications
        updateEmergencyContactDisplay()
        notes.setText(p.notes)
        shareEnabled.isChecked = p.shareEnabled
        publicToken = p.publicToken
        syncNfcPrefs(p)
        viewPublicButton.visibility = if (p.publicToken.isBlank() || !p.shareEnabled) View.GONE else View.VISIBLE
    }

    private fun showBirthDatePicker() {
        val initial = parseBirthDate(birthDateIso).let { if (it == null) LocalDate.of(1985, 1, 1) else it }
        DatePickerDialog(this, { _, year, month, day ->
            val selected = LocalDate.of(year, month + 1, day)
            birthDateIso = selected.toString()
            birthDate.setText(selected.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun parseBirthDate(raw: String): LocalDate? {
        val value = raw.trim()
        if (value.isBlank()) return null
        val formats = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
        )
        for (format in formats) {
            try { return LocalDate.parse(value, format) } catch (_: DateTimeParseException) {}
        }
        return null
    }

    private fun formatBirthDateForDisplay(raw: String): String {
        val parsed = parseBirthDate(raw) ?: return raw
        return parsed.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }

    private fun pickEmergencyContact() {
        try {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            startActivityForResult(intent, REQ_MEDICAL_CONTACT)
        } catch (_: Exception) {
            toast("No pude abrir tus contactos.")
        }
    }

    @Deprecated("Legacy activity result is kept for compatibility with the current CERCA project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_MEDICAL_CONTACT || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                emergencyContactName = cursor.getString(0)?.trim().orEmpty()
                emergencyContactPhone = cursor.getString(1)?.trim().orEmpty()
                updateEmergencyContactDisplay()
            }
        }
    }

    private fun updateEmergencyContactDisplay() {
        emergencyContactDisplay.text = if (emergencyContactPhone.isBlank()) {
            "Todavía no elegiste un contacto"
        } else {
            (emergencyContactName.ifBlank { "Contacto de emergencia" }) + " · " + emergencyContactPhone
        }
    }

    private fun saveProfile() {
        val s = session ?: run { toast("Esperá a que termine de cargar tu cuenta."); return }
        val normalizedBirthDate = when {
            birthDate.text.toString().trim().isBlank() -> ""
            birthDateIso.isNotBlank() -> birthDateIso
            else -> parseBirthDate(birthDate.text.toString())?.toString().orEmpty()
        }
        if (birthDate.text.toString().trim().isNotBlank() && normalizedBirthDate.isBlank()) {
            toast("Elegí la fecha de nacimiento desde el calendario.")
            return
        }
        if (shareEnabled.isChecked && fullName.text.toString().trim().isBlank()) {
            toast("Ingresá el nombre para identificar la ficha.")
            return
        }
        val p = SupabaseApi.MedicalProfile(
            fullName = fullName.text.toString().trim(),
            birthDate = normalizedBirthDate,
            bloodType = bloodType.text.toString().trim(),
            allergies = allergies.text.toString().trim(),
            medications = legacyMedications,
            conditions = conditions.text.toString().trim(),
            healthProvider = healthProvider.text.toString().trim(),
            memberNumber = memberNumber.text.toString().trim(),
            emergencyContactName = emergencyContactName,
            emergencyContactPhone = emergencyContactPhone,
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

    private fun refreshNfcStatus() {
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null) { nfcStatus.text = "NFC: no disponible en este teléfono."; return }
        if (!adapter.isEnabled) { nfcStatus.text = "NFC: apagado. Activá NFC para usar CERCA ID."; return }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
            nfcStatus.text = "NFC activo, pero este teléfono no admite HCE."
            return
        }
        val ready = getSharedPreferences("cerca_medical_nfc", MODE_PRIVATE).getBoolean("share_enabled", false)
        nfcStatus.text = if (ready) "Ficha preparada. Usá el modo CERCA ID para probar con un iPhone." else "Guardá la ficha y activá el acceso de emergencia."
    }

    private fun setBusy(busy: Boolean, message: String) {
        saveButton.isEnabled = !busy
        status.text = message
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object { private const val REQ_MEDICAL_CONTACT = 801 }
}
