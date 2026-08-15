package com.help.seguridad

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant

class SupabaseApi {
    companion object {
        const val BASE_URL = "https://yduoxeqgxolkzvjexlqk.supabase.co"
        const val PUBLISHABLE_KEY = "sb_publishable_XtjPnBnjESZwcUnUUAPybg_Y6LqivaD"
        const val WEB_BASE = "$BASE_URL/functions/v1/help-web"
        const val PRIVACY_URL = "$WEB_BASE?page=privacy"
        const val DELETE_ACCOUNT_URL = "$WEB_BASE?page=delete"
        const val RESET_URL = "$WEB_BASE?page=reset"
        const val ADMIN_URL = "$WEB_BASE?page=admin"
    }

    data class Session(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtEpochSeconds: Long,
        val userId: String,
        val email: String
    )

    data class SignUpResult(val session: Session?, val needsEmailConfirmation: Boolean)

    data class Profile(
        val fullName: String,
        val email: String,
        val trialStartedAt: String,
        val trialEndsAt: String,
        val serverEpochMs: Long
    )

    data class Entitlement(
        val subscriptionActive: Boolean,
        val expiresAt: String?,
        val serverEpochMs: Long
    )

    class ApiException(val status: Int, override val message: String) : Exception(message)

    fun signUp(fullName: String, email: String, password: String): SignUpResult {
        val redirect = URLEncoder.encode("$WEB_BASE?page=welcome", "UTF-8")
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("data", JSONObject().put("full_name", fullName.trim()))
        val response = request("POST", "/auth/v1/signup?redirect_to=$redirect", body, null)
        val json = JSONObject(response.body.ifBlank { "{}" })
        val access = json.optString("access_token", "")
        return if (access.isNotBlank()) {
            SignUpResult(parseSession(json, email), false)
        } else {
            SignUpResult(null, true)
        }
    }

    fun signIn(email: String, password: String): Session {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val response = request("POST", "/auth/v1/token?grant_type=password", body, null)
        return parseSession(JSONObject(response.body), email)
    }

    fun refreshSession(session: Session): Session {
        val body = JSONObject().put("refresh_token", session.refreshToken)
        val response = request("POST", "/auth/v1/token?grant_type=refresh_token", body, null)
        return parseSession(JSONObject(response.body), session.email)
    }

    fun requestPasswordReset(email: String) {
        val redirect = URLEncoder.encode(RESET_URL, "UTF-8")
        val body = JSONObject().put("email", email.trim())
        request("POST", "/auth/v1/recover?redirect_to=$redirect", body, null)
    }

    fun fetchProfile(session: Session): Profile {
        val uid = URLEncoder.encode(session.userId, "UTF-8")
        val response = request(
            "GET",
            "/rest/v1/profiles?user_id=eq.$uid&select=full_name,email,trial_started_at,trial_ends_at",
            null,
            session.accessToken
        )
        val arr = JSONArray(response.body)
        if (arr.length() == 0) throw ApiException(404, "No encontramos tu perfil.")
        val o = arr.getJSONObject(0)
        return Profile(
            fullName = o.optString("full_name", ""),
            email = o.optString("email", session.email),
            trialStartedAt = o.optString("trial_started_at", ""),
            trialEndsAt = o.optString("trial_ends_at", ""),
            serverEpochMs = response.serverEpochMs
        )
    }

    fun updateProfileName(session: Session, fullName: String) {
        val uid = URLEncoder.encode(session.userId, "UTF-8")
        request(
            "PATCH",
            "/rest/v1/profiles?user_id=eq.$uid",
            JSONObject().put("full_name", fullName.trim()).put("updated_at", Instant.now().toString()),
            session.accessToken,
            extraHeaders = mapOf("Prefer" to "return=minimal")
        )
    }

    fun fetchEntitlement(session: Session): Entitlement {
        val uid = URLEncoder.encode(session.userId, "UTF-8")
        val response = request(
            "GET",
            "/rest/v1/entitlements?user_id=eq.$uid&select=subscription_active,expires_at",
            null,
            session.accessToken
        )
        val arr = JSONArray(response.body)
        if (arr.length() == 0) return Entitlement(false, null, response.serverEpochMs)
        val o = arr.getJSONObject(0)
        return Entitlement(
            o.optBoolean("subscription_active", false),
            o.optString("expires_at", "").ifBlank { null },
            response.serverEpochMs
        )
    }

    fun insertActivation(session: Session, event: ActivationQueue.Event) {
        val body = JSONObject()
            .put("id", event.id)
            .put("user_id", event.userId)
            .put("activated_at", event.activatedAt)
            .put("source", "android")
            .put("app_version", event.appVersion)
        try {
            request(
                "POST",
                "/rest/v1/help_activations",
                body,
                session.accessToken,
                extraHeaders = mapOf("Prefer" to "return=minimal")
            )
        } catch (e: ApiException) {
            // HTTP 409 means this UUID was already synchronized; treat it as success.
            if (e.status != 409) throw e
        }
    }

    fun verifyGooglePlayPurchase(session: Session, purchaseToken: String) {
        val body = JSONObject()
            .put("purchase_token", purchaseToken)
            .put("product_id", "help_monthly")
        request("POST", "/functions/v1/google-play-entitlement", body, session.accessToken)
    }

    fun deleteAccount(session: Session) {
        request("POST", "/functions/v1/delete-account", JSONObject(), session.accessToken)
    }

    fun isSessionNearExpiry(session: Session): Boolean {
        val now = Instant.now().epochSecond
        return session.expiresAtEpochSeconds <= now + 120L
    }

    private data class RawResponse(val status: Int, val body: String, val serverEpochMs: Long)

    private fun request(
        method: String,
        path: String,
        body: JSONObject?,
        accessToken: String?,
        extraHeaders: Map<String, String> = emptyMap()
    ): RawResponse {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 9000
            readTimeout = 12000
            setRequestProperty("apikey", PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
            if (!accessToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $accessToken")
            for ((k, v) in extraHeaders) setRequestProperty(k, v)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            val serverEpochMs = connection.getHeaderFieldDate("Date", -1L)
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = if (stream != null) BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() } else ""
            if (status !in 200..299) throw ApiException(status, friendlyError(text, status))
            return RawResponse(status, text, serverEpochMs)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSession(json: JSONObject, fallbackEmail: String): Session {
        val access = json.optString("access_token", "")
        val refresh = json.optString("refresh_token", "")
        if (access.isBlank() || refresh.isBlank()) throw ApiException(401, "No pudimos iniciar la sesión.")
        val expiresIn = json.optLong("expires_in", 3600L)
        val user = json.optJSONObject("user")
        val uid = user?.optString("id", "") ?: ""
        val email = user?.optString("email", fallbackEmail) ?: fallbackEmail
        if (uid.isBlank()) throw ApiException(401, "La sesión no contiene un usuario válido.")
        return Session(access, refresh, Instant.now().epochSecond + expiresIn, uid, email)
    }

    private fun friendlyError(raw: String, status: Int): String {
        val detail = try {
            val j = JSONObject(raw)
            j.optString("error_description").ifBlank {
                j.optString("msg").ifBlank {
                    j.optString("message").ifBlank { j.optString("error") }
                }
            }
        } catch (_: Exception) { "" }
        if (detail.isNotBlank()) {
            val lower = detail.lowercase()
            return when {
                lower.contains("invalid login") || lower.contains("invalid credentials") -> "Email o contraseña incorrectos."
                lower.contains("email not confirmed") -> "Primero confirmá tu email desde el mensaje que te enviamos."
                lower.contains("user already registered") -> "Ya existe una cuenta con ese email."
                lower.contains("password") && lower.contains("characters") -> "La contraseña no cumple los requisitos de seguridad."
                else -> detail
            }
        }
        return when (status) {
            401 -> "Sesión vencida o credenciales inválidas."
            403 -> "No tenés permiso para realizar esta acción."
            429 -> "Demasiados intentos. Esperá unos minutos y probá de nuevo."
            else -> "No pudimos conectarnos con H.E.L.P. Probá nuevamente."
        }
    }
}
