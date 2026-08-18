package com.help.seguridad

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MedicationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        MedicationReminderScheduler.rescheduleAfterBoot(context)
    }
}
