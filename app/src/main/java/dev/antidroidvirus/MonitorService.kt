package dev.antidroidvirus

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager

/**
 * Servizio leggero: nessun lavoro continuo. Si sveglia su apertura di fotocamera o microfono,
 * all'accensione dello schermo, e con una lettura periodica (60 s a schermo acceso, 15 min spento)
 * che serve soprattutto per la posizione, che non ha callback pubbliche.
 */
class MonitorService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var monitor: Monitor

    private val pollNow = Runnable { monitor.poll() }
    private val periodic = object : Runnable {
        override fun run() {
            monitor.poll()
            val interactive = getSystemService(PowerManager::class.java).isInteractive
            handler.postDelayed(this, if (interactive) INTERVAL_SCREEN_ON_MS else INTERVAL_SCREEN_OFF_MS)
        }
    }

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) = schedulePoll()
    }

    private val audioCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            if (configs.isNotEmpty()) schedulePoll()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(periodic)
            handler.post(periodic)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NOTIFICATION_ID, serviceNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        thread = HandlerThread("monitor").also { it.start() }
        handler = Handler(thread.looper)
        monitor = Monitor(this)

        getSystemService(CameraManager::class.java).registerAvailabilityCallback(cameraCallback, handler)
        getSystemService(AudioManager::class.java).registerAudioRecordingCallback(audioCallback, handler)
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            null,
            handler,
        )
        handler.post(periodic)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        getSystemService(CameraManager::class.java).unregisterAvailabilityCallback(cameraCallback)
        getSystemService(AudioManager::class.java).unregisterAudioRecordingCallback(audioCallback)
        unregisterReceiver(screenReceiver)
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun schedulePoll() {
        // AppOps si aggiorna un istante dopo l'apertura del dispositivo.
        handler.removeCallbacks(pollNow)
        handler.postDelayed(pollNow, 800)
    }

    private fun serviceNotification(): Notification = Notification.Builder(this, CHANNEL_SERVICE)
        .setSmallIcon(R.drawable.ic_notify)
        .setContentTitle("Monitoraggio permessi attivo")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        )
        .build()

    companion object {
        const val CHANNEL_SERVICE = "service"
        private const val NOTIFICATION_ID = 1
        private const val INTERVAL_SCREEN_ON_MS = 60_000L
        private const val INTERVAL_SCREEN_OFF_MS = 15 * 60_000L
        private const val PREFS = "service"
        private const val KEY_ENABLED = "enabled"

        @Volatile
        var running = false
            private set

        fun isEnabled(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        fun start(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).apply()
            context.startForegroundService(Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
