package com.help.seguridad

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class QuickAccessSettingsActivity : AppCompatActivity() {
    private lateinit var prefs: QuickAccessPrefs
    private lateinit var sw: SwitchCompat
    private lateinit var state: TextView
    private lateinit var test: TextView
    private var silent = false
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == QuickAccessPrefs.ACTION_TEST_SUCCESS) {
                test.text = "✓ Prueba correcta. La secuencia física está lista."
                test.setTextColor(Color.parseColor("#31664A"))
            }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = QuickAccessPrefs(this)
        ui()
    }

    override fun onStart() {
        super.onStart()
        if (!registered) {
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(QuickAccessPrefs.ACTION_TEST_SUCCESS),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            registered = true
        }
    }

    override fun onStop() {
        if (registered) {
            try { unregisterReceiver(receiver) } catch (_: Exception) {}
            registered = false
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.pending() && serviceOn()) {
            prefs.setEnabled(true)
            prefs.setPending(false)
        }
        refresh()
    }

    private fun ui() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F7FAF9"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(36))
        }

        root.addView(title("Acceso rápido de emergencia", 29f))
        root.addView(
            body(
                "Activá CERCA sin buscar la app. Siempre tendrás 5 segundos para cancelar antes de enviar la alerta."
            )
        )

        val physical = card()
        physical.addView(title("Botones físicos", 21f))
        physical.addView(
            body(
                "Para pedir ayuda presioná, en menos de 3 segundos: Volumen –  ·  Volumen +  ·  Volumen –  ·  Volumen +. " +
                    "Subir o bajar el volumen normalmente no activa el SOS."
            )
        )

        sw = SwitchCompat(this).apply {
            text = "Activar SOS con botones de volumen"
            textSize = 17f
            setOnCheckedChangeListener { _, on ->
                if (!silent) {
                    if (on) enable()
                    else {
                        prefs.setEnabled(false)
                        prefs.setPending(false)
                        refresh()
                    }
                }
            }
        }
        physical.addView(sw)

        state = body("")
        physical.addView(state)

        physical.addView(
            secondary("PROBAR SECUENCIA") {
                if (!prefs.enabled() || !serviceOn()) {
                    Toast.makeText(
                        this,
                        "Primero activá los botones físicos.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    prefs.beginTest()
                    test.text =
                        "Ahora presioná Volumen – / Volumen + / Volumen – / Volumen +. " +
                            "Esta prueba NO enviará SMS ni iniciará llamadas."
                }
            }
        )

        test = body("La prueba comprueba la secuencia sin enviar ninguna alerta.")
        physical.addView(test)
        root.addView(physical, margin())

        val widgetCard = card()
        widgetCard.addView(title("Botón CERCA en la pantalla principal", 21f))
        widgetCard.addView(
            body(
                "Agregá un botón grande al inicio del teléfono. Un toque abre la confirmación de 5 segundos y después activa el mismo SOS."
            )
        )
        widgetCard.addView(primary("AGREGAR BOTÓN CERCA") { pinWidget() })
        root.addView(widgetCard, margin())

        val safety = card()
        safety.addView(title("Cancelación de seguridad", 21f))
        safety.addView(
            body(
                "Podés tocar CANCELAR. Si la activación fue con los botones físicos, también podés presionar una vez cualquiera de las teclas de volumen."
            )
        )
        root.addView(safety, margin())

        root.addView(secondary("VOLVER") { finish() }, margin())
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun enable() {
        if (serviceOn()) {
            prefs.setEnabled(true)
            refresh()
            return
        }

        silent = true
        sw.isChecked = false
        silent = false

        AlertDialog.Builder(this)
            .setTitle("Permitir acceso rápido de CERCA")
            .setMessage(
                "Para detectar la secuencia Volumen – / Volumen + / Volumen – / Volumen + aunque CERCA no esté abierta, Android exige habilitar un Servicio de accesibilidad. " +
                    "CERCA usa este servicio únicamente para detectar esas teclas y reconocer la secuencia de emergencia. " +
                    "No lee el contenido de la pantalla, no controla otras apps y no recopila ni comparte datos mediante este servicio."
            )
            .setNegativeButton("AHORA NO", null)
            .setPositiveButton("ACEPTO Y CONTINUAR") { _, _ ->
                prefs.setPending(true)
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .show()
    }

    private fun serviceOn(): Boolean {
        val wanted = ComponentName(this, HelpAccessibilityService::class.java).flattenToString()
        return Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty().split(':').any { it.equals(wanted, true) }
    }

    private fun refresh() {
        val sys = serviceOn()
        if (!sys) prefs.setEnabled(false)

        silent = true
        sw.isChecked = prefs.enabled() && sys
        silent = false

        state.text =
            if (prefs.enabled() && sys) "✓ Secuencia física activa"
            else "Requiere habilitar CERCA en Accesibilidad de Android."
    }

    private fun pinWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(AppWidgetManager::class.java)
            val provider = ComponentName(this, HelpWidgetProvider::class.java)
            if (manager != null && manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(provider, null, null)
                return
            }
        }
        Toast.makeText(
            this,
            "Mantené apretada la pantalla principal y elegí Widgets > CERCA.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        setBackgroundColor(Color.WHITE)
    }

    private fun title(t: String, s: Float) = TextView(this).apply {
        text = t
        textSize = s
        setTextColor(Color.parseColor("#0B5960"))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun body(t: String) = TextView(this).apply {
        text = t
        textSize = 15f
        setTextColor(Color.parseColor("#667177"))
        setPadding(0, dp(7), 0, dp(8))
    }

    private fun primary(t: String, a: () -> Unit) = Button(this).apply {
        text = t
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0B5960"))
        setOnClickListener { a() }
    }

    private fun secondary(t: String, a: () -> Unit) = Button(this).apply {
        text = t
        setTextColor(Color.parseColor("#0B5960"))
        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E8F2F0"))
        setOnClickListener { a() }
    }

    private fun margin() = LinearLayout.LayoutParams(-1, -2).apply {
        setMargins(0, dp(14), 0, 0)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
