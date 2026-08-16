package com.help.seguridad

import android.content.Context
import android.view.KeyEvent

class QuickAccessPrefs(context: Context) {
    companion object {
        const val ACTION_TEST_SUCCESS = "com.help.seguridad.QUICK_ACCESS_TEST_SUCCESS"
        const val SOURCE_VOLUME = "volume"
        const val SOURCE_WIDGET = "widget"
        private const val PREFS = "help_quick_access"
    }
    private val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled() = p.getBoolean("enabled", false)
    fun setEnabled(v:Boolean) { p.edit().putBoolean("enabled",v).apply() }
    fun key() = p.getInt("key", KeyEvent.KEYCODE_VOLUME_DOWN)
    fun setKey(v:Int) { p.edit().putInt("key",v).apply() }
    fun count() = p.getInt("count",4).coerceIn(3,5)
    fun setCount(v:Int) { p.edit().putInt("count",v.coerceIn(3,5)).apply() }
    fun oppositeKey() = if(key()==KeyEvent.KEYCODE_VOLUME_DOWN) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN
    fun setPending(v:Boolean) { p.edit().putBoolean("pending",v).apply() }
    fun pending() = p.getBoolean("pending",false)
    fun beginTest() { p.edit().putLong("test_until",System.currentTimeMillis()+20_000L).apply() }
    fun consumeTest(now:Long):Boolean {
        val until=p.getLong("test_until",0L)
        if(until<=0L || now>until){p.edit().remove("test_until").apply();return false}
        p.edit().remove("test_until").apply();return true
    }
}
