package com.help.seguridad

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Color
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class FamilyCircleActivity : AppCompatActivity() {
    companion object { private const val REQ_PICK = 710 }
    private val api = SupabaseApi()
    private lateinit var sessionStore: SecureSessionStore
    private lateinit var root: LinearLayout
    private val executor = Executors.newSingleThreadExecutor()
    private var session: SupabaseApi.Session? = null
    private var serverState = JSONObject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionStore = SecureSessionStore(this)
        session = sessionStore.load()
        if (session == null) { finish(); return }
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#F7FAF9")) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(36)) }
        scroll.addView(root); setContentView(scroll)
        loadState()
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }

    private fun prefs() = getSharedPreferences("help_contacts_" + (session?.userId ?: "none"), MODE_PRIVATE)

    private data class C(val slot:Int,val name:String,val phone:String,val access:String)
    private fun contacts(): List<C> {
        val p=prefs(); val out=mutableListOf<C>()
        for(i in 1..4){
            val phone=p.getString("sms"+i+"Phone","").orEmpty()
            if(phone.isNotBlank()) out += C(i,p.getString("sms"+i+"Name","").orEmpty().ifBlank{"Contacto"},phone,p.getString("sms"+i+"MedicalAccess","emergency").orEmpty().ifBlank{"emergency"})
        }
        return out
    }

    private fun loadState(){
        renderLoading()
        val s=session ?: return
        executor.execute {
            try {
                val fresh=if(api.isSessionNearExpiry(s)) api.refreshSession(s) else s
                session=fresh; sessionStore.save(fresh)
                val state=api.fetchNetworkState(fresh)
                runOnUiThread { serverState=state; render() }
            } catch(e:Exception){ runOnUiThread { serverState=JSONObject(); render(); toast("No pudimos sincronizar la Red CERCA. Tus contactos siguen guardados en el teléfono.") } }
        }
    }

    private fun renderLoading(){ root.removeAllViews(); root.addView(title("Tu Red CERCA",30f)); root.addView(body("Cargando tus contactos…")) }

    private fun render(){
        root.removeAllViews()
        root.addView(title("Tu Red CERCA",30f))
        root.addView(body("Agregá hasta 4 contactos. Si alguno también usa CERCA, lo detectamos automáticamente y activamos funciones adicionales."))

        val alerts=serverState.optJSONArray("incoming_alerts")
        if(alerts!=null && alerts.length()>0){
            val aCard=card(); aCard.setBackgroundColor(Color.parseColor("#FFF2EF")); aCard.addView(title("Alertas activas",20f))
            for(i in 0 until alerts.length()){
                val a=alerts.optJSONObject(i) ?: continue
                aCard.addView(body("🚨 " + a.optString("person_name","Un contacto CERCA") + " necesita ayuda"))
                aCard.addView(primary("VER ALERTA") { openAlert(a) })
            }
            root.addView(aCard,margin())
        }

        if(serverState.optString("phone_e164","").isBlank()) root.addView(phoneCard(), margin())

        val local=contacts()
        val count=TextView(this).apply { text=local.size.toString()+" de 4 contactos configurados"; textSize=15f; setTextColor(Color.parseColor("#0B5960")); setTypeface(typeface,android.graphics.Typeface.BOLD); setPadding(0,dp(14),0,dp(8)) }
        root.addView(count)
        if(local.size<4) root.addView(primary("＋  AGREGAR CONTACTO") { pickContact() })

        val remote=serverState.optJSONArray("contacts") ?: serverState.optJSONArray("outgoing")
        for(c in local) root.addView(contactCard(c, remote), margin())

        val info=card(); info.addView(body("Los contactos reciben el SMS aunque no tengan CERCA. Si tienen la app, además reciben la alerta prioritaria y las funciones que autorices.")); root.addView(info,margin())
        root.addView(secondary("ACTUALIZAR ESTADO") { syncAndReload() },margin())
        root.addView(secondary("VOLVER") { finish() },margin())
    }

    private fun phoneCard(): LinearLayout {
        val c=card(); c.addView(title("Completá tu teléfono",20f)); c.addView(body("Lo usamos para que otros usuarios CERCA puedan reconocerte automáticamente cuando te agregan como contacto."))
        val input=EditText(this).apply { hint="Ej. +54 9 11 1234 5678"; inputType=android.text.InputType.TYPE_CLASS_PHONE; setBackgroundResource(R.drawable.input_bg); setPadding(dp(14),dp(12),dp(14),dp(12)) }
        c.addView(input)
        c.addView(primary("GUARDAR MI TELÉFONO") { savePhone(input.text.toString()) })
        return c
    }

    private fun contactCard(c:C, remote:JSONArray?): LinearLayout {
        val rc=findRemote(c.phone,remote); val has=rc?.optBoolean("has_cerca",false)==true
        val box=card()
        val top=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
        val left=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        left.addView(title(c.name,19f)); left.addView(body(c.phone))
        top.addView(left,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        top.addView(TextView(this).apply { text=if(has) "✓ Tiene CERCA" else "Sin CERCA"; textSize=13f; setTypeface(typeface,android.graphics.Typeface.BOLD); setTextColor(Color.parseColor(if(has)"#0B5960" else "#657579")); setPadding(dp(10),dp(7),dp(10),dp(7)); setBackgroundColor(Color.parseColor(if(has)"#DDF2F0" else "#EEF1F1")) })
        box.addView(top)
        val call=prefs().getString("callPhone","").orEmpty()==c.phone
        box.addView(body("SMS activo" + (if(call) "  ·  Llamada activa" else "") + (if(has) "  ·  Alerta CERCA activa" else "")))

        val medical = card().apply {
            setBackgroundColor(Color.parseColor("#F1F7F6"))
            addView(TextView(this@FamilyCircleActivity).apply {
                text = "Ficha médica: " + accessLabel(c.access)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#0B5960"))
            })
            addView(body(if(has) "CERCA compartirá la ficha según este permiso." else "Este permiso quedará preparado y se activará automáticamente si este contacto instala CERCA. La ficha no se envía por SMS."))
            addView(secondary("CAMBIAR FICHA MÉDICA") { chooseAccess(c) })
        }
        box.addView(medical, margin())

        if(!call) box.addView(secondary("USAR PARA LLAMADA") { setCall(c) })
        box.addView(danger("QUITAR CONTACTO") { removeContact(c) })
        return box
    }

    private fun findRemote(phone:String, arr:JSONArray?):JSONObject?{ if(arr==null)return null; for(i in 0 until arr.length()){ val o=arr.optJSONObject(i)?:continue; if(norm(o.optString("phone_e164"))==norm(phone)) return o }; return null }
    private fun accessLabel(v:String)=when(v){"always"->"siempre";"never"->"no compartir";else->"solo en emergencia"}

    private fun chooseAccess(c:C){
        val opts=arrayOf("No compartir","Solo durante una emergencia","Siempre")
        val checked=when(c.access){"never"->0;"always"->2;else->1}
        AlertDialog.Builder(this).setTitle("Ficha médica para "+c.name).setSingleChoiceItems(opts,checked){d,w->
            val v=when(w){0->"never";2->"always";else->"emergency"}; d.dismiss(); prefs().edit().putString("sms"+c.slot+"MedicalAccess",v).apply(); syncAndReload()
        }.show()
    }

    private fun setCall(c:C){ prefs().edit().putString("callName",c.name).putString("callPhone",c.phone).apply(); syncAndReload() }

    private fun removeContact(c:C){
        AlertDialog.Builder(this).setTitle("Quitar a "+c.name).setMessage("Dejará de recibir tus alertas CERCA.").setNegativeButton("CANCELAR",null).setPositiveButton("QUITAR"){_,_->
            val p=prefs(); val wasCall=p.getString("callPhone","").orEmpty()==c.phone
            p.edit().putString("sms"+c.slot+"Name","").putString("sms"+c.slot+"Phone","").putString("sms"+c.slot+"MedicalAccess","emergency").apply()
            if(wasCall){ val next=contacts().firstOrNull(); p.edit().putString("callName",next?.name ?: "").putString("callPhone",next?.phone ?: "").apply() }
            compactSlots(); syncAndReload()
        }.show()
    }

    private fun compactSlots(){
        val old=contacts(); val p=prefs().edit()
        for(i in 1..4){p.putString("sms"+i+"Name","").putString("sms"+i+"Phone","").putString("sms"+i+"MedicalAccess","emergency")}
        old.forEachIndexed{i,c-> val n=i+1; p.putString("sms"+n+"Name",c.name).putString("sms"+n+"Phone",c.phone).putString("sms"+n+"MedicalAccess",c.access)}; p.apply()
    }

    private fun pickContact(){ if(contacts().size>=4){toast("Ya tenés 4 contactos.");return}; try{startActivityForResult(Intent(Intent.ACTION_PICK,ContactsContract.CommonDataKinds.Phone.CONTENT_URI),REQ_PICK)}catch(_:Exception){toast("No pude abrir tus contactos.")} }

    @Deprecated("compat")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data); if(requestCode!=REQ_PICK || resultCode!=Activity.RESULT_OK)return
        val uri=data?.data ?: return; var n=""; var p=""; var cur:Cursor?=null
        try{cur=contentResolver.query(uri,arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER),null,null,null); if(cur!=null&&cur.moveToFirst()){val ni=cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);val pi=cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);if(ni>=0)n=cur.getString(ni)?:"";if(pi>=0)p=cur.getString(pi)?:""}}finally{cur?.close()}
        p=norm(p); if(p.isBlank()){toast("Ese contacto no tiene un teléfono válido.");return}; if(contacts().any{norm(it.phone)==p}){toast("Ese contacto ya está agregado.");return}
        val slot=(1..4).firstOrNull{i->prefs().getString("sms"+i+"Phone","").isNullOrBlank()} ?: return
        val e=prefs().edit().putString("sms"+slot+"Name",n.ifBlank{"Contacto"}).putString("sms"+slot+"Phone",p).putString("sms"+slot+"MedicalAccess","emergency")
        if(prefs().getString("callPhone","").isNullOrBlank()) e.putString("callName",n.ifBlank{"Contacto"}).putString("callPhone",p)
        e.apply(); syncAndReload()
    }

    private fun savePhone(raw:String){
        if(norm(raw).length<9){toast("Ingresá un número válido.");return}; val s=session?:return
        executor.execute{try{val r=api.setNetworkPhone(s,raw);runOnUiThread{toast("Teléfono guardado.");loadState()}}catch(e:Exception){runOnUiThread{toast(e.message?:"No pudimos guardar tu teléfono.")}}}
    }

    private fun syncAndReload(){
        val s=session ?: return; val a=JSONArray(); val call=prefs().getString("callPhone","").orEmpty()
        for(c in contacts()) a.put(JSONObject().put("slot",c.slot).put("name",c.name).put("phone",c.phone).put("sms_enabled",true).put("call_enabled",c.phone==call).put("medical_access",c.access))
        executor.execute{try{val state=api.syncNetworkContacts(s,a);runOnUiThread{serverState=state;render()}}catch(e:Exception){runOnUiThread{render();toast(e.message?:"Los contactos quedaron guardados en el teléfono y se sincronizarán cuando vuelva Internet.")}}}
    }

    private fun openAlert(a:JSONObject){ startActivity(Intent(this,EmergencyAlertActivity::class.java).apply{putExtra("emergency_id",a.optString("id"));putExtra("owner_user_id",a.optString("owner_user_id"));putExtra("person_name",a.optString("person_name","Contacto CERCA"));putExtra("mode",a.optString("mode","normal"));putExtra("latitude",a.optString("latitude",""));putExtra("longitude",a.optString("longitude",""));putExtra("medical_access",a.optString("medical_access","never"))}) }

    private fun norm(raw:String):String{ val v=raw.trim(); return v.filterIndexed{i,ch->ch.isDigit() || (ch=='+'&&i==0)} }
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(17),dp(16),dp(17),dp(16));setBackgroundResource(R.drawable.card_bg)}
    private fun title(v:String,s:Float)=TextView(this).apply{text=v;textSize=s;setTextColor(Color.parseColor("#0B5960"));setTypeface(typeface,android.graphics.Typeface.BOLD)}
    private fun body(v:String)=TextView(this).apply{text=v;textSize=14f;setTextColor(Color.parseColor("#657579"));setPadding(0,dp(6),0,dp(8))}
    private fun primary(v:String,f:()->Unit)=Button(this).apply{text=v;setTextColor(Color.WHITE);backgroundTintList=ColorStateList.valueOf(Color.parseColor("#0B5960"));setTypeface(typeface,android.graphics.Typeface.BOLD);setOnClickListener{f()}}
    private fun secondary(v:String,f:()->Unit)=Button(this).apply{text=v;setTextColor(Color.parseColor("#0B5960"));backgroundTintList=ColorStateList.valueOf(Color.parseColor("#DDF2F0"));setTypeface(typeface,android.graphics.Typeface.BOLD);setOnClickListener{f()}}
    private fun danger(v:String,f:()->Unit)=Button(this).apply{text=v;setTextColor(Color.parseColor("#B54F45"));backgroundTintList=ColorStateList.valueOf(Color.parseColor("#F8ECE8"));setTypeface(typeface,android.graphics.Typeface.BOLD);setOnClickListener{f()}}
    private fun margin()=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(12),0,0)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun toast(v:String)=Toast.makeText(this,v,Toast.LENGTH_LONG).show()
}
