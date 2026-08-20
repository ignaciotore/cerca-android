package com.help.seguridad

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MasterAdminActivity : AppCompatActivity() {
    companion object {
        private const val DASHBOARD_URL = "https://yduoxeqgxolkzvjexlqk.supabase.co/functions/v1/cerca-admin-page"
        private const val API_URL = "${SupabaseApi.BASE_URL}/functions/v1/cerca-admin-panel?action=dashboard"
        private const val REQ_FILE = 942
        fun canAccess(session: SupabaseApi.Session): Boolean = try {
            val c=(URL(API_URL).openConnection() as HttpURLConnection).apply{
                requestMethod="GET";connectTimeout=6000;readTimeout=7000
                setRequestProperty("apikey",SupabaseApi.PUBLISHABLE_KEY)
                setRequestProperty("Authorization","Bearer ${session.accessToken}")
            }
            try { c.responseCode in 200..299 } finally { c.disconnect() }
        } catch (_:Exception) { false }
    }

    private lateinit var web: WebView
    private var chooser: ValueCallback<Array<Uri>>? = null
    private var session: SupabaseApi.Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session=SecureSessionStore(this).load()
        if(session==null){finish();return}
        web=WebView(this)
        setContentView(web)
        web.settings.apply{
            javaScriptEnabled=true
            domStorageEnabled=true
            cacheMode=WebSettings.LOAD_NO_CACHE
            allowFileAccess=false
            allowContentAccess=true
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        web.webViewClient=object:WebViewClient(){
            override fun onPageFinished(view:WebView,url:String){super.onPageFinished(view,url);injectSession(view)}
        }
        web.webChromeClient=object:WebChromeClient(){
            override fun onShowFileChooser(webView:WebView?,filePathCallback:ValueCallback<Array<Uri>>?,fileChooserParams:FileChooserParams?):Boolean{
                chooser?.onReceiveValue(null);chooser=filePathCallback
                return try{
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/*"},REQ_FILE);true
                }catch(e:Exception){chooser=null;Toast.makeText(this@MasterAdminActivity,"No pude abrir tus imágenes.",Toast.LENGTH_LONG).show();false}
            }
        }
        loadDashboardHtml()
    }

    private fun loadDashboardHtml(){
        Thread {
            try {
                val html = URL(DASHBOARD_URL + "?v=" + System.currentTimeMillis()).readText()
                if (!html.contains("<html", ignoreCase = true)) throw IllegalStateException("Respuesta inválida del panel")
                runOnUiThread { web.loadDataWithBaseURL(DASHBOARD_URL, html, "text/html", "UTF-8", null) }
            } catch (e:Exception) {
                runOnUiThread { Toast.makeText(this, "No pudimos abrir el Panel Maestro. Volvé a intentar.", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun injectSession(view:WebView){
        val s=session?:return
        val obj=JSONObject().put("access_token",s.accessToken).put("refresh_token",s.refreshToken).put("expires_at",s.expiresAtEpochSeconds).put("user",JSONObject().put("id",s.userId).put("email",s.email))
        val js="localStorage.setItem('cerca_master_session',"+JSONObject.quote(obj.toString())+"); if(window.cercaSessionReady){window.cercaSessionReady();}"
        view.evaluateJavascript(js,null)
    }

    @Deprecated("compat")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=REQ_FILE)return
        val cb=chooser?:return;chooser=null
        val out=if(resultCode==Activity.RESULT_OK&&data?.data!=null) arrayOf(data.data!!) else null
        cb.onReceiveValue(out)
    }

    @Deprecated("compat")
    override fun onBackPressed(){if(::web.isInitialized&&web.canGoBack())web.goBack()else super.onBackPressed()}

    override fun onDestroy(){if(::web.isInitialized){web.stopLoading();web.destroy()};chooser?.onReceiveValue(null);chooser=null;super.onDestroy()}
}
