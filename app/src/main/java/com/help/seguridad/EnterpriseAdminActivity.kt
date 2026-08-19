package com.help.seguridad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class EnterpriseAdminActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private var session: SupabaseApi.Session? = null
    private var state: EnterpriseApi.State? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SecureSessionStore(this).load()
        if (session == null) {
            finish()
            return
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(30))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F7FAF9"))
            addView(root)
        })
        root.addView(TextView(this).apply {
            text = "Cargando tu cuenta empresarial…"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#657579"))
        })
        load()
    }

    private fun load() {
        val current = session ?: return
        executor.execute {
            try {
                val s = EnterpriseApi.fetchState(current)
                if (!s.enterprise || s.role != "admin" || s.organization == null) {
                    throw IllegalStateException("Esta cuenta no es administradora empresarial.")
                }
                val members = EnterpriseApi.fetchMembers(current)
                runOnUiThread {
                    state = s
                    render(s, members)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    root.removeAllViews()
                    root.addView(TextView(this).apply {
                        text = e.message ?: "No pudimos cargar la empresa."
                        textSize = 18f
                        gravity = Gravity.CENTER
                        setPadding(0, dp(50), 0, 0)
                    })
                }
            }
        }
    }

    private fun render(s: EnterpriseApi.State, members: List<EnterpriseApi.Member>) {
        val org = s.organization ?: return
        val primary = safeColor(org.primaryColor, "#0B5960")
        val secondary = safeColor(org.secondaryColor, "#DDF2F0")
        root.removeAllViews()

        root.addView(TextView(this).apply {
            text = org.name
            textSize = 28f
            setTextColor(primary)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Administración empresarial · CERCA"
            textSize = 15f
            setTextColor(Color.parseColor("#657579"))
            setPadding(0, dp(4), 0, dp(16))
        })

        root.addView(card(secondary).apply {
            addView(TextView(this@EnterpriseAdminActivity).apply {
                text = "${members.size} usuario(s) activos"
                textSize = 22f
                setTextColor(primary)
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(this@EnterpriseAdminActivity).apply {
                text = if (org.billingMode == "company_pays") "Plan: la empresa cubre el acceso." else "Plan: cada usuario mantiene su suscripción CERCA."
                textSize = 14f
                setTextColor(Color.parseColor("#657579"))
                setPadding(0, dp(5), 0, 0)
            })
        })

        root.addView(Button(this).apply {
            text = "INVITAR USUARIO POR WHATSAPP"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            backgroundTintList = ColorStateList.valueOf(primary)
            setOnClickListener { createAndShareInvite() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)).apply {
            setMargins(0, dp(14), 0, dp(16))
        })

        root.addView(TextView(this).apply {
            text = "Usuarios de ${org.name}"
            textSize = 20f
            setTextColor(primary)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })

        members.forEach { member ->
            root.addView(card(secondary).apply {
                addView(TextView(this@EnterpriseAdminActivity).apply {
                    text = member.fullName.ifBlank { member.email }
                    textSize = 17f
                    setTextColor(Color.parseColor("#263238"))
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(this@EnterpriseAdminActivity).apply {
                    text = member.email
                    textSize = 14f
                    setTextColor(Color.parseColor("#657579"))
                })
                addView(TextView(this@EnterpriseAdminActivity).apply {
                    text = if (member.role == "admin") "Administrador" else "Usuario empresarial"
                    textSize = 13f
                    setTextColor(primary)
                    setPadding(0, dp(5), 0, 0)
                })
            })
        }

        root.addView(Button(this).apply {
            text = "VOLVER"
            setTextColor(primary)
            backgroundTintList = ColorStateList.valueOf(secondary)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
            setMargins(0, dp(18), 0, 0)
        })
    }

    private fun createAndShareInvite() {
        val current = session ?: return
        val org = state?.organization ?: return
        Toast.makeText(this, "Generando invitación…", Toast.LENGTH_SHORT).show()
        executor.execute {
            try {
                val invite = EnterpriseApi.createInvite(current, 50)
                val message = buildString {
                    append("Te invito a usar CERCA con ")
                    append(org.name)
                    append(".\n\n")
                    append("1. Instalá o abrí CERCA: https://play.google.com/store/apps/details?id=com.help.seguridad\n")
                    append("2. Creá/iniciá tu cuenta.\n")
                    append("3. Entrá a Mi perfil > UNIRME A UNA EMPRESA.\n")
                    append("4. Ingresá este código: ")
                    append(invite.code)
                }
                runOnUiThread {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Código CERCA Empresa", invite.code))
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                    }, "Enviar invitación"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, e.message ?: "No pudimos generar la invitación.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun card(borderColor: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), borderColor)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(7), 0, dp(7))
            }
        }

    private fun safeColor(value: String, fallback: String): Int =
        try { Color.parseColor(value) } catch (_: Exception) { Color.parseColor(fallback) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
