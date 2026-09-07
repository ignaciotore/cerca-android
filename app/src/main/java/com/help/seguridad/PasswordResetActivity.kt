package com.help.seguridad

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class PasswordResetActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var pass1: EditText
    private lateinit var pass2: EditText
    private lateinit var status: TextView
    private lateinit var saveButton: Button
    private var accessToken: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val params = intent?.data?.fragment.orEmpty().split("&").mapNotNull {
            val p = it.split("=", limit = 2)
            if (p.size == 2) java.net.URLDecoder.decode(p[0], "UTF-8") to java.net.URLDecoder.decode(p[1], "UTF-8") else null
        }.toMap()
        accessToken = params["access_token"].orEmpty()
        setContentView(buildUi())
        if (accessToken.isBlank()) {
            status.text = "El enlace venció o no es válido. Volvé a CERCA y pedí uno nuevo."
            saveButton.isEnabled = false
        }
    }

    private fun buildUi(): ScrollView {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(34), dp(24), dp(34))
            setBackgroundColor(Color.rgb(247,250,249))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply { text="CERCA"; textSize=27f; setTextColor(Color.rgb(11,89,96)); setTypeface(typeface,android.graphics.Typeface.BOLD); letterSpacing=.12f })
        root.addView(TextView(this).apply { text="Elegí una nueva contraseña"; textSize=30f; setTextColor(Color.rgb(11,89,96)); setTypeface(typeface,android.graphics.Typeface.BOLD); setPadding(0,dp(34),0,dp(8)) })
        root.addView(TextView(this).apply { text="Usá al menos 8 caracteres. Cuando la guardes, podés volver a iniciar sesión normalmente."; textSize=17f; setTextColor(Color.rgb(102,119,123)); setPadding(0,0,0,dp(24)) })
        pass1=EditText(this).apply { hint="Nueva contraseña"; textSize=17f; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setPadding(dp(14),dp(14),dp(14),dp(14)); setBackgroundResource(R.drawable.input_bg) }
        pass2=EditText(this).apply { hint="Repetir contraseña"; textSize=17f; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setPadding(dp(14),dp(14),dp(14),dp(14)); setBackgroundResource(R.drawable.input_bg) }
        root.addView(pass1,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(pass2,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{topMargin=dp(12)})
        saveButton=Button(this).apply { text="GUARDAR NUEVA CONTRASEÑA"; textSize=16f; setTextColor(Color.WHITE); setTypeface(typeface,android.graphics.Typeface.BOLD); setBackgroundColor(Color.rgb(11,89,96)); setOnClickListener{savePassword()} }
        root.addView(saveButton,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(58)).apply{topMargin=dp(20)})
        status=TextView(this).apply { textSize=15f; setTextColor(Color.rgb(65,82,86)); gravity=Gravity.CENTER; setPadding(dp(8),dp(16),dp(8),dp(8)) }
        root.addView(status)
        return scroll
    }

    private fun savePassword() {
        val p1=pass1.text.toString(); val p2=pass2.text.toString()
        if(p1.length<8){status.text="La contraseña debe tener al menos 8 caracteres.";return}
        if(p1!=p2){status.text="Las contraseñas no coinciden.";return}
        saveButton.isEnabled=false; status.text="Guardando…"
        executor.execute {
            try {
                val c=(URL(SupabaseApi.BASE_URL+"/auth/v1/user").openConnection() as HttpURLConnection).apply {
                    requestMethod="PUT"; connectTimeout=8000; readTimeout=8000; doOutput=true
                    setRequestProperty("apikey",SupabaseApi.PUBLISHABLE_KEY)
                    setRequestProperty("Authorization","Bearer $accessToken")
                    setRequestProperty("Content-Type","application/json")
                }
                c.outputStream.use{it.write(org.json.JSONObject().put("password",p1).toString().toByteArray())}
                val ok=c.responseCode in 200..299
                val body=try{(if(ok)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()}catch(_:Exception){""}
                c.disconnect()
                if(!ok)throw IllegalStateException(runCatching{org.json.JSONObject(body).optString("msg")}.getOrNull().orEmpty().ifBlank{"No pudimos cambiar la contraseña."})
                runOnUiThread { status.text="Contraseña actualizada. Ya podés volver a CERCA e iniciar sesión."; saveButton.text="VOLVER A CERCA"; saveButton.isEnabled=true; saveButton.setOnClickListener{finish()}; pass1.isEnabled=false; pass2.isEnabled=false }
            } catch(e:Exception) { runOnUiThread { status.text=e.message?:"No pudimos cambiar la contraseña."; saveButton.isEnabled=true } }
        }
    }
    override fun onDestroy(){executor.shutdownNow();super.onDestroy()}
}
