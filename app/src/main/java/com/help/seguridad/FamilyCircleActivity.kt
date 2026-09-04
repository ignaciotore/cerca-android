package com.help.seguridad

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.concurrent.Executors

class FamilyCircleActivity : AppCompatActivity() {
    private val api = SupabaseApi()
    private lateinit var sessionStore: SecureSessionStore
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private var session: SupabaseApi.Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionStore = SecureSessionStore(this)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#F7FAF9")) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(36))
        }
        scroll.addView(root)
        setContentView(scroll)
        loadState()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun loadState() {
        root.removeAllViews()
        root.addView(title("Mi Red CERCA", 29f))
        root.addView(body("Cargando tu red de confianza…"))
        executor.execute {
            try {
                var s = sessionStore.load() ?: throw IllegalStateException("Iniciá sesión nuevamente.")
                if (api.isSessionNearExpiry(s)) {
                    s = api.refreshSession(s)
                    sessionStore.save(s)
                }
                session = s
                val state = api.fetchNetworkState(s)
                runOnUiThread { render(state) }
            } catch (e: Exception) {
                runOnUiThread { renderError(e.message ?: "No pudimos cargar Mi Red CERCA.") }
            }
        }
    }

    private fun render(state: JSONObject) {
        root.removeAllViews()
        root.addView(title("Mi Red CERCA", 29f))
        root.addView(body("CERCA funciona aunque tus contactos no tengan la app. Cuando también tienen CERCA, reciben alertas prioritarias y pueden acceder a las funciones que vos autorices."))

        val alerts = state.optJSONArray("incoming_alerts")
        if (alerts != null && alerts.length() > 0) {
            val alertCard = card()
            alertCard.setBackgroundColor(Color.parseColor("#FFF2EF"))
            alertCard.addView(title("Alertas activas", 20f))
            for (i in 0 until alerts.length()) {
                val a = alerts.optJSONObject(i) ?: continue
                val person = a.optString("person_name", "Una persona de tu Red CERCA")
                val mode = if (a.optString("mode") == "silent") "SOS silencioso" else "SOS"
                alertCard.addView(body("🚨 $person necesita ayuda · $mode"))
                alertCard.addView(primary("VER ALERTA") { openAlert(a) })
            }
            root.addView(alertCard, margin())
        }

        val outgoing = state.optJSONArray("outgoing")
        val count = outgoing?.length() ?: 0
        root.addView(TextView(this).apply {
            text = "$count de 4 contactos configurados"
            textSize = 14f
            setTextColor(Color.parseColor("#0B5960"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(2))
        })

        if (outgoing != null && outgoing.length() > 0) {
            for (i in 0 until outgoing.length()) {
                val item = outgoing.optJSONObject(i) ?: continue
                root.addView(outgoingCard(item), margin())
            }
        }

        if (count < 4) {
            val invite = card()
            invite.addView(title("Sumar un contacto a tu Red CERCA", 20f))
            invite.addView(body("Generá una invitación y enviala por WhatsApp. Cuando la otra persona la acepte desde CERCA, queda vinculada a tu red."))
            val name = EditText(this).apply { hint = "Nombre del contacto" }
            val relationship = EditText(this).apply { hint = "Relación · ej. Mamá, Pareja, Amigo" }
            invite.addView(name)
            invite.addView(relationship)

            var permission = "emergency"
            val permissionLabel = body("Ficha médica: solo durante una emergencia")
            invite.addView(permissionLabel)
            invite.addView(secondary("CAMBIAR PERMISO DE FICHA") {
                val options = arrayOf("No compartir", "Solo durante una emergencia", "Siempre")
                AlertDialog.Builder(this)
                    .setTitle("Compartir mi ficha médica")
                    .setItems(options) { _, which ->
                        permission = when (which) { 0 -> "never"; 2 -> "always"; else -> "emergency" }
                        permissionLabel.text = "Ficha médica: " + when (permission) {
                            "never" -> "no compartir"
                            "always" -> "siempre disponible"
                            else -> "solo durante una emergencia"
                        }
                    }.show()
            })
            invite.addView(primary("GENERAR INVITACIÓN") {
                createInvite(name.text.toString(), relationship.text.toString(), permission)
            })
            root.addView(invite, margin())
        }

        val join = card()
        join.addView(title("¿Te invitaron a una Red CERCA?", 20f))
        join.addView(body("Ingresá el código que recibiste por WhatsApp."))
        val code = EditText(this).apply {
            hint = "Código de invitación"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
        }
        join.addView(code)
        join.addView(primary("ACEPTAR INVITACIÓN") { acceptCode(code.text.toString()) })
        root.addView(join, margin())

        val incoming = state.optJSONArray("incoming")
        if (incoming != null && incoming.length() > 0) {
            val shared = card()
            shared.addView(title("Personas que te tienen en su Red CERCA", 20f))
            shared.addView(body("Desde acá podés ver la ficha médica cuando esa persona te dio permiso."))
            for (i in 0 until incoming.length()) {
                val item = incoming.optJSONObject(i) ?: continue
                val name = item.optString("owner_name", "Contacto CERCA")
                val access = item.optString("medical_access", "never")
                val label = when (access) {
                    "always" -> "Ficha disponible siempre"
                    "emergency" -> "Ficha disponible durante una emergencia"
                    else -> "No comparte ficha"
                }
                shared.addView(title(name, 17f))
                shared.addView(body(label))
                if (access != "never") {
                    shared.addView(secondary("VER FICHA MÉDICA") {
                        showMedical(item.optString("owner_user_id"), name)
                    })
                }
            }
            root.addView(shared, margin())
        }

        root.addView(secondary("ACTUALIZAR") { loadState() }, margin())
        root.addView(secondary("VOLVER") { finish() }, margin())
    }

    private fun outgoingCard(item: JSONObject): LinearLayout {
        val c = card()
        val name = item.optString("display_name", "Contacto")
        val status = item.optString("status", "pending")
        val relationship = item.optString("relationship", "Contacto")
        val hasCerca = item.optBoolean("has_cerca", false)
        val access = item.optString("medical_access", "emergency")
        c.addView(title(name, 18f))
        c.addView(body("$relationship · " + if (hasCerca) "CERCA activo ✓" else "Invitación pendiente"))
        c.addView(body("Ficha médica: " + when (access) {
            "always" -> "siempre"
            "never" -> "no compartir"
            else -> "solo en emergencia"
        }))

        if (status == "pending") {
            val code = item.optString("invite_code", "")
            if (code.isNotBlank()) {
                c.addView(body("Código: ${code.uppercase()}"))
                c.addView(primary("ENVIAR POR WHATSAPP") { shareInvite(name, code) })
            }
        } else {
            c.addView(secondary("CAMBIAR ACCESO A FICHA") {
                chooseMedicalAccess(item.optString("id"), access)
            })
        }
        c.addView(danger(if (status == "pending") "CANCELAR INVITACIÓN" else "QUITAR DE MI RED") {
            confirmRemove(item.optString("id"), name)
        })
        return c
    }

    private fun createInvite(displayName: String, relationship: String, permission: String) {
        val cleanName = displayName.trim()
        if (cleanName.length < 2) { toast("Ingresá el nombre del contacto."); return }
        val s = session ?: run { toast("Iniciá sesión nuevamente."); return }
        executor.execute {
            try {
                val result = api.createNetworkInvite(s, cleanName, relationship, permission)
                val code = result.optString("invite_code", "")
                runOnUiThread {
                    if (code.isBlank()) toast("La invitación se creó, pero no encontramos el código.")
                    else {
                        toast("Invitación creada.")
                        shareInvite(cleanName, code)
                    }
                    loadState()
                }
            } catch (e: Exception) {
                runOnUiThread { toast(e.message ?: "No pudimos crear la invitación.") }
            }
        }
    }

    private fun acceptCode(raw: String) {
        val code = raw.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (code.length < 6) { toast("Ingresá el código de invitación."); return }
        post({ s -> api.acceptNetworkInviteCode(s, code) }, "Ya sos parte de esa Red CERCA.")
    }

    private fun chooseMedicalAccess(linkId: String, current: String) {
        val options = arrayOf("No compartir", "Solo durante una emergencia", "Siempre")
        val checked = when (current) { "never" -> 0; "always" -> 2; else -> 1 }
        AlertDialog.Builder(this)
            .setTitle("Compartir mi ficha médica")
            .setSingleChoiceItems(options, checked) { dialog, which ->
                val access = when (which) { 0 -> "never"; 2 -> "always"; else -> "emergency" }
                dialog.dismiss()
                post({ s -> api.setNetworkMedicalAccess(s, linkId, access) }, "Permiso actualizado.")
            }.show()
    }

    private fun confirmRemove(linkId: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle("Quitar a $name")
            .setMessage("Dejará de formar parte de tu Red CERCA.")
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("QUITAR") { _, _ ->
                post({ s -> api.removeNetworkLink(s, linkId) }, "Contacto quitado.")
            }.show()
    }

    private fun shareInvite(displayName: String, code: String) {
        val message = """
            Hola $displayName, te agregué a mi Red CERCA.

            Instalá CERCA desde:
            https://cerca-cuidarte.vercel.app

            Después entrá a Mi Red CERCA e ingresá este código:

            ${code.uppercase()}

            Si formamos parte de la misma Red CERCA, podés recibir mis alertas prioritarias y las funciones que yo autorice.
        """.trimIndent()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        try {
            startActivity(Intent.createChooser(send, "Compartir invitación CERCA"))
        } catch (_: Exception) {
            toast("No pude abrir la opción para compartir.")
        }
    }

    private fun openAlert(alert: JSONObject) {
        startActivity(Intent(this, EmergencyAlertActivity::class.java).apply {
            putExtra("emergency_id", alert.optString("id"))
            putExtra("owner_user_id", alert.optString("owner_user_id"))
            putExtra("person_name", alert.optString("person_name", "Contacto CERCA"))
            putExtra("mode", alert.optString("mode", "normal"))
            putExtra("latitude", alert.optString("latitude", ""))
            putExtra("longitude", alert.optString("longitude", ""))
            putExtra("medical_access", alert.optString("medical_access", "never"))
        })
    }

    private fun showMedical(userId: String, personName: String) {
        if (userId.isBlank()) return
        val s = session ?: return
        toast("Cargando ficha de $personName…")
        executor.execute {
            try {
                val result = api.fetchNetworkMedical(s, userId)
                runOnUiThread { showMedicalDialog(personName, result) }
            } catch (e: Exception) {
                runOnUiThread { toast(e.message ?: "No pudimos cargar la ficha.") }
            }
        }
    }

    private fun showMedicalDialog(personName: String, result: JSONObject) {
        val m = result.optJSONObject("medical")
        if (m == null) { toast("Esta persona todavía no completó su ficha médica."); return }
        fun field(label: String, key: String): String {
            val value = m.optString(key, "").trim()
            return if (value.isBlank()) "" else "$label: $value\n"
        }
        val text = buildString {
            append(field("Nombre", "full_name"))
            append(field("Nacimiento", "birth_date"))
            append(field("Grupo sanguíneo", "blood_type"))
            append(field("Alergias", "allergies"))
            append(field("Medicaciones importantes", "medications"))
            append(field("Condiciones", "conditions"))
            append(field("Cobertura", "health_provider"))
            append(field("N.º afiliado", "member_number"))
            val ec = m.optString("emergency_contact_name", "").trim()
            val ep = m.optString("emergency_contact_phone", "").trim()
            if (ec.isNotBlank() || ep.isNotBlank()) append("Contacto de emergencia: $ec $ep\n")
            append(field("Notas", "notes"))
        }.trim()
        AlertDialog.Builder(this)
            .setTitle("Ficha médica · $personName")
            .setMessage(text.ifBlank { "La ficha todavía está vacía." })
            .setPositiveButton("CERRAR", null)
            .show()
    }

    private fun post(call: (SupabaseApi.Session) -> JSONObject, success: String) {
        val s = session ?: run { toast("Iniciá sesión nuevamente."); return }
        executor.execute {
            try {
                call(s)
                runOnUiThread { toast(success); loadState() }
            } catch (e: Exception) {
                runOnUiThread { toast(e.message ?: "No pudimos completar la operación.") }
            }
        }
    }

    private fun renderError(message: String) {
        root.removeAllViews()
        root.addView(title("Mi Red CERCA", 29f))
        root.addView(body(message))
        root.addView(secondary("REINTENTAR") { loadState() }, margin())
        root.addView(secondary("VOLVER") { finish() }, margin())
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(16), dp(17), dp(16))
        setBackgroundResource(R.drawable.card_bg)
    }

    private fun title(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.parseColor("#0B5960"))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun body(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.parseColor("#657579"))
        setPadding(0, dp(6), 0, dp(8))
    }

    private fun primary(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0B5960"))
        setOnClickListener { action() }
    }

    private fun secondary(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        setTextColor(Color.parseColor("#0B5960"))
        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DDF2F0"))
        setOnClickListener { action() }
    }

    private fun danger(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        setTextColor(Color.parseColor("#B54F45"))
        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F8ECE8"))
        setOnClickListener { action() }
    }

    private fun margin() = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(12), 0, 0) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
