package com.help.seguridad

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class WebAppActivity : AppCompatActivity() {

    companion object {
        private const val WEB_URL = "https://cerca-seguridad.pages.dev/"
        private const val REQ_CALL = 4101
        private const val REQ_LOCATION = 4102
        private const val PREFS = "cerca_native_bridge"
        private const val CALL_INTRO_SHOWN = "call_intro_shown"
    }

    private lateinit var webView: WebView
    private var pendingCallPhone: String? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.setGeolocationEnabled(true)
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = settings.userAgentString + " CERCA-Native-Android/1"
            addJavascriptInterface(CercaNativeBridge(), "CercaNative")
            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    if (origin == null || callback == null) return
                    if (ContextCompat.checkSelfPermission(
                            this@WebAppActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        callback.invoke(origin, true, false)
                    } else {
                        pendingGeoOrigin = origin
                        pendingGeoCallback = callback
                        ActivityCompat.requestPermissions(
                            this@WebAppActivity,
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ),
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
            }
        }

        setContentView(webView)
        maybeExplainCallPermission()
        loadIntentTarget(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadIntentTarget(intent)
    }

    override fun onResume() {
        super.onResume()
        NativePushRegistrar.registerLatest(applicationContext)
    }

    private fun handleNavigation(uri: Uri): Boolean {
        return when (uri.scheme?.lowercase()) {
            "tel" -> {
                startDirectCall(uri.schemeSpecificPart.orEmpty())
                true
            }
            "http", "https" -> {
                if (uri.host.equals("cerca-seguridad.pages.dev", ignoreCase = true)) {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Exception) {
                    }
                    true
                }
            }
            else -> {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    private fun loadIntentTarget(intent: Intent) {
        val emergencyId = intent.getStringExtra("emergency_id").orEmpty().trim()
        val openAction = intent.getStringExtra("open_action").orEmpty().trim()
        val target = if (emergencyId.isBlank()) {
            WEB_URL
        } else {
            Uri.parse(WEB_URL).buildUpon()
                .appendQueryParameter("alert", emergencyId)
                .apply {
                    if (openAction.isNotBlank()) appendQueryParameter("view", openAction)
                }
                .build()
                .toString()
        }
        webView.loadUrl(target)
    }

    private fun maybeExplainCallPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(CALL_INTRO_SHOWN, false)) return
        prefs.edit().putBoolean(CALL_INTRO_SHOWN, true).apply()
        webView.post {
            if (isFinishing || isDestroyed) return@post
            AlertDialog.Builder(this)
                .setTitle("Activá la llamada automática")
                .setMessage("CERCA necesita permiso para llamar automáticamente a tu contacto de llamada cuando activás un SOS normal. El SOS silencioso nunca realiza llamadas.")
                .setPositiveButton("ACTIVAR") { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CALL_PHONE),
                        REQ_CALL
                    )
                }
                .setNegativeButton("MÁS TARDE", null)
                .show()
        }
    }

    private fun normalizePhone(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isBlank()) return ""
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    private fun startDirectCall(rawPhone: String) {
        val phone = normalizePhone(rawPhone)
        if (phone.isBlank()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingCallPhone = phone
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                REQ_CALL
            )
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", phone, null)))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
            } catch (_: Exception) {
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_CALL -> {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                val pending = pendingCallPhone
                pendingCallPhone = null
                if (granted && !pending.isNullOrBlank()) startDirectCall(pending)
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
            runOnUiThread { startDirectCall(phone) }
        }

        @JavascriptInterface
        fun syncSession(accessToken: String) {
            NativePushRegistrar.saveAccessToken(applicationContext, accessToken)
        }

        @JavascriptInterface
        fun isNativeAndroid(): Boolean = true
    }
}
