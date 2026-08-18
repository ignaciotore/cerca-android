package com.help.seguridad

import android.content.ComponentName
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CercaIdTapActivity : AppCompatActivity() {
    private var adapter: NfcAdapter? = null
    private var cardEmulation: CardEmulation? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(247, 250, 249))
        }
        fun label(value: String, size: Float, bold: Boolean = false): TextView = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.rgb(32, 51, 55))
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 12, 0, 12)
        }
        root.addView(label("CERCA ID", 30f, true), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(label("Listo para que un iPhone lea tu ficha", 22f, true), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(label("Dejá este modo activo. CERCA intenta evitar que tu Android actúe como lector y le da prioridad a la ficha médica como tarjeta NFC.", 15f), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        status = label("Preparando NFC…", 16f, true).apply { setTextColor(Color.rgb(11, 89, 96)) }
        root.addView(status, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(label("Para probar: bloqueá el teléfono, encendé la pantalla sin desbloquear y acercá la parte superior del iPhone a la zona NFC de este teléfono.", 14f), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val close = Button(this).apply {
            text = "SALIR DEL MODO CERCA ID"
            setOnClickListener { releaseNfcMode(); finish() }
        }
        root.addView(close, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(root)
        adapter = NfcAdapter.getDefaultAdapter(this)
        cardEmulation = adapter?.let { CardEmulation.getInstance(it) }
    }

    override fun onResume() { super.onResume(); activateNfcMode() }
    override fun onDestroy() { releaseNfcMode(); super.onDestroy() }

    private fun activateNfcMode() {
        val a = adapter
        if (a == null) { status.text = "Este teléfono no tiene NFC."; return }
        if (!a.isEnabled) { status.text = "NFC está apagado. Activá NFC y volvé."; return }
        val ready = getSharedPreferences("cerca_medical_nfc", MODE_PRIVATE).getBoolean("share_enabled", false)
        if (!ready) { status.text = "Primero guardá la ficha médica y activá CERCA ID."; return }
        val component = ComponentName(this, CercaNfcCardService::class.java)
        val preferred = runCatching { cardEmulation?.setPreferredService(this, component) == true }.getOrDefault(false)
        var listenOnly = false
        if (Build.VERSION.SDK_INT >= 35) {
            listenOnly = runCatching {
                a.setDiscoveryTechnology(this, NfcAdapter.FLAG_READER_DISABLE, NfcAdapter.FLAG_LISTEN_KEEP)
                true
            }.getOrDefault(false)
        }
        status.text = when {
            preferred && listenOnly -> "CERCA ID listo · Android quedó en modo escucha NFC."
            preferred -> "CERCA tiene prioridad NFC. Este Android no permitió desactivar completamente el sondeo."
            else -> "No pude dar prioridad NFC a CERCA en este equipo."
        }
    }

    private fun releaseNfcMode() {
        runCatching { cardEmulation?.unsetPreferredService(this) }
        if (Build.VERSION.SDK_INT >= 35) runCatching { adapter?.resetDiscoveryTechnology(this) }
    }
}
