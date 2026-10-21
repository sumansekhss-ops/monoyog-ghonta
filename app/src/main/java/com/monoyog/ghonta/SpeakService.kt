package com.monoyog.ghonta

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

/**
 * ব্যাকগ্রাউন্ডে চলা সার্ভিস। স্ক্রিন বন্ধ থাকলেও নির্দিষ্ট সময় পর পর বাংলায় বলে,
 * যতক্ষণ না আপনি থামান।
 */
class SpeakService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_START = "com.monoyog.ghonta.START"
        const val ACTION_STOP = "com.monoyog.ghonta.STOP"
        const val ACTION_TOGGLE = "com.monoyog.ghonta.TOGGLE"
        const val CHANNEL = "monoyog_run"
        const val NOTIF_ID = 7
    }

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingSpeak = false
    private var running = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            speakNow()
            scheduleNext()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopAll()
            return START_NOT_STICKY
        }
        goForeground()
        if (action == ACTION_TOGGLE && running) {
            stopAll()
            return START_NOT_STICKY
        }
        if (action == ACTION_START || action == ACTION_TOGGLE || !running) beginRun()
        return START_STICKY
    }

    private fun beginRun() {
        running = true
        if (wakeLock == null) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "monoyog:run")
        }
        if (wakeLock?.isHeld != true) wakeLock?.acquire()

        if (tts == null) {
            pendingSpeak = true
            tts = TextToSpeech(applicationContext, this)
        } else if (ready) {
            speakNow()
        } else {
            pendingSpeak = true
        }
        scheduleNext()
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            toast("ভয়েস ইঞ্জিন চালু হয়নি")
            return
        }
        var r = t.setLanguage(Locale.forLanguageTag("bn-BD"))
        if (r < 0) r = t.setLanguage(Locale.forLanguageTag("bn-IN"))
        if (r < 0) toast("এই ফোনে বাংলা ভয়েস নেই। Settings > Text-to-speech এ গিয়ে বাংলা ডাউনলোড করুন।")
        t.setSpeechRate(0.9f)
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        ready = true
        if (pendingSpeak) {
            pendingSpeak = false
            speakNow()
        }
    }

    private fun speakNow() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 300)
            handler.postDelayed({ tg.release() }, 800)
        } catch (e: Exception) {
        }
        val t = tts
        if (t != null && ready) {
            t.speak(Store.speech(this), TextToSpeech.QUEUE_FLUSH, null, "bell")
        }
    }

    private fun scheduleNext() {
        val interval = Store.minutes(this) * 60_000L
        Store.setRun(this, true, System.currentTimeMillis() + interval)
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, interval)
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        BellWidget.refresh(this)
    }

    private fun stopAll() {
        running = false
        handler.removeCallbacks(tick)
        tts?.stop()
        Store.setRun(this, false, 0L)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        BellWidget.refresh(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "মনোযোগের ঘণ্টা", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, SpeakService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_pause), "থামান", stop
        ).build()

        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("মনোযোগের ঘণ্টা চলছে")
            .setContentText("পরের ঘণ্টা বাকি")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(action)

        val next = Store.nextAt(this)
        if (next > System.currentTimeMillis()) {
            b.setWhen(next)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        return b.build()
    }

    private fun toast(s: String) {
        handler.post { Toast.makeText(applicationContext, s, Toast.LENGTH_LONG).show() }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        tts?.shutdown()
        tts = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (running) {
            running = false
            Store.setRun(this, false, 0L)
            BellWidget.refresh(this)
        }
        super.onDestroy()
    }
}
