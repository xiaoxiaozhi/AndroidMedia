package com.mymedia.projection.send

import android.app.Activity
import android.app.Notification
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
import com.mymedia.projection.send.SendActivity.Companion.STATUS_OPEN
import com.mymedia.projection.send.SendActivity.Companion.STATUS_TEST
import com.mymedia.web.SocketManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SendService : Service() {
    @Inject
    lateinit var record: SocketManager

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
        println(
            "startId----$startId, flags-----$flags,  intent-------${
                intent?.getIntExtra(
                    "type",
                    0
                )
            }"
        )
        intent?.apply {
            when (this.getIntExtra("type", 0)) {
                0 -> println("默认")
                STATUS_TEST -> record.senText()
                STATUS_OPEN -> obtainMediaProjection(intent)
            }
        } ?: println("onStartCommand-----intent is null!!!")
        return START_STICKY
    }

    private fun obtainMediaProjection(intent: Intent?) {
        val notification: Notification =
            NotificationCompat.Builder(this@SendService, CHANNEL_ID)
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
            mediaProjectionManager?.getMediaProjection(Activity.RESULT_OK, intent)?.apply {
                H264Record(this, record).encode()
            } ?: println("getMediaProjection-----is  null")
        }
    }
}