package com.help.seguridad

import android.app.Activity
import android.app.Application
import android.os.Bundle

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
