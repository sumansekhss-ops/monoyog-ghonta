package com.monoyog.ghonta

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

class BellWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, build(context))
    }

    companion object {
        fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            v.setTextViewText(
                R.id.wTitle,
                "মনোযোগের ঘণ্টা (" + Store.bn(Store.minutes(ctx)) + " মিনিট)"
            )
            if (Store.running(ctx)) {
                val left = (Store.nextAt(ctx) - System.currentTimeMillis()).coerceAtLeast(0L)
                v.setChronometerCountDown(R.id.wClock, true)
                v.setChronometer(R.id.wClock, SystemClock.elapsedRealtime() + left, null, true)
                v.setViewVisibility(R.id.wClock, View.VISIBLE)
                v.setTextViewText(R.id.wState, "চলছে, ট্যাপ করলে থামবে")
            } else {
                v.setChronometer(R.id.wClock, SystemClock.elapsedRealtime(), null, false)
                v.setViewVisibility(R.id.wClock, View.GONE)
                v.setTextViewText(R.id.wState, "বন্ধ আছে, ট্যাপ করলে শুরু হবে")
            }
            val intent = Intent(ctx, SpeakService::class.java).setAction(SpeakService.ACTION_TOGGLE)
            val pi = PendingIntent.getForegroundService(
                ctx, 2, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.wRoot, pi)
            return v
        }

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, BellWidget::class.java))
            if (ids.isNotEmpty()) mgr.updateAppWidget(ids, build(ctx))
        }
    }
}
