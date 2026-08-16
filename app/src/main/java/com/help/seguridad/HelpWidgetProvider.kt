package com.help.seguridad

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class HelpWidgetProvider:AppWidgetProvider(){
    override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){ids.forEach{id->val i=Intent(c,EmergencyShortcutActivity::class.java).putExtra(EmergencyShortcutActivity.EXTRA_SOURCE,QuickAccessPrefs.SOURCE_WIDGET);val pi=PendingIntent.getActivity(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val v=RemoteViews(c.packageName,R.layout.widget_help);v.setOnClickPendingIntent(R.id.widgetHelpButton,pi);m.updateAppWidget(id,v)}}
}
