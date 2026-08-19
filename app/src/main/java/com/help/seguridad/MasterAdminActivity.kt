package com.help.seguridad

import android.app.Activity
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MasterAdminActivity : AppCompatActivity() {
    companion object {
        private const val REQ_LOGO=941
        private const val URL="${SupabaseApi.BASE_URL}/functions/v1/cerca-admin-panel"
        fun canAccess(s:SupabaseApi.Session)=try{ call("GET","dashboard",null,s);true }catch(_:Exception){false}
        private fun call(m:String,a:String,b:JSONObject?,s:SupabaseApi.Session):JSONObject{
            val c=(URL("$URL?action=$a").openConnection() as HttpURLConnection).apply{
                requestMethod=m;connectTimeout=8000;readTimeout=12000
                setRequestProperty("apikey",SupabaseApi.PUBLISHABLE_KEY);setRequestProperty("Authorization","Bearer ${s.accessToken}")
                setRequestProperty("Accept","application/json");if(b!=null){doOutput=true;setRequestProperty("Content-Type","application/json")}
            }
            try{
                if(b!=null)c.outputStream.use{it.write(b.toString().toByteArray(StandardCharsets.UTF_8))}
                val ok=c.responseCode in 200..299;val st=if(ok)c.inputStream else c.errorStream
                val x=JSONObject(st?.bufferedReader()?.use{it.readText()}?.ifBlank{"{}"}?:"{}")
                if(!ok)throw IllegalStateException(x.optString("error","Error CERCA"));return x
            }finally{c.disconnect()}
        }
    }
    private val ex=Executors.newSingleThreadExecutor();private lateinit var root:LinearLayout
    private var s:SupabaseApi.Session?=null;private var logoOrg:String?=null
    private val p by lazy{Color.parseColor("#0B5960")};private val soft by lazy{Color.parseColor("#DDF2F0")}

    override fun onCreate(b:Bundle?){super.onCreate(b);s=SecureSessionStore(this).load();if(s==null){finish();return}
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(28))}
        setContentView(ScrollView(this).apply{setBackgroundColor(Color.parseColor("#F7FAF9"));addView(root)})
        loading();load()
    }
    private fun loading(){root.removeAllViews();root.addView(tv("Cargando Panel Maestro CERCA…",17,p,true).apply{gravity=Gravity.CENTER;setPadding(0,dp(60),0,0)})}
    private fun load(msg:String?=null){val ss=s?:return;ex.execute{try{val d=call("GET","dashboard",null,ss);runOnUiThread{render(d);if(msg!=null)toast(msg)}}catch(e:Exception){runOnUiThread{root.removeAllViews();root.addView(tv(e.message?:"No pudimos abrir el panel.",17,p,true))}}}}
    private fun render(d:JSONObject){root.removeAllViews();root.addView(tv("CERCA · PANEL MAESTRO",27,p,true));root.addView(tv("Empresas, usuarios y activaciones",14,Color.DKGRAY,false))
        val sm=d.optJSONObject("summary")?:JSONObject();val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};r.addView(metric("Usuarios",sm.optInt("users")),lpw());r.addView(metric("Empresas",sm.optInt("organizations")),lpw());root.addView(r)
        val r2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};r2.addView(metric("Empresariales",sm.optInt("enterprise_users")),lpw());r2.addView(metric("Activaciones",sm.optInt("activations")),lpw());root.addView(r2)
        root.addView(btn("CREAR EMPRESA",p){newOrg()},full(56,12));root.addView(title("Empresas"));val os=d.optJSONArray("organizations");if(os!=null)for(i in 0 until os.length())root.addView(orgCard(os.getJSONObject(i)))
        root.addView(title("Usuarios CERCA"));val us=d.optJSONArray("users");if(us!=null)for(i in 0 until us.length()){val u=us.getJSONObject(i);root.addView(card().apply{addView(tv(u.optString("full_name").ifBlank{u.optString("email")},16,Color.DKGRAY,true));addView(tv(u.optString("email"),13,Color.GRAY,false));addView(tv("${u.optString("organization_name").ifBlank{"Individual"}} · ${u.optInt("activation_count")} activación(es)",13,p,false))})}
        root.addView(btn("VOLVER",soft){finish()}.apply{setTextColor(p)},full(52,16))
    }
    private fun orgCard(o:JSONObject)=card().apply{val id=o.optString("id");val n=o.optString("name");addView(tv(n,18,p,true));addView(tv("${o.optInt("member_count")} usuario(s) · ${if(o.optString("billing_mode")=="company_pays")"empresa paga" else "cada usuario paga"}",13,Color.GRAY,false));addView(btn("MARCA / PLAN",soft){editOrg(o)}.apply{setTextColor(p)});addView(btn("MARCAR ADMINISTRADOR",soft){admin(id,n)}.apply{setTextColor(p)});addView(btn("SUBIR LOGO",soft){logoOrg=id;startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/*"},REQ_LOGO)}.apply{setTextColor(p)});addView(btn("GENERAR INVITACIÓN",p){invite(id,n)})}
    private fun newOrg(){val n=EditText(this).apply{hint="Nombre de empresa"};val pay=CheckBox(this).apply{text="La empresa paga el acceso de todos"};val lic=EditText(this).apply{hint="Licencias (opcional)";inputType=2};dialog("Nueva empresa",box(n,pay,lic)){val name=n.text.toString().trim();if(name.length<2){toast("Ingresá un nombre.");return@dialog};val b=JSONObject().put("name",name).put("billing_mode",if(pay.isChecked)"company_pays" else "user_pays");lic.text.toString().toIntOrNull()?.let{b.put("license_limit",it)};post("save_org",b,"Empresa creada.")}}
    private fun editOrg(o:JSONObject){val n=EditText(this).apply{setText(o.optString("name"));hint="Nombre"};val pc=EditText(this).apply{setText(o.optString("primary_color","#0B5960"));hint="Color principal #RRGGBB"};val sc=EditText(this).apply{setText(o.optString("secondary_color","#DDF2F0"));hint="Color secundario #RRGGBB"};val ac=EditText(this).apply{setText(o.optString("accent_color","#D95F52"));hint="Acento #RRGGBB"};val pay=CheckBox(this).apply{text="La empresa paga a todos";isChecked=o.optString("billing_mode")=="company_pays"};val lic=EditText(this).apply{hint="Licencias (vacío = sin límite)";inputType=2;if(!o.isNull("license_limit"))setText(o.optInt("license_limit").toString())};dialog("Marca y plan",box(n,pc,sc,ac,pay,lic)){val b=JSONObject().put("id",o.optString("id")).put("name",n.text.toString().trim()).put("primary_color",pc.text.toString().trim()).put("secondary_color",sc.text.toString().trim()).put("accent_color",ac.text.toString().trim()).put("billing_mode",if(pay.isChecked)"company_pays" else "user_pays");if(lic.text.isBlank())b.put("license_limit",JSONObject.NULL) else b.put("license_limit",lic.text.toString().toInt());post("save_org",b,"Empresa actualizada.")}}
    private fun admin(id:String,n:String){val e=EditText(this).apply{hint="Email de la cuenta CERCA";inputType=33};dialog("Administrador · $n",box(e)){post("assign_admin",JSONObject().put("organization_id",id).put("email",e.text.toString().trim()),"Administrador actualizado.")}}
    private fun invite(id:String,n:String){val ss=s?:return;ex.execute{try{val x=call("POST","create_invite",JSONObject().put("organization_id",id).put("max_uses",50),ss);val c=x.optString("code");runOnUiThread{(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Código CERCA Empresa",c));startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"Te invito a usar CERCA con $n. Instalá CERCA, iniciá tu cuenta y en Mi perfil tocá UNIRME A UNA EMPRESA. Código: $c")},"Enviar invitación"))}}catch(e:Exception){runOnUiThread{toast(e.message?:"No pudimos generar la invitación.")}}}}
    private fun post(a:String,b:JSONObject,msg:String){val ss=s?:return;toast("Guardando…");ex.execute{try{call("POST",a,b,ss);runOnUiThread{load(msg)}}catch(e:Exception){runOnUiThread{toast(e.message?:"No pudimos guardar.")}}}}
    @Deprecated("compat") override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r!=REQ_LOGO||c!=Activity.RESULT_OK)return;val u=d?.data?:return;val id=logoOrg?:return;try{val bytes=contentResolver.openInputStream(u)?.use{it.readBytes()}?:return;if(bytes.size>2097152){toast("El logo debe pesar menos de 2 MB.");return};post("upload_logo",JSONObject().put("organization_id",id).put("content_type",contentResolver.getType(u)?:"image/png").put("base64",Base64.encodeToString(bytes,Base64.NO_WRAP)),"Logo actualizado.")}catch(e:Exception){toast(e.message?:"No pude leer el logo.")}}
    private fun dialog(t:String,v:LinearLayout,ok:()->Unit){AlertDialog.Builder(this).setTitle(t).setView(v).setNegativeButton("CANCELAR",null).setPositiveButton("GUARDAR"){_,_->ok()}.show()}
    private fun box(vararg v:android.view.View)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),0,dp(20),0);v.forEach{addView(it)}}
    private fun metric(l:String,v:Int)=card().apply{addView(tv(v.toString(),24,p,true));addView(tv(l,12,Color.GRAY,false))}
    private fun title(t:String)=tv(t,20,p,true).apply{setPadding(0,dp(16),0,dp(6))}
    private fun tv(t:String,z:Int,c:Int,b:Boolean)=TextView(this).apply{text=t;textSize=z.toFloat();setTextColor(c);if(b)setTypeface(typeface,android.graphics.Typeface.BOLD);setPadding(0,dp(3),0,dp(3))}
    private fun btn(t:String,c:Int,a:()->Unit)=Button(this).apply{text=t;setTextColor(Color.WHITE);backgroundTintList=ColorStateList.valueOf(c);setOnClickListener{a()}}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(13),dp(11),dp(13),dp(11));setBackgroundColor(Color.WHITE);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(5),0,dp(5))}}
    private fun lpw()=LinearLayout.LayoutParams(0,-2,1f);private fun full(h:Int,m:Int)=LinearLayout.LayoutParams(-1,dp(h)).apply{setMargins(0,dp(m),0,0)};private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt();private fun toast(t:String)=Toast.makeText(this,t,Toast.LENGTH_LONG).show()
    override fun onDestroy(){ex.shutdownNow();super.onDestroy()}
}
