package com.help.seguridad

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.util.ArrayDeque

class HelpAccessibilityService : AccessibilityService() {
    private val presses=ArrayDeque<Long>()
    override fun onServiceConnected(){super.onServiceConnected();serviceInfo=serviceInfo.apply{flags=flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS}}
    override fun onAccessibilityEvent(event: AccessibilityEvent?)=Unit
    override fun onInterrupt()=Unit
    override fun onKeyEvent(event:KeyEvent):Boolean{
        val prefs=QuickAccessPrefs(this)
        if(!prefs.enabled() || event.action!=KeyEvent.ACTION_DOWN || event.repeatCount>0 || event.keyCode!=prefs.key()) return false
        val now=System.currentTimeMillis();presses.addLast(now)
        while(presses.isNotEmpty() && now-presses.peekFirst()>2600L) presses.removeFirst()
        if(presses.size<prefs.count()) return false
        presses.clear(); vibrate()
        if(prefs.consumeTest(now)) sendBroadcast(Intent(QuickAccessPrefs.ACTION_TEST_SUCCESS).setPackage(packageName))
        else startActivity(Intent(this,EmergencyShortcutActivity::class.java).putExtra(EmergencyShortcutActivity.EXTRA_SOURCE,QuickAccessPrefs.SOURCE_VOLUME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        return false
    }
    @Suppress("DEPRECATION") private fun vibrate(){val v=getSystemService(Vibrator::class.java)?:return;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)v.vibrate(VibrationEffect.createOneShot(180L,VibrationEffect.DEFAULT_AMPLITUDE))else v.vibrate(180L)}
}
