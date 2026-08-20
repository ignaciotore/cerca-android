package com.help.seguridad

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.URL
import java.util.concurrent.Executors

object EnterpriseUiController {
    private const val BANNER_TAG = "cerca_enterprise_banner"
    private const val ADMIN_BUTTON_TAG = "cerca_enterprise_admin_button"
    private const val JOIN_BUTTON_TAG = "cerca_enterprise_join_button"
    private const val MASTER_BUTTON_TAG = "cerca_master_admin_button"
    private val executor = Executors.newCachedThreadPool()

    fun attach(activity: Activity) {
        hideFamilyUi(activity)
        val session = SecureSessionStore(activity).load() ?: return
        executor.execute {
            try {
                val state = EnterpriseApi.fetchState(session)
                val prefs = activity.getSharedPreferences("help_account_${session.userId}", Activity.MODE_PRIVATE)
                val previousAccess = prefs.getBoolean("enterprise_access_active", false)
                prefs.edit()
                    .putBoolean("enterprise_access_active", state.enterpriseAccessActive)
                    .putBoolean("enterprise_member", state.enterprise)
                    .putString("enterprise_role", state.role ?: "")
                    .apply()
                activity.runOnUiThread {
                    hideFamilyUi(activity)
                    if (state.enterprise && state.organization != null) {
                        applyBrand(activity, state)
                    } else if (activity is MainActivity) {
                        installJoinButton(activity, session)
                    }
                    if (activity is MainActivity && previousAccess != state.enterpriseAccessActive) activity.recreate()
                }
                if (activity is MainActivity) {
                    val master = try { MasterAdminActivity.canAccess(session) } catch (_: Exception) { false }
                    if (master) activity.runOnUiThread { installMasterButton(activity) }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun applyBrand(activity: Activity, state: EnterpriseApi.State) {
        val org = state.organization ?: return
        val primary = safeColor(org.primaryColor, "#0B5960")
        val secondary = safeColor(org.secondaryColor, "#DDF2F0")
        if (activity is MainActivity) {
            addBanner(activity.findViewById(R.id.homePanel), org, state.role, primary, secondary)
            addBanner(activity.findViewById(R.id.profilePanel), org, state.role, primary, secondary)
            addBanner(activity.findViewById(R.id.setupPanel), org, state.role, primary, secondary)
            tintTree(activity.findViewById(R.id.homePanel), primary, secondary)
            tintTree(activity.findViewById(R.id.profilePanel), primary, secondary)
            tintTree(activity.findViewById(R.id.setupPanel), primary, secondary)
            installAdminButton(activity, state, primary)
            removeJoinButton(activity)
        } else {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            val target = findFirstLinearLayout(content)
            if (target != null) {
                addBanner(target, org, state.role, primary, secondary)
                tintTree(target, primary, secondary)
            }
        }
    }

    private fun installMasterButton(activity: MainActivity) {
        val profile = activity.findViewById<LinearLayout>(R.id.profilePanel)
        if (findByTag(profile, MASTER_BUTTON_TAG) != null) return
        val button = Button(activity).apply {
            tag = MASTER_BUTTON_TAG
            text = "PANEL MAESTRO CERCA"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0B5960"))
            setOnClickListener { activity.startActivity(Intent(activity, MasterAdminActivity::class.java)) }
        }
        val back = profile.findViewById<Button>(R.id.profileBackButton)
        val index = profile.indexOfChild(back).let { if (it >= 0) it else profile.childCount }
        profile.addView(button, index, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 56)).apply {
            setMargins(0, dp(activity, 12), 0, dp(activity, 4))
        })
    }

    private fun installAdminButton(activity: MainActivity, state: EnterpriseApi.State, primary: Int) {
        val home = activity.findViewById<LinearLayout>(R.id.homePanel)
        val existing = findByTag(home, ADMIN_BUTTON_TAG)
        if (state.role != "admin") {
            existing?.visibility = View.GONE
            return
        }
        val button = (existing as? Button) ?: Button(activity).apply {
            tag = ADMIN_BUTTON_TAG
            text = "MI EMPRESA · ADMINISTRAR"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { activity.startActivity(Intent(activity, EnterpriseAdminActivity::class.java)) }
            home.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 56)).apply {
                setMargins(0, dp(activity, 10), 0, 0)
            })
        }
        button.visibility = View.VISIBLE
        button.backgroundTintList = ColorStateList.valueOf(primary)
    }

    private fun installJoinButton(activity: MainActivity, session: SupabaseApi.Session) {
        val profile = activity.findViewById<LinearLayout>(R.id.profilePanel)
        if (findByTag(profile, JOIN_BUTTON_TAG) != null) return
        val button = Button(activity).apply {
            tag = JOIN_BUTTON_TAG
            text = "UNIRME A UNA EMPRESA"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0B5960"))
            setOnClickListener {
                val input = EditText(activity).apply {
                    hint = "Código de 8 caracteres"
                    isSingleLine = true
                    setPadding(dp(activity, 18), dp(activity, 10), dp(activity, 18), dp(activity, 10))
                }
                AlertDialog.Builder(activity)
                    .setTitle("Unirme a una empresa")
                    .setMessage("Ingresá el código que te envió el administrador empresarial.")
                    .setView(input)
                    .setNegativeButton("CANCELAR", null)
                    .setPositiveButton("UNIRME") { _, _ ->
                        val code = input.text.toString().trim().uppercase()
                        if (code.length != 8) {
                            Toast.makeText(activity, "Ingresá un código válido.", Toast.LENGTH_LONG).show()
                            return@setPositiveButton
                        }
                        executor.execute {
                            try {
                                val joined = EnterpriseApi.join(session, code)
                                activity.runOnUiThread {
                                    Toast.makeText(activity, "Listo. Ahora usás CERCA con ${joined.organization?.name ?: "tu empresa"}.", Toast.LENGTH_LONG).show()
                                    activity.recreate()
                                }
                            } catch (e: Exception) {
                                activity.runOnUiThread { Toast.makeText(activity, e.message ?: "No pudimos unir la cuenta.", Toast.LENGTH_LONG).show() }
                            }
                        }
                    }.show()
            }
        }
        val back = profile.findViewById<Button>(R.id.profileBackButton)
        val index = profile.indexOfChild(back).let { if (it >= 0) it else profile.childCount }
        profile.addView(button, index, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 56)).apply {
            setMargins(0, dp(activity, 12), 0, dp(activity, 4))
        })
    }

    private fun removeJoinButton(activity: MainActivity) {
        val profile = activity.findViewById<LinearLayout>(R.id.profilePanel)
        findByTag(profile, JOIN_BUTTON_TAG)?.let { profile.removeView(it) }
    }

    private fun addBanner(container: LinearLayout?, org: EnterpriseApi.Organization, role: String?, primary: Int, secondary: Int) {
        if (container == null || findByTag(container, BANNER_TAG) != null) return
        val activity = container.context as Activity
        val banner = LinearLayout(activity).apply {
            tag = BANNER_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12))
            background = GradientDrawable().apply {
                cornerRadius = dp(activity, 18).toFloat()
                setColor(secondary)
            }
        }
        if (org.logoUrl.isNotBlank()) {
            val image = ImageView(activity).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            banner.addView(image, LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58)).apply { marginEnd = dp(activity, 12) })
            loadLogo(activity, image, org.logoUrl)
        }
        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(activity).apply {
            text = org.name
            textSize = 20f
            setTextColor(primary)
            setTypeface(typeface, Typeface.BOLD)
        })
        texts.addView(TextView(activity).apply {
            text = if (role == "admin") "CERCA EMPRESAS · Administrador" else "CERCA EMPRESAS"
            textSize = 13f
            setTextColor(primary)
        })
        banner.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(banner, 0, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dp(activity, 14))
        })
    }

    private fun tintTree(view: View, primary: Int, secondary: Int) {
        when (view) {
            is Button -> {
                val label = view.text?.toString()?.uppercase().orEmpty()
                val keep = label.contains("PEDIR AYUDA") || label.contains("ELIMINAR") || label.contains("SUSCRIBIRME") || label.contains("CANCELAR INVITACIÓN")
                if (!keep && view.tag != ADMIN_BUTTON_TAG && view.tag != JOIN_BUTTON_TAG) {
                    val soft = label.contains("VOLVER") || label.contains("EDITAR") || label.contains("QUITAR") || label.contains("PRIVACIDAD")
                    view.backgroundTintList = ColorStateList.valueOf(if (soft) secondary else primary)
                    view.setTextColor(if (soft) primary else Color.WHITE)
                }
            }
            is TextView -> {
                val label = view.text?.toString()?.trim().orEmpty()
                if (label == "CERCA" || label.startsWith("CERCA ID")) view.setTextColor(primary)
            }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) tintTree(view.getChildAt(i), primary, secondary)
    }

    private fun hideFamilyUi(view: View) {
        if (view is Button && view.text?.toString()?.uppercase()?.contains("MI CÍRCULO CERCA") == true) view.visibility = View.GONE
        if (view is TextView && view.text?.toString()?.contains("Modo de prueba de planes") == true) (view.parent as? View)?.visibility = View.GONE
        if (view is ViewGroup) for (i in 0 until view.childCount) hideFamilyUi(view.getChildAt(i))
    }

    private fun hideFamilyUi(activity: Activity) {
        hideFamilyUi(activity.findViewById<View>(android.R.id.content))
    }

    private fun findFirstLinearLayout(view: View?): LinearLayout? {
        if (view is LinearLayout) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findFirstLinearLayout(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun findByTag(view: View, tag: String): View? {
        if (view.tag == tag) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findByTag(view.getChildAt(i), tag)?.let { return it }
        return null
    }

    private fun loadLogo(activity: Activity, image: ImageView, url: String) {
        executor.execute {
            try {
                val bitmap = URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                activity.runOnUiThread { image.setImageBitmap(bitmap) }
            } catch (_: Exception) {
                activity.runOnUiThread { image.visibility = View.GONE }
            }
        }
    }

    private fun safeColor(value: String, fallback: String): Int =
        try { Color.parseColor(value) } catch (_: Exception) { Color.parseColor(fallback) }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
