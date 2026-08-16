package com.help.seguridad

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class EmergencyShortcutActivity:AppCompatActivity(){
    companion object{const val EXTRA_SOURCE="quick_access_source"}
    private val h=Handler(Looper.getMainLooper());private lateinit var number:TextView;private lateinit var hint:TextView;private var left=5;private var done=false
    private val tick=object:Runnable{override fun run(){if(done)return;if(left<=0){done=true;launchHelp();return};number.text=left.toString();left--;h.postDelayed(this,1000L)}}
    override fun onCreate(b:Bundle?){super.onCreate(b);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O_MR1){setShowWhenLocked(true);setTurnScreenOn(true)}else{@Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)};ui();buzz();h.post(tick)}
    override fun onKeyDown(code:Int,e:KeyEvent?):Boolean{if(code==QuickAccessPrefs(this).oppositeKey()){cancel();return true};return super.onKeyDown(code,e)}
    override fun onDestroy(){h.removeCallbacksAndMessages(null);super.onDestroy()}
    private fun ui(){
        val cancelKey=if(QuickAccessPrefs(this).oppositeKey()==KeyEvent.KEYCODE_VOLUME_UP)"Volumen +" else "Volumen –"
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(24),dp(48),dp(24),dp(32));setBackgroundColor(Color.parseColor("#FAF7F2"))}
        root.addView(TextView(this).apply{text="H.E.L.P ACTIVADO";textSize=30f;setTextColor(Color.parseColor("#A92C2C"));gravity=Gravity.CENTER;setTypeface(typeface,android.graphics.Typeface.BOLD)})
        root.addView(TextView(this).apply{text="El pedido de ayuda se enviará en";textSize=18f;gravity=Gravity.CENTER;setPadding(0,dp(18),0,0)})
        number=TextView(this).apply{text="5";textSize=76f;setTextColor(Color.parseColor("#A92C2C"));gravity=Gravity.CENTER;setTypeface(typeface,android.graphics.Typeface.BOLD)};root.addView(number)
        root.addView(Button(this).apply{text="CANCELAR";textSize=19f;setTextColor(Color.WHITE);backgroundTintList=ColorStateList.valueOf(Color.parseColor("#173B46"));setOnClickListener{cancel()}},LinearLayout.LayoutParams(-1,dp(62)))
        hint=TextView(this).apply{text="Si fue un error, también podés presionar "+cancelKey+" una vez.";textSize=15f;gravity=Gravity.CENTER;setTextColor(Color.parseColor("#667177"));setPadding(0,dp(18),0,0)};root.addView(hint);setContentView(root)
    }
    private fun cancel(){if(done)return;done=true;h.removeCallbacksAndMessages(null);number.text="✓";hint.text="Pedido cancelado. No se envió ninguna alerta.";h.postDelayed({finish()},650L)}
    private fun launchHelp(){startActivity(Intent(this,MainActivity::class.java).putExtra(MainActivity.EXTRA_AUTO_TRIGGER_HELP,true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP));finish()}
    @Suppress("DEPRECATION") private fun buzz(){val v=getSystemService(Vibrator::class.java)?:return;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)v.vibrate(VibrationEffect.createWaveform(longArrayOf(0,140,80,180),-1))else v.vibrate(longArrayOf(0,140,80,180),-1)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
