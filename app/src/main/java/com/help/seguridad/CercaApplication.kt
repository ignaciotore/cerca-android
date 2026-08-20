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

    override fun onActivityCreated(activity: Activity, state: Bundle?) {
        if (activity is MainActivity ||
            activity is MedicalProfileActivity ||
            activity is MedicationActivity ||
            activity is QuickAccessSettingsActivity
        ) {
            EnterpriseUiController.attach(activity)
        }
    }
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
