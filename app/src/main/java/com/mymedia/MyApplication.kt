package com.mymedia

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build



class MyApplication : Application()
//    , CameraXConfig.Provider
{
//    lateinit var component: MyComponent
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
//        component = DaggerMyComponent.create()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            println("Build.VERSION.SDK_INT = ${Build.VERSION.SDK_INT}")
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }// importance 此参数确定出现任何属于此渠道的通知时如何打断用户
            // 注册通知
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        } else {
            println("--------sdk小于android O 26----------")
        }
    }

//    override fun getCameraXConfig(): CameraXConfig {
//        return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())//查看defaultConfig()源码会发现，那三个Provider是必须的，否则会报错。配置主要在下面几行代码体现
//            .setAvailableCamerasLimiter(CameraSelector.DEFAULT_FRONT_CAMERA).setCameraExecutor(mainExecutor)
//            .setMinimumLoggingLevel(Log.INFO).setSchedulerHandler(Handler(Looper.getMainLooper()))
//            .build()//从 Camera2Config 获取配置
//
//    }
}