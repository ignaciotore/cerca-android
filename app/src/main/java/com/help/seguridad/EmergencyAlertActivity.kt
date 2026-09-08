package com.help.seguridad

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.concurrent.Executors

class EmergencyAlertActivity : AppCompatActivity() {
    private val api = SupabaseApi()
    private lateinit var sessionStore: SecureSessionStore
    private val executor = Executors.newSingleThreadExecutor()

    private var emergencyId = ""
    private var ownerUserId = ""
    private var personName = "Contacto CERCA"
    private var latitude = ""
    private var longitude = ""
    private var medicalAccess = "never"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionStore = SecureSessionStore(this)

        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        emergencyId = intent.getStringExtra("emergency_id").orEmpty()
        ownerUserId = intent.getStringExtra("owner_user_id").orEmpty()
        personName = intent.getStringExtra("person_name").orEmpty().ifBlank { "Contacto CERCA" }
        latitude = intent.getStringExtra("latitude").orEmpty()
        longitude = intent.getStringExtra("longitude").orEmpty()
        medicalAccess = if (BuildConfig.HEALTH_FEATURES) intent.getStringExtra("medical_access").orEmpty().ifBlank { "never" } else "never"

        render()
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        emergencyId = newIntent.getStringExtra("emergency_id").orEmpty()
        ownerUserId = newIntent.getStringExtra("owner_user_id").orEmpty()
        personName = newIntent.getStringExtra("person_name").orEmpty().ifBlank { "Contacto CERCA" }
        latitude = newIntent.getStringExtra("latitude").orEmpty()
        longitude = newIntent.getStringExtra("longitude").orEmpty()
        medicalAccess = if (BuildConfig.HEALTH_FEATURES) newIntent.getStringExtra("medical_access").orEmpty().ifBlank { "never" } else "never"
        render()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun render() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#FFF8F6")) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "🚨 ALERTA CERCA"
            textSize = 18f
            setTextColor(Color.parseColor("#B54F45"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "$personName necesita ayuda"
            textSize = 30f
            setTextColor(Color.parseColor("#173B46"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(6))
        })

        val mode = if (intent.getStringExtra("mode") == "silent") {
            "La persona activó un SOS silencioso."
        } else {
            "La persona activó un pedido de ayuda."
        }
        root.addView(TextView(this).apply {
            text = mode
            textSize = 16f
            setTextColor(Color.parseColor("#59696E"))
            setPadding(0, 0, 0, dp(22))
        })

        root.addView(primary("YA LO VI") { markSeen() })

        if (latitude.isNotBlank() && longitude.isNotBlank()) {
            root.addView(primary("VER UBICACIÓN") { openLocation() }, topMargin())
        }

        if (BuildConfig.HEALTH_FEATURES && medicalAccess != "never" && ownerUserId.isNotBlank()) {
            root.addView(secondary("VER FICHA MÉDICA") { loadMedical() }, topMargin())
        }

        root.addView(secondary("CERRAR") { finish() }, topMargin())
        setContentView(scroll)
    }

    private fun markSeen() {
        if (emergencyId.isBlank()) { finish(); return }
        val s = sessionStore.load()
        if (s == null) {
            toast("Abrí CERCA e iniciá sesión para confirmar la alerta.")
            return
        }
        executor.execute {
            try {
                var active = s
                if (api.isSessionNearExpiry(active)) {
                    active = api.refreshSession(active)
                    sessionStore.save(active)
                }
                api.markNetworkEmergencySeen(active, emergencyId)
                runOnUiThread {
                    toast("Confirmaste que viste la alerta.")
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread { toast(e.message ?: "No pudimos confirmar la alerta.") }
            }
        }
    }

    private fun openLocation() {
        val uri = Uri.parse("https://maps.google.com/?q=${Uri.encode("$latitude,$longitude")}")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            toast("No pude abrir la ubicación.")
        }
    }

    private fun loadMedical() {
        val s = sessionStore.load()
        if (s == null) {
            toast("Iniciá sesión para ver la ficha médica.")
            return
        }
        executor.execute {
            try {
                var active = s
                if (api.isSessionNearExpiry(active)) {
                    active = api.refreshSession(active)
                    sessionStore.save(active)
                }
                val result = api.fetchNetworkMedical(active, ownerUserId)
                runOnUiThread { showMedical(result) }
            } catch (e: Exception) {
                runOnUiThread { toast(e.message ?: "No pudimos cargar la ficha médica.") }
            }
        }
    }

    private fun showMedical(result: JSONObject) {
        val m = result.optJSONObject("medical")
        if (m == null) {
            toast("La ficha médica todavía está vacía.")
            return
        }
        fun f(label: String, key: String): String {
            val value = m.optString(key, "").trim()
            return if (value.isBlank()) "" else "$label: $value\n"
        }
        val text = buildString {
            append(f("Nombre", "full_name"))
            append(f("Nacimiento", "birth_date"))
            append(f("Grupo sanguíneo", "blood_type"))
            append(f("Alergias", "allergies"))
            append(f("Medicaciones importantes", "medications"))
            append(f("Condiciones", "conditions"))
            append(f("Cobertura", "health_provider"))
            append(f("N.º afiliado", "member_number"))
            val ec = m.optString("emergency_contact_name", "").trim()
            val ep = m.optString("emergency_contact_phone", "").trim()
            if (ec.isNotBlank() || ep.isNotBlank()) append("Contacto de emergencia: $ec $ep\n")
            append(f("Notas", "notes"))
        }.trim()

        AlertDialog.Builder(this)
            .setTitle("Ficha médica · $personName")
            .setMessage(text.ifBlank { "La ficha médica todavía está vacía." })
            .setPositiveButton("CERRAR", null)
            .show()
    }

    private fun primary(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        textSize = 16f
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0B5960"))
        setOnClickListener { action() }
    }

    private fun secondary(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        textSize = 15f
        setTextColor(Color.parseColor("#0B5960"))
        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DDF2F0"))
        setOnClickListener { action() }
    }

    private fun topMargin() = LinearLayout.LayoutParams(-1, dp(56)).apply { setMargins(0, dp(10), 0, 0) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
