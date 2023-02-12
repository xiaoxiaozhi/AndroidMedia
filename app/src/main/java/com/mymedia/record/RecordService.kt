package com.mymedia.record

import android.app.Activity.RESULT_OK
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.mymedia.CHANNEL_ID
import com.mymedia.R
import java.io.File

/**
 * TODO 暂停功能
 */
class RecordService : Service() {

    private val mediaProjectionManager: MediaProjectionManager? by lazy {
        getSystemService()
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("startId----$startId, flags-----$flags,  intent-------${intent?.extras.toString()}")
        obtainMediaProjection(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun obtainMediaProjection(intent: Intent?) {
        val notification: Notification =
            NotificationCompat.Builder(this@RecordService, CHANNEL_ID)
                .setContentTitle("前台服务")
                .setContentText("显示前台服务")
                .setSmallIcon(R.drawable.screen_capture_24)
                .setTicker("什么是ticker")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                107,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(107, notification)
        }
        if (intent != null) {
            mediaProjectionManager?.getMediaProjection(RESULT_OK, intent)?.apply {
                H264Encode(this, File(filesDir, "record.h264"),File(filesDir,"h264.txt")).encode()
            } ?: println("getMediaProjection-----is  null")
        }
    }
}