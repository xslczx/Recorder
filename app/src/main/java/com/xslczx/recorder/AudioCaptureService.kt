package com.xslczx.recorder

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.xslczx.recorder.recorder.AudioRecorder
import com.xslczx.recorder.recorder.RecordConfig
import com.xslczx.recorder.recorder.RecorderDurationHandler
import com.xslczx.recorder.recorder.RecorderRecordStreamHandler
import com.xslczx.recorder.recorder.RecorderStateStreamHandler

class AudioCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private val mediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    var isBound = false
        private set
    private val audioRecorder by lazy {
        AudioRecorder(this)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                SERVICE_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(SERVICE_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "内录模式",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(NotificationManager::class.java) as NotificationManager
        manager.createNotificationChannel(serviceChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                mediaProjection =
                    mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, resultData!!)
                return START_STICKY
            }

            ACTION_STOP -> {
                stopServiceSafely()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopServiceSafely()
    }

    fun startRecorder(config: RecordConfig) {
        val projection = mediaProjection ?: return
        config.mediaProjection = projection
        audioRecorder.start(config)
    }

    fun pauseRecorder() = audioRecorder.pause()

    fun resumeRecorder() = audioRecorder.resume()

    fun stopRecorder(stopCb: ((path: String?) -> Unit)?) {
        audioRecorder.stop {
            stopCb?.invoke(it)
            stopServiceSafely()
        }
    }

    private fun stopServiceSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        RecorderConstant.logD("AudioCaptureService", "stopServiceSafely")
    }

    fun setRecorderStateStreamHandler(handler: RecorderStateStreamHandler) {
        audioRecorder.setRecorderStateStreamHandler(handler)
    }

    fun setRecorderRecordStreamHandler(handler: RecorderRecordStreamHandler) {
        audioRecorder.setRecorderRecordStreamHandler(handler)
    }

    fun setRecorderDurationHandler(handler: RecorderDurationHandler) {
        audioRecorder.setRecorderDurationHandler(handler)
    }

    override fun onDestroy() {
        isBound = false
        super.onDestroy()
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(SERVICE_ID)
        audioRecorder.dispose()
        RecorderConstant.logD("AudioCaptureService", "onDestroy")
    }

    override fun onBind(p0: Intent?): IBinder? {
        isBound = true
        RecorderConstant.logD("AudioCaptureService", "onUnbind")
        return binder
    }

    fun unbindServiceSafely(context: Context, conn: ServiceConnection?) {
        if (isBound && conn != null) {
            try {
                context.unbindService(conn)
                RecorderConstant.logD("AudioCaptureService", "unbindService")
            } catch (e: Exception) {
                RecorderConstant.logW("AudioCaptureService", "unbindService", e)
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isBound = false
        RecorderConstant.logD("AudioCaptureService", "onUnbind")
        return super.onUnbind(intent)
    }

    class AudioCaptureBinder(val service: AudioCaptureService) : Binder()

    private val binder = AudioCaptureBinder(this)

    companion object {
        const val ACTION_START = "AudioCaptureService.ACTION_START"
        const val ACTION_STOP = "AudioCaptureService.ACTION_STOP"
        const val EXTRA_RESULT_DATA = "AudioCaptureService.EXTRA_RESULT_DATA"

        private const val SERVICE_ID = 123
        private const val NOTIFICATION_CHANNEL_ID = "AudioCapture channel"

        fun start(context: Context, resultData: Intent, serviceConnection: ServiceConnection) {
            if (isServiceRunning(context)) {
                stop(context)
            }
            val intent = Intent(context, AudioCaptureService::class.java)
            intent.action = ACTION_START
            intent.putExtra(EXTRA_RESULT_DATA, resultData)
            context.startService(intent)
            context.bindService(
                Intent(context, AudioCaptureService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioCaptureService::class.java)
            context.stopService(intent)
        }

        fun isServiceRunning(context: Context): Boolean {
            val service = context.getSystemService(Context.ACTIVITY_SERVICE)
            val manager = service as ActivityManager
            for (serviceInfo in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceInfo.service.className == AudioCaptureService::class.java.name) {
                    return true
                }
            }
            return false
        }
    }
}
