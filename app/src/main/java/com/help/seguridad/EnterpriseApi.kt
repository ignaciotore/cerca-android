package com.help.seguridad

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object EnterpriseApi {
    private const val ENDPOINT = "${SupabaseApi.BASE_URL}/functions/v1/cerca-enterprise"

    data class Organization(
        val id: String,
        val name: String,
        val slug: String,
        val logoUrl: String,
        val primaryColor: String,
        val secondaryColor: String,
        val accentColor: String,
        val billingMode: String,
        val licenseLimit: Int?
    )

    data class State(
        val enterprise: Boolean,
        val role: String?,
        val enterpriseAccessActive: Boolean,
        val memberCount: Int,
        val canInvite: Boolean,
        val organization: Organization?
    )

    data class Member(
        val userId: String,
        val fullName: String,
        val email: String,
        val role: String,
        val status: String,
        val joinedAt: String
    )

    data class Invite(val code: String, val maxUses: Int)

    fun fetchState(session: SupabaseApi.Session): State =
        parseState(request("GET", "state", null, session))

    fun join(session: SupabaseApi.Session, code: String): State =
        parseState(request("POST", "join", JSONObject().put("code", code.trim().uppercase()), session))

    fun createInvite(session: SupabaseApi.Session, maxUses: Int = 50): Invite {
        val json = request("POST", "create_invite", JSONObject().put("max_uses", maxUses.coerceIn(1, 500)), session)
        val invite = json.optJSONObject("invite") ?: throw IllegalStateException("No se generó la invitación.")
        return Invite(invite.optString("code", ""), invite.optInt("max_uses", maxUses))
    }

    fun fetchMembers(session: SupabaseApi.Session): List<Member> {
        val json = request("GET", "members", null, session)
        val arr = json.optJSONArray("members") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(Member(
                    userId = o.optString("user_id", ""),
                    fullName = o.optString("full_name", ""),
                    email = o.optString("email", ""),
                    role = o.optString("role", "member"),
                    status = o.optString("status", "active"),
                    joinedAt = o.optString("joined_at", "")
                ))
            }
        }
    }

    private fun parseState(json: JSONObject): State {
        val org = json.optJSONObject("organization")
        return State(
            enterprise = json.optBoolean("enterprise", false),
            role = json.optString("role", "").ifBlank { null },
            enterpriseAccessActive = json.optBoolean("enterprise_access_active", false),
            memberCount = json.optInt("member_count", 0),
            canInvite = json.optBoolean("can_invite", false),
            organization = org?.let {
                Organization(
                    id = it.optString("id", ""),
                    name = it.optString("name", ""),
                    slug = it.optString("slug", ""),
                    logoUrl = it.optString("logo_url", ""),
                    primaryColor = it.optString("primary_color", "#0B5960"),
                    secondaryColor = it.optString("secondary_color", "#DDF2F0"),
                    accentColor = it.optString("accent_color", "#D95F52"),
                    billingMode = it.optString("billing_mode", "user_pays"),
                    licenseLimit = if (it.isNull("license_limit")) null else it.optInt("license_limit")
                )
            }
        )
    }

    private fun request(method: String, action: String, body: JSONObject?, session: SupabaseApi.Session): JSONObject {
        val connection = (URL("$ENDPOINT?action=$action").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("apikey", SupabaseApi.PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Accept", "application/json")
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
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = if (stream != null) BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() } else "{}"
            val json = try { JSONObject(text.ifBlank { "{}" }) } catch (_: Exception) { JSONObject() }
            if (status !in 200..299) throw IllegalStateException(json.optString("error", "No pudimos conectarnos con CERCA Empresas."))
            return json
        } finally {
            connection.disconnect()
        }
    }
}
