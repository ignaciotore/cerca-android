package com.help.seguridad

import android.app.Activity
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
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
    private val p by lazy{Color.parseColor("#0B5960")}
    private val dark by lazy{Color.parseColor("#06494F")}
    private val soft by lazy{Color.parseColor("#DDF2F0")}
    private val soft2 by lazy{Color.parseColor("#ECF7F5")}
    private val bg by lazy{Color.parseColor("#F4F8F7")}
    private val ink by lazy{Color.parseColor("#26393D")}
    private val muted by lazy{Color.parseColor("#7B898C")}
    private val line by lazy{Color.parseColor("#DCE8E6")}

    override fun onCreate(b:Bundle?){
        super.onCreate(b)
        s=SecureSessionStore(this).load()
        if(s==null){finish();return}
        window.statusBarColor=bg
        window.navigationBarColor=bg
        root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(14),dp(16),dp(28))
        }
        setContentView(ScrollView(this).apply{
            setBackgroundColor(bg)
            isFillViewport=true
            addView(root)
        })
        loading()
        load()
    }
    private fun loading(){
        root.removeAllViews()
        root.addView(LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
            setPadding(dp(20),dp(80),dp(20),dp(80))
            addView(badge("CERCA",p,Color.WHITE))
            addView(tv("Cargando Panel Maestro…",18,ink,true).apply{
                gravity=Gravity.CENTER
                setPadding(0,dp(18),0,dp(4))
            })
            addView(tv("Preparando empresas, usuarios y actividad.",13,muted,false).apply{gravity=Gravity.CENTER})
        })
    }
    private fun load(msg:String?=null){val ss=s?:return;ex.execute{try{val d=call("GET","dashboard",null,ss);runOnUiThread{render(d);if(msg!=null)toast(msg)}}catch(e:Exception){runOnUiThread{root.removeAllViews();root.addView(tv(e.message?:"No pudimos abrir el panel.",17,p,true))}}}}
    private fun render(d:JSONObject){
        root.removeAllViews()
        root.addView(headerCard())
        val sm=d.optJSONObject("summary")?:JSONObject()

        root.addView(sectionTitle("Resumen","Vista general de CERCA"))
        val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        r.addView(metric("Usuarios",sm.optInt("users"),"U"),lpw())
        r.addView(metric("Empresas",sm.optInt("organizations"),"E"),lpw())
        root.addView(r)
        val r2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        r2.addView(metric("Empresariales",sm.optInt("enterprise_users"),"C"),lpw())
        r2.addView(metric("Activaciones",sm.optInt("activations"),"A"),lpw())
        root.addView(r2)

        root.addView(btn("＋  Crear empresa",p){newOrg()},full(54,14))

        root.addView(sectionTitle("Empresas","Marca, plan y administración"))
        val os=d.optJSONArray("organizations")
        if(os==null||os.length()==0){
            root.addView(emptyCard("Todavía no hay empresas creadas."))
        }else{
            for(i in 0 until os.length())root.addView(orgCard(os.getJSONObject(i)))
        }

        root.addView(sectionTitle("Usuarios CERCA","Cuenta, empresa y actividad"))
        val us=d.optJSONArray("users")
        if(us==null||us.length()==0){
            root.addView(emptyCard("Todavía no hay usuarios para mostrar."))
        }else{
            for(i in 0 until us.length()){
                val u=us.getJSONObject(i)
                val name=u.optString("full_name").ifBlank{u.optString("email")}
                val org=u.optString("organization_name").ifBlank{"Individual"}
                val role=u.optString("enterprise_role")
                val tag=when{
                    role=="admin"->"ADMIN EMPRESA"
                    org!="Individual"->"EMPRESARIAL"
                    else->"INDIVIDUAL"
                }
                root.addView(card().apply{
                    val top=LinearLayout(this@MasterAdminActivity).apply{
                        orientation=LinearLayout.HORIZONTAL
                        gravity=Gravity.CENTER_VERTICAL
                        addView(initialCircle(name),LinearLayout.LayoutParams(dp(44),dp(44)))
                        addView(LinearLayout(this@MasterAdminActivity).apply{
                            orientation=LinearLayout.VERTICAL
                            setPadding(dp(12),0,dp(8),0)
                            addView(tv(name,16,ink,true))
                            addView(tv(u.optString("email"),12,muted,false))
                        },LinearLayout.LayoutParams(0,-2,1f))
                        addView(badge(tag,if(tag=="INDIVIDUAL")Color.parseColor("#EEF1F1") else soft,if(tag=="INDIVIDUAL")muted else dark))
                    }
                    addView(top)
                    addView(LinearLayout(this@MasterAdminActivity).apply{
                        orientation=LinearLayout.HORIZONTAL
                        gravity=Gravity.CENTER_VERTICAL
                        setPadding(0,dp(12),0,0)
                        addView(tv(org,12,p,true),LinearLayout.LayoutParams(0,-2,1f))
                        addView(tv("${u.optInt("activation_count")} activación(es)",12,muted,false))
                    })
                })
            }
        }
        root.addView(btn("Volver",soft){finish()}.apply{setTextColor(dark)},full(50,18))
    }
    private fun orgCard(o:JSONObject)=card().apply{
        val id=o.optString("id")
        val n=o.optString("name")
        val billing=if(o.optString("billing_mode")=="company_pays")"La empresa paga" else "Cada usuario paga"

        addView(LinearLayout(this@MasterAdminActivity).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            addView(initialCircle(n),LinearLayout.LayoutParams(dp(46),dp(46)))
            addView(LinearLayout(this@MasterAdminActivity).apply{
                orientation=LinearLayout.VERTICAL
                setPadding(dp(12),0,dp(8),0)
                addView(tv(n,18,ink,true))
                addView(tv("${o.optInt("member_count")} usuario(s) · $billing",12,muted,false))
            },LinearLayout.LayoutParams(0,-2,1f))
            addView(badge(if(o.optBoolean("active",true))"ACTIVA" else "INACTIVA",soft,dark))
        })

        addView(LinearLayout(this@MasterAdminActivity).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(12),dp(10),dp(12),dp(10))
            background=rounded(soft2,14,null,0)
            addView(tv("Identidad visual",12,muted,true),LinearLayout.LayoutParams(0,-2,1f))
            addView(colorDot(o.optString("primary_color","#0B5960")))
            addView(colorDot(o.optString("secondary_color","#DDF2F0")))
            addView(colorDot(o.optString("accent_color","#D95F52")))
        },LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(13),0,dp(5))})

        addView(actionRow(
            btn("Marca / plan",soft2){editOrg(o)}.apply{setTextColor(dark)},
            btn("Administrador",soft2){admin(id,n)}.apply{setTextColor(dark)}
        ))
        addView(actionRow(
            btn("Subir logo",soft2){
                logoOrg=id
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type="image/*"
                },REQ_LOGO)
            }.apply{setTextColor(dark)},
            btn("Generar invitación",p){invite(id,n)}
        ))
    }
    private fun newOrg(){val n=EditText(this).apply{hint="Nombre de empresa"};val pay=CheckBox(this).apply{text="La empresa paga el acceso de todos"};val lic=EditText(this).apply{hint="Licencias (opcional)";inputType=2};dialog("Nueva empresa",box(n,pay,lic)){val name=n.text.toString().trim();if(name.length<2){toast("Ingresá un nombre.");return@dialog};val b=JSONObject().put("name",name).put("billing_mode",if(pay.isChecked)"company_pays" else "user_pays");lic.text.toString().toIntOrNull()?.let{b.put("license_limit",it)};post("save_org",b,"Empresa creada.")}}
    private fun editOrg(o:JSONObject){val n=EditText(this).apply{setText(o.optString("name"));hint="Nombre"};val pc=EditText(this).apply{setText(o.optString("primary_color","#0B5960"));hint="Color principal #RRGGBB"};val sc=EditText(this).apply{setText(o.optString("secondary_color","#DDF2F0"));hint="Color secundario #RRGGBB"};val ac=EditText(this).apply{setText(o.optString("accent_color","#D95F52"));hint="Acento #RRGGBB"};val pay=CheckBox(this).apply{text="La empresa paga a todos";isChecked=o.optString("billing_mode")=="company_pays"};val lic=EditText(this).apply{hint="Licencias (vacío = sin límite)";inputType=2;if(!o.isNull("license_limit"))setText(o.optInt("license_limit").toString())};dialog("Marca y plan",box(n,pc,sc,ac,pay,lic)){val b=JSONObject().put("id",o.optString("id")).put("name",n.text.toString().trim()).put("primary_color",pc.text.toString().trim()).put("secondary_color",sc.text.toString().trim()).put("accent_color",ac.text.toString().trim()).put("billing_mode",if(pay.isChecked)"company_pays" else "user_pays");if(lic.text.isBlank())b.put("license_limit",JSONObject.NULL) else b.put("license_limit",lic.text.toString().toInt());post("save_org",b,"Empresa actualizada.")}}
    private fun admin(id:String,n:String){val e=EditText(this).apply{hint="Email de la cuenta CERCA";inputType=33};dialog("Administrador · $n",box(e)){post("assign_admin",JSONObject().put("organization_id",id).put("email",e.text.toString().trim()),"Administrador actualizado.")}}
    private fun invite(id:String,n:String){val ss=s?:return;ex.execute{try{val x=call("POST","create_invite",JSONObject().put("organization_id",id).put("max_uses",50),ss);val c=x.optString("code");runOnUiThread{(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Código CERCA Empresa",c));startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"Te invito a usar CERCA con $n. Instalá CERCA, iniciá tu cuenta y en Mi perfil tocá UNIRME A UNA EMPRESA. Código: $c")},"Enviar invitación"))}}catch(e:Exception){runOnUiThread{toast(e.message?:"No pudimos generar la invitación.")}}}}
    private fun post(a:String,b:JSONObject,msg:String){val ss=s?:return;toast("Guardando…");ex.execute{try{call("POST",a,b,ss);runOnUiThread{load(msg)}}catch(e:Exception){runOnUiThread{toast(e.message?:"No pudimos guardar.")}}}}
    @Deprecated("compat") override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r!=REQ_LOGO||c!=Activity.RESULT_OK)return;val u=d?.data?:return;val id=logoOrg?:return;try{val bytes=contentResolver.openInputStream(u)?.use{it.readBytes()}?:return;if(bytes.size>2097152){toast("El logo debe pesar menos de 2 MB.");return};post("upload_logo",JSONObject().put("organization_id",id).put("content_type",contentResolver.getType(u)?:"image/png").put("base64",Base64.encodeToString(bytes,Base64.NO_WRAP)),"Logo actualizado.")}catch(e:Exception){toast(e.message?:"No pude leer el logo.")}}
    private fun dialog(t:String,v:LinearLayout,ok:()->Unit){AlertDialog.Builder(this).setTitle(t).setView(v).setNegativeButton("CANCELAR",null).setPositiveButton("GUARDAR"){_,_->ok()}.show()}
    private fun box(vararg v:android.view.View)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),0,dp(20),0);v.forEach{addView(it)}}
    private fun headerCard()=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(dp(18),dp(18),dp(18),dp(18))
        background=rounded(Color.WHITE,20,line,1)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(8))}
        addView(LinearLayout(this@MasterAdminActivity).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            addView(LinearLayout(this@MasterAdminActivity).apply{
                orientation=LinearLayout.VERTICAL
                addView(tv("CERCA",13,p,true).apply{letterSpacing=.18f})
                addView(tv("Panel Maestro",27,dark,true))
            },LinearLayout.LayoutParams(0,-2,1f))
            addView(badge("ADMIN",soft,dark))
        })
        addView(tv("Empresas, usuarios y activaciones",14,muted,false).apply{setPadding(0,dp(8),0,0)})
        addView(LinearLayout(this@MasterAdminActivity).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(12),dp(10),dp(12),dp(10))
            background=rounded(soft2,14,null,0)
            addView(tv("●",12,p,true))
            addView(tv("  Sesión administradora activa",12,dark,true))
        },LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(14),0,0)})
    }

    private fun sectionTitle(t:String,s:String)=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(0,dp(18),0,dp(8))
        addView(tv(t,21,dark,true))
        addView(tv(s,12,muted,false))
    }

    private fun metric(l:String,v:Int,k:String)=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(dp(14),dp(13),dp(14),dp(13))
        background=rounded(Color.WHITE,17,line,1)
        addView(LinearLayout(this@MasterAdminActivity).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            addView(TextView(this@MasterAdminActivity).apply{
                text=k
                textSize=11f
                setTextColor(dark)
                setTypeface(typeface,Typeface.BOLD)
                gravity=Gravity.CENTER
                background=rounded(soft,99,null,0)
            },LinearLayout.LayoutParams(dp(28),dp(28)))
            addView(tv(l,12,muted,true).apply{setPadding(dp(8),0,0,0)})
        })
        addView(tv(v.toString(),29,dark,true).apply{setPadding(0,dp(7),0,0)})
    }

    private fun tv(t:String,z:Int,c:Int,b:Boolean)=TextView(this).apply{
        text=t
        textSize=z.toFloat()
        setTextColor(c)
        if(b)setTypeface(typeface,Typeface.BOLD)
        setPadding(0,dp(2),0,dp(2))
    }

    private fun btn(t:String,c:Int,a:()->Unit)=Button(this).apply{
        text=t
        isAllCaps=false
        textSize=13f
        setTextColor(Color.WHITE)
        setTypeface(typeface,Typeface.BOLD)
        backgroundTintList=ColorStateList.valueOf(c)
        stateListAnimator=null
        minHeight=0
        minimumHeight=0
        setPadding(dp(8),0,dp(8),0)
        setOnClickListener{a()}
    }

    private fun card()=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(dp(15),dp(15),dp(15),dp(15))
        background=rounded(Color.WHITE,18,line,1)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(5),0,dp(5))}
    }

    private fun emptyCard(m:String)=card().apply{
        gravity=Gravity.CENTER
        setPadding(dp(18),dp(24),dp(18),dp(24))
        addView(tv(m,13,muted,false).apply{gravity=Gravity.CENTER})
    }

    private fun actionRow(l:View,r:View)=LinearLayout(this).apply{
        orientation=LinearLayout.HORIZONTAL
        addView(l,LinearLayout.LayoutParams(0,dp(48),1f).apply{setMargins(0,dp(6),dp(4),0)})
        addView(r,LinearLayout.LayoutParams(0,dp(48),1f).apply{setMargins(dp(4),dp(6),0,0)})
    }

    private fun badge(t:String,bgColor:Int,textColor:Int)=TextView(this).apply{
        text=t
        textSize=10f
        setTextColor(textColor)
        setTypeface(typeface,Typeface.BOLD)
        gravity=Gravity.CENTER
        setPadding(dp(10),dp(6),dp(10),dp(6))
        background=rounded(bgColor,99,null,0)
    }

    private fun initialCircle(n:String)=TextView(this).apply{
        text=n.trim().firstOrNull()?.uppercase()?:"C"
        textSize=16f
        setTextColor(dark)
        setTypeface(typeface,Typeface.BOLD)
        gravity=Gravity.CENTER
        background=rounded(soft,99,null,0)
    }

    private fun colorDot(h:String)=View(this).apply{
        background=rounded(safeColor(h,p),99,Color.WHITE,1)
        layoutParams=LinearLayout.LayoutParams(dp(18),dp(18)).apply{setMargins(dp(5),0,0,0)}
    }

    private fun safeColor(h:String,f:Int)=try{Color.parseColor(h)}catch(_:Exception){f}

    private fun rounded(c:Int,r:Int,sc:Int?,sw:Int)=GradientDrawable().apply{
        shape=GradientDrawable.RECTANGLE
        setColor(c)
        cornerRadius=dp(r).toFloat()
        if(sc!=null&&sw>0)setStroke(dp(sw),sc)
    }

    private fun lpw()=LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(4),dp(4),dp(4),dp(4))}
    private fun full(h:Int,m:Int)=LinearLayout.LayoutParams(-1,dp(h)).apply{setMargins(0,dp(m),0,0)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun toast(t:String)=Toast.makeText(this,t,Toast.LENGTH_LONG).show()
    override fun onDestroy(){ex.shutdownNow();super.onDestroy()}
}
