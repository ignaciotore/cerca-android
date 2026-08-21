package com.help.seguridad

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class HelpAccessibilityService : AccessibilityService() {
    companion object {
        private const val COMBO_WINDOW_MS = 3_000L
        private val COMBO = intArrayOf(
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP
        )
    }

    private var comboIndex = 0
    private var comboStartedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val prefs = QuickAccessPrefs(this)
        if (!prefs.enabled()) {
            resetCombo()
            return false
        }
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_UP) return false

        val now = SystemClock.elapsedRealtime()
        if (comboIndex > 0 && now - comboStartedAt > COMBO_WINDOW_MS) resetCombo()

        if (event.keyCode == COMBO[comboIndex]) {
            if (comboIndex == 0) comboStartedAt = now
            comboIndex++
            if (comboIndex == COMBO.size) {
                val completedInTime = now - comboStartedAt <= COMBO_WINDOW_MS
                resetCombo()
                if (completedInTime) triggerQuickAccess()
            }
        } else {
            if (event.keyCode == COMBO[0]) {
                comboIndex = 1
                comboStartedAt = now
            } else {
                resetCombo()
            }
        }

        // No consumimos el evento: el volumen sigue funcionando normalmente.
        return false
    }

    private fun triggerQuickAccess() {
        val wallClockNow = System.currentTimeMillis()
        vibrate()
        val prefs = QuickAccessPrefs(this)
        if (prefs.consumeTest(wallClockNow)) {
            sendBroadcast(Intent(QuickAccessPrefs.ACTION_TEST_SUCCESS).setPackage(packageName))
        } else {
            startActivity(
                Intent(this, EmergencyShortcutActivity::class.java)
                    .putExtra(EmergencyShortcutActivity.EXTRA_SOURCE, QuickAccessPrefs.SOURCE_VOLUME)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
            )
        }
    }

    private fun resetCombo() {
        comboIndex = 0
        comboStartedAt = 0L
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            vibrator.vibrate(180L)
        }
    }
}
