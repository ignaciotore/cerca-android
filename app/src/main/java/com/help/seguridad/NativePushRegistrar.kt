package com.help.seguridad

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object NativePushRegistrar {
    private const val PREFS = "cerca_native_bridge"
    private const val ACCESS_TOKEN = "access_token"
    private val executor = Executors.newSingleThreadExecutor()

    fun saveAccessToken(context: Context, accessToken: String) {
        val clean = accessToken.trim()
        if (clean.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ACCESS_TOKEN, clean)
            .apply()
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            registerToken(context.applicationContext, token)
        }
    }

    fun registerLatest(context: Context) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            registerToken(context.applicationContext, token)
        }
    }

    fun registerToken(context: Context, fcmToken: String) {
        val accessToken = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ACCESS_TOKEN, "")
            .orEmpty()
            .trim()
        if (accessToken.isBlank() || fcmToken.isBlank()) return

        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(
                    SupabaseApi.BASE_URL + "/functions/v1/cerca-network-v2?action=register_device"
                ).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("apikey", SupabaseApi.PUBLISHABLE_KEY)
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }
                val body = JSONObject().put("token", fcmToken).toString().toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                if (code in 200..299) {
                    connection.inputStream?.close()
                } else {
                    connection.errorStream?.close()
                }
            } catch (_: Exception) {
                // Se reintenta en el próximo arranque/focus o cuando Firebase renueve el token.
            } finally {
                connection?.disconnect()
            }
        }
    }
}
