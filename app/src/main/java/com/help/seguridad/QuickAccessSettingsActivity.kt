package com.help.seguridad

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class QuickAccessSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); ui() }
    private fun ui() {
        val scroll=ScrollView(this).apply{setBackgroundColor(Color.parseColor("#F7FAF9"))}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(24),dp(20),dp(36))}
        root.addView(title("Acceso rápido de emergencia",29f))
        root.addView(body("Tené CERCA a mano desde la pantalla principal del teléfono."))
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18));setBackgroundColor(Color.WHITE)}
        card.addView(title("Botón CERCA en la pantalla principal",21f))
        card.addView(body("Agregá un botón grande al inicio del teléfono. Un toque abre la confirmación de 5 segundos y después activa el SOS."))
        card.addView(Button(this).apply{text="AGREGAR BOTÓN CERCA";setTextColor(Color.WHITE);backgroundTintList=ColorStateList.valueOf(Color.parseColor("#0B5960"));setOnClickListener{pinWidget()}})
        root.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(14),0,0)})
        root.addView(Button(this).apply{text="VOLVER";setTextColor(Color.parseColor("#0B5960"));backgroundTintList=ColorStateList.valueOf(Color.parseColor("#E8F2F0"));setOnClickListener{finish()}},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(14),0,0)})
        scroll.addView(root);setContentView(scroll)
    }
    private fun pinWidget(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){val m=getSystemService(AppWidgetManager::class.java);val p=ComponentName(this,HelpWidgetProvider::class.java);if(m!=null&&m.isRequestPinAppWidgetSupported){m.requestPinAppWidget(p,null,null);return}};Toast.makeText(this,"Mantené apretada la pantalla principal y elegí Widgets > CERCA.",Toast.LENGTH_LONG).show()}
    private fun title(t:String,s:Float)=TextView(this).apply{text=t;textSize=s;setTextColor(Color.parseColor("#0B5960"));setTypeface(typeface,android.graphics.Typeface.BOLD)}
    private fun body(t:String)=TextView(this).apply{text=t;textSize=15f;setTextColor(Color.parseColor("#667177"));setPadding(0,dp(7),0,dp(8))}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
