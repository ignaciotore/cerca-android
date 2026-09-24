package com.help.seguridad

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class WebAppActivity : AppCompatActivity() {

    companion object {
        private const val WEB_URL = "https://cerca-seguridad.pages.dev/"
        private const val REQ_CALL = 4101
        private const val REQ_LOCATION = 4102
        private const val WATCH_INTERVAL_MS = 1000L
        private const val PREFS = "cerca_native_bridge"
        private const val LAST_CALLED_EMERGENCY = "last_called_emergency"
        private const val WEB_CALL_LATCH = "web_call_latch"
        private const val WEB_CALL_LATCH_AT = "web_call_latch_at"
        private const val WEB_CALL_LATCH_GRACE_MS = 15000L
        private const val SESSION_STORAGE_KEY = "cerca_web_session_v1"
    }

    private lateinit var webView: WebView
    private var pendingCallPhone: String? = null
    private var pendingCallEmergencyId: String? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    @Volatile private var currentAccessToken = ""
    @Volatile private var cachedUserId = ""
    @Volatile private var resumed = false
    private val nativeExecutor = Executors.newSingleThreadExecutor()
    private val nativeWatchBusy = AtomicBoolean(false)
    private val nativeHandler = Handler(Looper.getMainLooper())

    private val nativeWatchdog = object : Runnable {
        override fun run() {
            if (resumed) syncSessionFromWebStorage()
            nativeHandler.postDelayed(this, WATCH_INTERVAL_MS)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.setGeolocationEnabled(true)
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = settings.userAgentString + " CERCA-Native-Android/8"
            addJavascriptInterface(CercaNativeBridge(), "CercaNative")
            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                    if (origin == null || callback == null) return
                    if (ContextCompat.checkSelfPermission(this@WebAppActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        callback.invoke(origin, true, false)
                    } else {
                        pendingGeoOrigin = origin
                        pendingGeoCallback = callback
                        ActivityCompat.requestPermissions(
                            this@WebAppActivity,
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            REQ_LOCATION
                        )
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    return handleNavigation(uri)
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val uri = url?.let(Uri::parse) ?: return false
                    return handleNavigation(uri)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectNativeMarker()
                    syncSessionFromWebStorage()
                }
            }
        }

        setContentView(webView)
        nativeHandler.post(nativeWatchdog)
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        try { NativePushRegistrar.registerLatest(applicationContext) } catch (_: Throwable) {}
        nativeHandler.removeCallbacks(nativeWatchdog)
        nativeHandler.post(nativeWatchdog)
        if (::webView.isInitialized) webView.post { syncSessionFromWebStorage() }
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onDestroy() {
        resumed = false
        nativeHandler.removeCallbacksAndMessages(null)
        nativeExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun injectNativeMarker() {
        if (!::webView.isInitialized) return
        val version = BuildConfig.VERSION_NAME.replace("'", "")
        val script = """
            (function(){
              try {
                var label='APP ANDROID NATIVA $version';
                window.__CERCA_NATIVE_ANDROID__=true;
                window.__CERCA_NATIVE_VERSION__='$version';
                if(document.documentElement) document.documentElement.setAttribute('data-cerca-native-android','true');

                function applyNativeMarker(){
                  try {
                    var p=document.getElementById('connectionPill');
                    if(p && p.textContent!==label) p.textContent=label;
                    var i=document.getElementById('installAppBtn');
                    if(i)i.style.display='none';
                  } catch(e) {}
                }

                applyNativeMarker();
                if(!window.__cercaNativeMarkerObserver && document.documentElement){
                  window.__cercaNativeMarkerObserver=new MutationObserver(function(){ applyNativeMarker(); });
                  window.__cercaNativeMarkerObserver.observe(document.documentElement,{childList:true,subtree:true,characterData:true});
                }
                if(!window.__cercaNativeMarkerTimer){
                  window.__cercaNativeMarkerTimer=setInterval(applyNativeMarker,250);
                }
              } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun decodeJsString(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null" || raw == "undefined") return ""
        return try { JSONArray("[$raw]").optString(0, "") } catch (_: Throwable) { "" }
    }

    private fun syncSessionFromWebStorage() {
        if (!::webView.isInitialized) return
        injectNativeMarker()
        val script = """
            (function(){
              try {
                function tokenFromValue(value){
                  if(!value)return '';
                  try {
                    var x=JSON.parse(value);
                    if(x && typeof x==='object'){
                      if(typeof x.access_token==='string' && x.access_token)return x.access_token;
                      if(x.currentSession && typeof x.currentSession.access_token==='string')return x.currentSession.access_token;
                      if(x.session && typeof x.session.access_token==='string')return x.session.access_token;
                      if(x.data && x.data.session && typeof x.data.session.access_token==='string')return x.data.session.access_token;
                    }
                  } catch(e) {}
                  var m=String(value).match(/\"access_token\"\s*:\s*\"([^\"]+)\"/);
                  return m?m[1]:'';
                }

                var preferred=localStorage.getItem('$SESSION_STORAGE_KEY');
                var t=tokenFromValue(preferred);
                if(t)return t;

                var stores=[localStorage,sessionStorage];
                for(var s=0;s<stores.length;s++){
                  var storage=stores[s];
                  for(var i=0;i<storage.length;i++){
                    var key=storage.key(i);
                    var value=storage.getItem(key);
                    t=tokenFromValue(value);
                    if(t)return t;
                  }
                }
                return '';
              }catch(e){return '';}
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            val token = decodeJsString(raw).trim()
            if (token.isNotBlank()) {
                if (token != currentAccessToken) {
                    currentAccessToken = token
                    cachedUserId = ""
                    try { NativePushRegistrar.saveAccessToken(applicationContext, token) } catch (_: Throwable) {}
                }
                checkEmergencyNatively()
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent) {
        val data = intent.data
        if (data?.scheme.equals("cerca", true) && data?.host.equals("call", true)) {
            val phone = data?.getQueryParameter("phone").orEmpty()
            if (phone.isNotBlank()) {
                webView.post { startDirectCall(phone, null) }
                return
            }
        }
        loadIntentTarget(intent)
    }

    private fun handleNavigation(uri: Uri): Boolean = when (uri.scheme?.lowercase()) {
        "tel" -> {
            startDirectCall(uri.schemeSpecificPart.orEmpty(), null)
            true
        }
        "cerca" -> {
            if (uri.host.equals("call", true)) {
                startDirectCall(uri.getQueryParameter("phone").orEmpty(), null)
                true
            } else false
        }
        "http", "https" -> {
            if (uri.host.equals("cerca-seguridad.pages.dev", ignoreCase = true)) false
            else {
                try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
                true
            }
        }
        else -> {
            try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true } catch (_: Exception) { false }
        }
    }

    private fun loadIntentTarget(intent: Intent) {
        val emergencyId = intent.getStringExtra("emergency_id").orEmpty().trim()
        val openAction = intent.getStringExtra("open_action").orEmpty().trim()
        val target = if (emergencyId.isBlank()) WEB_URL else Uri.parse(WEB_URL).buildUpon()
            .appendQueryParameter("alert", emergencyId)
            .apply { if (openAction.isNotBlank()) appendQueryParameter("view", openAction) }
            .build().toString()
        webView.loadUrl(target)
    }

    private fun normalizePhone(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isBlank()) return ""
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    private fun startDirectCall(rawPhone: String, emergencyId: String?) {
        val phone = normalizePhone(rawPhone)
        if (phone.isBlank()) return

        if (!emergencyId.isNullOrBlank() && emergencyId == lastCalledEmergency()) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingCallPhone = phone
            pendingCallEmergencyId = emergencyId
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL)
            return
        }

        // Mark BEFORE opening the Phone app. The Activity can pause immediately after startActivity(),
        // and a one-second watchdog must never see the same SOS as uncalled during that transition.
        if (!emergencyId.isNullOrBlank()) markEmergencyCalled(emergencyId)

        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phone")
            }
            startActivity(callIntent)
        } catch (_: Exception) {
            if (!emergencyId.isNullOrBlank()) clearEmergencyCalled(emergencyId)
            Toast.makeText(this, "No pude iniciar la llamada automática.", Toast.LENGTH_LONG).show()
        }
    }

    private fun markEmergencyCalled(emergencyId: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(LAST_CALLED_EMERGENCY, emergencyId)
            .apply()
    }

    private fun clearEmergencyCalled(emergencyId: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getString(LAST_CALLED_EMERGENCY, "").orEmpty() == emergencyId) {
            prefs.edit().remove(LAST_CALLED_EMERGENCY).apply()
        }
    }

    private fun lastCalledEmergency(): String =
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(LAST_CALLED_EMERGENCY, "")
            .orEmpty()

    private fun webCallLatched(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(WEB_CALL_LATCH, false)

    private fun webCallLatchAt(): Long =
        getSharedPreferences(PREFS, MODE_PRIVATE).getLong(WEB_CALL_LATCH_AT, 0L)

    private fun setWebCallLatched(active: Boolean) {
        val edit = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
        if (active) {
            edit.putBoolean(WEB_CALL_LATCH, true)
                .putLong(WEB_CALL_LATCH_AT, System.currentTimeMillis())
        } else {
            edit.remove(WEB_CALL_LATCH).remove(WEB_CALL_LATCH_AT)
        }
        edit.apply()
    }

    private fun checkEmergencyNatively() {
        val accessToken = currentAccessToken.trim()
        if (accessToken.isBlank() || !nativeWatchBusy.compareAndSet(false, true)) return

        nativeExecutor.execute {
            try {
                val userId = cachedUserId.ifBlank {
                    val me = httpGet("${SupabaseApi.BASE_URL}/auth/v1/user", accessToken)
                    JSONObject(me).optString("id", "").also { cachedUserId = it }
                }
                if (userId.isBlank()) return@execute

                val emergencyJson = httpGet(
                    "${SupabaseApi.BASE_URL}/rest/v1/cerca_emergencies" +
                        "?owner_user_id=eq.$userId&mode=eq.normal&status=eq.active" +
                        "&select=id&order=started_at.desc&limit=1",
                    accessToken
                )
                val emergencies = JSONArray(emergencyJson)
                if (emergencies.length() == 0) {
                    val latchedAt = webCallLatchAt()
                    if (webCallLatched() && latchedAt > 0L && System.currentTimeMillis() - latchedAt > WEB_CALL_LATCH_GRACE_MS) {
                        setWebCallLatched(false)
                    }
                    return@execute
                }
                val emergencyId = emergencies.getJSONObject(0).optString("id", "")
                if (emergencyId.isBlank()) return@execute
                if (emergencyId == lastCalledEmergency()) {
                    if (!webCallLatched()) setWebCallLatched(true)
                    return@execute
                }
                if (webCallLatched()) {
                    // La llamada ya salió directamente desde el puente web.
                    // Asociamos ese disparo al ID real del SOS para impedir cualquier redial.
                    markEmergencyCalled(emergencyId)
                    return@execute
                }

                val contactsJson = httpGet(
                    "${SupabaseApi.BASE_URL}/rest/v1/cerca_contacts_v2" +
                        "?owner_user_id=eq.$userId&call_enabled=eq.true" +
                        "&select=phone_e164&order=updated_at.desc&limit=1",
                    accessToken
                )
                val contacts = JSONArray(contactsJson)
                if (contacts.length() == 0) return@execute
                val phone = contacts.getJSONObject(0).optString("phone_e164", "")
                if (phone.isBlank()) return@execute

                runOnUiThread {
                    if (!isFinishing && !isDestroyed && resumed) startDirectCall(phone, emergencyId)
                }
            } catch (_: Throwable) {
                // Se vuelve a intentar mientras la emergencia normal siga activa, salvo que ya se haya llamado.
            } finally {
                nativeWatchBusy.set(false)
            }
        }
    }

    private fun httpGet(url: String, accessToken: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("apikey", SupabaseApi.PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            body
        } finally {
            connection.disconnect()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_CALL -> {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                val phone = pendingCallPhone
                val emergencyId = pendingCallEmergencyId
                pendingCallPhone = null
                pendingCallEmergencyId = null
                if (granted && !phone.isNullOrBlank()) {
                    startDirectCall(phone, emergencyId)
                } else if (!granted) {
                    Toast.makeText(this, "CERCA necesita permiso de Teléfono para realizar la llamada del SOS.", Toast.LENGTH_LONG).show()
                }
            }
            REQ_LOCATION -> {
                val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
                val origin = pendingGeoOrigin
                val callback = pendingGeoCallback
                pendingGeoOrigin = null
                pendingGeoCallback = null
                if (origin != null && callback != null) callback.invoke(origin, granted, false)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    inner class CercaNativeBridge {
        @JavascriptInterface
        fun directCall(phone: String) {
            val clean = normalizePhone(phone)
            if (clean.isBlank() || webCallLatched()) return

            // Primero cerramos el candado y después abrimos la app Teléfono.
            // Así la llamada sale en el acto y cualquier repetición de la web queda ignorada.
            setWebCallLatched(true)
            runOnUiThread { startDirectCall(clean, null) }

            // En paralelo asociamos esta llamada con el ID real de la emergencia.
            nativeHandler.postDelayed({ checkEmergencyNatively() }, 300L)
        }

        @JavascriptInterface
        fun syncSession(accessToken: String) {
            val clean = accessToken.trim()
            if (clean.isBlank()) return
            currentAccessToken = clean
            cachedUserId = ""
            try { NativePushRegistrar.saveAccessToken(applicationContext, clean) } catch (_: Throwable) {}
            nativeHandler.post { checkEmergencyNatively() }
        }

        @JavascriptInterface
        fun isNativeAndroid(): Boolean = true

        @JavascriptInterface
        fun nativeVersion(): String = BuildConfig.VERSION_NAME
    }
}
