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
import android.view.KeyEvent
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class QuickAccessSettingsActivity:AppCompatActivity(){
    private lateinit var prefs:QuickAccessPrefs;private lateinit var sw:SwitchCompat;private lateinit var state:TextView;private lateinit var test:TextView;private lateinit var keys:Spinner;private lateinit var counts:Spinner;private var silent=false;private var registered=false
    private val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){if(i?.action==QuickAccessPrefs.ACTION_TEST_SUCCESS){test.text="✓ Prueba correcta. El botón físico está listo.";test.setTextColor(Color.parseColor("#31664A"))}}}
    override fun onCreate(b:Bundle?){super.onCreate(b);prefs=QuickAccessPrefs(this);ui()}
    override fun onStart(){super.onStart();if(!registered){ContextCompat.registerReceiver(this,receiver,IntentFilter(QuickAccessPrefs.ACTION_TEST_SUCCESS),ContextCompat.RECEIVER_NOT_EXPORTED);registered=true}}
    override fun onStop(){if(registered){try{unregisterReceiver(receiver)}catch(_:Exception){};registered=false};super.onStop()}
    override fun onResume(){super.onResume();if(prefs.pending()&&serviceOn()){prefs.setEnabled(true);prefs.setPending(false)};refresh()}
    private fun ui(){
        val scroll=ScrollView(this).apply{setBackgroundColor(Color.parseColor("#FAF7F2"))};val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(24),dp(20),dp(36))}
        root.addView(title("Acceso rápido de emergencia",29f));root.addView(body("Configurá formas de activar H.E.L.P sin buscar la app. Siempre tendrás 5 segundos para cancelar antes de enviar la alerta."))
        val physical=card();physical.addView(title("Botón físico",21f));physical.addView(body("Elegí una tecla lateral y cuántas veces presionarla rápidamente."))
        sw=SwitchCompat(this).apply{text="Activar SOS con botón de volumen";textSize=17f;setOnCheckedChangeListener{_,on->if(!silent){if(on)enable()else{prefs.setEnabled(false);prefs.setPending(false);refresh()}}}};physical.addView(sw)
        state=body("");physical.addView(state);physical.addView(label("Tecla"));keys=Spinner(this);keys.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("Volumen –","Volumen +"));keys.setSelection(if(prefs.key()==KeyEvent.KEYCODE_VOLUME_DOWN)0 else 1);physical.addView(keys)
        physical.addView(label("Cantidad de pulsaciones"));counts=Spinner(this);counts.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("3 veces","4 veces · recomendado","5 veces"));counts.setSelection(prefs.count()-3);physical.addView(counts)
        physical.addView(primary("GUARDAR CONFIGURACIÓN"){save();Toast.makeText(this,"Configuración guardada.",Toast.LENGTH_SHORT).show();refresh()})
        physical.addView(secondary("PROBAR BOTÓN FÍSICO"){save();if(!prefs.enabled()||!serviceOn()){Toast.makeText(this,"Primero activá el botón físico.",Toast.LENGTH_LONG).show()}else{prefs.beginTest();val k=if(prefs.key()==KeyEvent.KEYCODE_VOLUME_DOWN)"Volumen –" else "Volumen +";test.text="Ahora presioná "+k+" "+prefs.count()+" veces rápidamente. Esta prueba NO enviará una alerta."}})
        test=body("La prueba comprueba la combinación sin enviar SMS ni iniciar llamadas.");physical.addView(test);root.addView(physical,margin())
        val widgetCard=card();widgetCard.addView(title("Botón H.E.L.P en la pantalla principal",21f));widgetCard.addView(body("Agregá un botón grande al inicio del teléfono. Un toque abre la confirmación de 5 segundos y después activa el mismo SOS."));widgetCard.addView(primary("AGREGAR BOTÓN H.E.L.P"){pinWidget()});root.addView(widgetCard,margin())
        val safety=card();safety.addView(title("Cancelación de seguridad",21f));safety.addView(body("Podés tocar CANCELAR. Si usaste la tecla física, también podés presionar una vez la tecla de volumen opuesta."));root.addView(safety,margin());root.addView(secondary("VOLVER"){finish()},margin());scroll.addView(root);setContentView(scroll)
    }
    private fun save(){prefs.setKey(if(keys.selectedItemPosition==0)KeyEvent.KEYCODE_VOLUME_DOWN else KeyEvent.KEYCODE_VOLUME_UP);prefs.setCount(counts.selectedItemPosition+3)}
    private fun enable(){save();if(serviceOn()){prefs.setEnabled(true);refresh();return};silent=true;sw.isChecked=false;silent=false;AlertDialog.Builder(this).setTitle("Permitir botón físico de H.E.L.P").setMessage("Para detectar únicamente las pulsaciones de Volumen aunque H.E.L.P no esté abierta, Android exige habilitar un Servicio de accesibilidad. H.E.L.P usa este servicio solo para detectar la tecla y la cantidad de pulsaciones que vos configuraste. No lee el contenido de la pantalla, no toca otras apps y no recopila datos mediante este servicio.").setNegativeButton("AHORA NO",null).setPositiveButton("ACEPTO Y CONTINUAR"){_,_->prefs.setPending(true);startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}.show()}
    private fun serviceOn():Boolean{val wanted=ComponentName(this,HelpAccessibilityService::class.java).flattenToString();return Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().split(':').any{it.equals(wanted,true)}}
    private fun refresh(){val sys=serviceOn();if(!sys)prefs.setEnabled(false);silent=true;sw.isChecked=prefs.enabled()&&sys;silent=false;state.text=if(prefs.enabled()&&sys)"✓ Botón físico activo" else "Requiere habilitar H.E.L.P en Accesibilidad de Android."}
    private fun pinWidget(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){val m=getSystemService(AppWidgetManager::class.java);val provider=ComponentName(this,HelpWidgetProvider::class.java);if(m!=null&&m.isRequestPinAppWidgetSupported){m.requestPinAppWidget(provider,null,null);return}};Toast.makeText(this,"Mantené apretada la pantalla principal y elegí Widgets > H.E.L.P.",Toast.LENGTH_LONG).show()}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18));setBackgroundColor(Color.WHITE)}
    private fun title(t:String,s:Float)=TextView(this).apply{text=t;textSize=s;setTextColor(Color.parseColor("#173B46"));setTypeface(typeface,android.graphics.Typeface.BOLD)}
    private fun body(t:String)=TextView(this).apply{text=t;textSize=15f;setTextColor(Color.parseColor("#667177"));setPadding(0,dp(7),0,dp(8))}
    private fun label(t:String)=TextView(this).apply{text=t;textSize=15f;setTypeface(typeface,android.graphics.Typeface.BOLD);setPadding(0,dp(10),0,dp(2))}
    private fun primary(t:String,a:()->Unit)=Button(this).apply{text=t;setTextColor(Color.WHITE);backgroundTintList=ColorStateList.valueOf(Color.parseColor("#173B46"));setOnClickListener{a()}}
    private fun secondary(t:String,a:()->Unit)=Button(this).apply{text=t;setTextColor(Color.parseColor("#173B46"));backgroundTintList=ColorStateList.valueOf(Color.parseColor("#EEE8E0"));setOnClickListener{a()}}
    private fun margin()=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(14),0,0)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
