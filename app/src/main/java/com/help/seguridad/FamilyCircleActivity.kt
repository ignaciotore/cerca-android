package com.help.seguridad

import android.content.res.ColorStateList
import android.graphics.Color
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
    private lateinit var scroll: ScrollView
    private lateinit var root: LinearLayout
    private var session: SupabaseApi.Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionStore = SecureSessionStore(this)
        scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#F7FAF9")) }
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
        root.addView(title("Mi Círculo CERCA", 29f))
        root.addView(body("Cargando tu plan familiar…"))
        executor.execute {
            try {
                var s = sessionStore.load() ?: throw IllegalStateException("Iniciá sesión nuevamente.")
                if (api.isSessionNearExpiry(s)) {
                    s = api.refreshSession(s)
                    sessionStore.save(s)
                }
                session = s
                val state = api.fetchFamilyState(s)
                runOnUiThread { render(state) }
            } catch (e: Exception) {
                runOnUiThread { renderError(e.message ?: "No pudimos cargar Mi Círculo CERCA.") }
            }
        }
    }

    private fun render(state: JSONObject) {
        root.removeAllViews()
        root.addView(title("Mi Círculo CERCA", 29f))
        val plan = state.optString("plan", "individual")
        val beta = state.optBoolean("beta_enabled", false)
        root.addView(body(if (plan == "family") "Plan de prueba activo: CERCA Familiar" else "Plan de prueba activo: CERCA Individual"))

        val invitations = state.optJSONArray("invitations")
        if (invitations != null && invitations.length() > 0) {
            val invCard = card()
            invCard.addView(title("Invitaciones pendientes", 19f))
            for (i in 0 until invitations.length()) {
                val inv = invitations.optJSONObject(i) ?: continue
                val rel = inv.optString("relationship", "Familiar")
                invCard.addView(body("Te invitaron a un Círculo CERCA · " + rel))
                invCard.addView(primary("ACEPTAR INVITACIÓN") { acceptInvite(inv.optString("id")) })
            }
            root.addView(invCard, margin())
        }

        if (!beta) {
            root.addView(body("El modo de prueba familiar está desactivado."), margin())
            root.addView(secondary("VOLVER") { finish() }, margin())
            return
        }

        val group = state.optJSONObject("group")
        if (group == null) {
            val c = card()
            c.addView(title("Todavía no tenés un círculo", 20f))
            c.addView(body(if (plan == "family") "Creá tu círculo e invitá hasta 4 familiares. Con vos, el plan admite hasta 5 personas." else "Activá CERCA Familiar desde Mi cuenta para crear tu círculo."))
            if (plan == "family") c.addView(primary("CREAR MI CÍRCULO CERCA") { createGroup() })
            root.addView(c, margin())
            root.addView(secondary("VOLVER") { finish() }, margin())
            return
        }

        val isOwner = state.optBoolean("is_owner", false)
        val members = state.optJSONArray("members")
        val count = members?.length() ?: 0
        root.addView(TextView(this).apply {
            text = count.toString() + " de 5 personas"
            textSize = 14f
            setTextColor(Color.parseColor("#0B5960"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(2))
        })

        if (members != null) {
            for (i in 0 until members.length()) {
                val m = members.optJSONObject(i) ?: continue
                root.addView(memberCard(m, isOwner), margin())
            }
        }

        if (isOwner && count < 5) {
            val inviteCard = card()
            inviteCard.addView(title("Invitar a un familiar", 20f))
            inviteCard.addView(body("La otra persona instala CERCA, crea su cuenta con este email y acepta la invitación desde Mi Círculo."))
            val email = EditText(this).apply {
                hint = "Email del familiar"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            }
            val relationship = EditText(this).apply { hint = "Relación · ej. Pareja, Mamá, Hijo" }
            inviteCard.addView(email)
            inviteCard.addView(relationship)
            inviteCard.addView(primary("ENVIAR INVITACIÓN") {
                invite(email.text.toString(), relationship.text.toString())
            })
            root.addView(inviteCard, margin())
        }

        if (!isOwner) {
            root.addView(secondary("SALIR DE ESTE CÍRCULO") { confirmLeave() }, margin())
        }
        root.addView(secondary("VOLVER") { finish() }, margin())
    }

    private fun memberCard(m: JSONObject, isOwner: Boolean): LinearLayout {
        val c = card()
        val name = m.optString("display_name", "Integrante")
        val role = m.optString("role", "member")
        val status = m.optString("status", "pending")
        val rel = m.optString("relationship", "Familiar")
        val me = m.optBoolean("is_me", false)
        c.addView(title(name + if (me) " · Vos" else "", 18f))
        c.addView(body((if (role == "owner") "Titular" else rel) + " · " + if (status == "active") "Activo" else "Invitación pendiente"))
        val uid = m.optString("user_id", "")
        if (status == "active" && uid.isNotBlank() && !me) {
            c.addView(secondary("VER FICHA MÉDICA") { showMedical(uid, name) })
        }
        if (isOwner && role != "owner") {
            c.addView(danger(if (status == "pending") "CANCELAR INVITACIÓN" else "QUITAR DEL CÍRCULO") {
                confirmRemove(m.optString("id"), name)
            })
        }
        return c
    }

    private fun createGroup() = post({ s -> api.createFamilyGroup(s) }, "Círculo creado.")

    private fun invite(email: String, relationship: String) {
        if (!email.trim().contains("@")) { toast("Ingresá un email válido."); return }
        post({ s -> api.inviteFamilyMember(s, email, relationship) }, "Invitación guardada.")
    }

    private fun acceptInvite(id: String) = post({ s -> api.acceptFamilyInvite(s, id) }, "Ya sos parte del Círculo CERCA.")

    private fun confirmRemove(id: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle("Quitar a " + name)
            .setMessage("Esta persona dejará de formar parte de tu Círculo CERCA.")
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("QUITAR") { _, _ -> post({ s -> api.removeFamilyMember(s, id) }, "Integrante quitado.") }
            .show()
    }

    private fun confirmLeave() {
        AlertDialog.Builder(this)
            .setTitle("Salir del Círculo CERCA")
            .setMessage("Dejarás de ver la información compartida por este grupo.")
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("SALIR") { _, _ -> post({ s -> api.leaveFamilyGroup(s) }, "Saliste del círculo.") }
            .show()
    }

    private fun showMedical(userId: String, personName: String) {
        val s = session ?: return
        toast("Cargando ficha de " + personName + "…")
        executor.execute {
            try {
                val result = api.fetchFamilyMedical(s, userId)
                runOnUiThread { showMedicalDialog(personName, result) }
            } catch (e: Exception) {
                runOnUiThread { toast(e.message ?: "No pudimos cargar la ficha.") }
            }
        }
    }

    private fun showMedicalDialog(personName: String, result: JSONObject) {
        val m = result.optJSONObject("medical")
        if (m == null) { toast("Ese integrante todavía no completó su ficha médica."); return }
        fun field(label:String,key:String):String {
            val v=m.optString(key,"").trim()
            return if(v.isBlank()) "" else label + ": " + v + System.lineSeparator()
        }
        val text = buildString {
            append(field("Nombre","full_name"))
            append(field("Nacimiento","birth_date"))
            append(field("Grupo sanguíneo","blood_type"))
            append(field("Alergias","allergies"))
            append(field("Condiciones","conditions"))
            append(field("Cobertura","health_provider"))
            append(field("N.º afiliado","member_number"))
            val emergency = m.optString("emergency_contact_name","").trim()
            val phone = m.optString("emergency_contact_phone","").trim()
            if (emergency.isNotBlank() || phone.isNotBlank()) append("Contacto de emergencia: " + emergency + " " + phone + System.lineSeparator())
            append(field("Notas","notes"))
            val meds = result.optJSONArray("medications")
            if (meds != null && meds.length() > 0) {
                appendLine()
                appendLine("MEDICACIÓN")
                for (i in 0 until meds.length()) {
                    val med = meds.optJSONObject(i) ?: continue
                    append("• " + med.optString("name","Medicamento"))
                    val dose = med.optString("dose","")
                    if (dose.isNotBlank()) append(" · " + dose)
                    appendLine()
                }
            }
        }.trim()
        AlertDialog.Builder(this)
            .setTitle("Ficha médica · " + personName)
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
        root.addView(title("Mi Círculo CERCA", 29f))
        root.addView(body(message))
        root.addView(secondary("REINTENTAR") { loadState() }, margin())
        root.addView(secondary("VOLVER") { finish() }, margin())
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(16), dp(17), dp(16))
        setBackgroundResource(R.drawable.card_bg)
    }
    private fun title(textValue:String,size:Float)=TextView(this).apply {
        text=textValue;textSize=size;setTextColor(Color.parseColor("#0B5960"));setTypeface(typeface,android.graphics.Typeface.BOLD)
    }
    private fun body(textValue:String)=TextView(this).apply {
        text=textValue;textSize=14f;setTextColor(Color.parseColor("#657579"));setPadding(0,dp(6),0,dp(8))
    }
    private fun primary(textValue:String, action:()->Unit)=Button(this).apply {
        text=textValue;setTextColor(Color.WHITE);backgroundTintList=ColorStateList.valueOf(Color.parseColor("#0B5960"));setOnClickListener{action()}
    }
    private fun secondary(textValue:String, action:()->Unit)=Button(this).apply {
        text=textValue;setTextColor(Color.parseColor("#0B5960"));backgroundTintList=ColorStateList.valueOf(Color.parseColor("#DDF2F0"));setOnClickListener{action()}
    }
    private fun danger(textValue:String, action:()->Unit)=Button(this).apply {
        text=textValue;setTextColor(Color.parseColor("#B54F45"));backgroundTintList=ColorStateList.valueOf(Color.parseColor("#F8ECE8"));setOnClickListener{action()}
    }
    private fun margin()=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(12),0,0)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_LONG).show()
}
