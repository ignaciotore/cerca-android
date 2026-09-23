package com.help.seguridad

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class CercaApplication : Application(), Application.ActivityLifecycleCallbacks {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is MainActivity ||
            activity is MedicalProfileActivity ||
            activity is MedicationActivity ||
            activity is QuickAccessSettingsActivity
        ) {
            EnterpriseUiController.attach(activity)
        }
        promptNotificationsIfNeeded(activity)
    }

    private fun promptNotificationsIfNeeded(activity: Activity) {
        if (activity !is MainActivity) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        if (activity.intent.getBooleanExtra("cerca_notification_prompted", false)) return

        activity.intent.putExtra("cerca_notification_prompted", true)
        activity.window.decorView.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            AlertDialog.Builder(activity)
                .setTitle("Activá las alertas de CERCA")
                .setMessage("CERCA necesita permiso para mostrarte las alertas de emergencia de tu Red, incluso cuando no estás usando la app.")
                .setCancelable(false)
                .setPositiveButton("ACTIVAR NOTIFICACIONES") { _, _ ->
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        302
                    )
                }
                .setNegativeButton("AHORA NO", null)
                .show()
        }
    }

    // MainActivity resuelve empresa/rol dentro de su propio arranque, después de inflar la UI.
    // Evitamos consultar acá porque onActivityCreated ocurre antes de setContentView y generaba la carrera
    // "Individual -> Empresarial" que se veía al abrir la app.
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
