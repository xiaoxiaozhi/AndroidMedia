package com.mymedia.record

import android.content.ComponentName
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import com.mymedia.databinding.ActivityRecordBinding
import androidx.databinding.DataBindingUtil.setContentView
import com.mymedia.R
import java.io.File

/**
 * [官方文档，没啥用](https://developer.android.google.cn/guide/topics/large-screens/media-projection)
 * 录屏
 * 1.动态申请录屏权限
 *   首先通过系统服务获取MediaProjectionManager，再获取录屏Intent，再调用StartActivityForResult 此时屏幕会弹出是否的对话框。选择后resultCode=0不同意录屏 = -1(RESULT_OK)同意录屏
 *   Android10 29 及以上的版本，需要在前台服务中获取MediaProjection否则报错。在startForeground(107, notification, FOREGROUND_SERVICE_TYPE_LOCATION)要填入启动类型
 *   Android10 29 及以上的版本，要在AndroidManifest.xml 的Service标签中设置 android:foregroundServiceType(自动补全能看到有多少类型,可以填入多个类型用 | 组合在一起)。
 *                以及在服务的onStartCommand中调用 startForeground(107, notification, FOREGROUND_SERVICE_TYPE_LOCATION)的时候要填入类型参数(就是在清单文件中填的类型，这里只要填一种即可)
 *   Android9 28及以上系统必须在配置文件中添加android:name="android.permission.FOREGROUND_SERVICE 权限请求否则报错
 *
 * 2.获取屏幕数据源
 *   在得到权限后调用   mediaProjectionManager?.getMediaProjection(activityResult.resultCode, data)得到数据源类 MediaProjection
 * 3.配置编码器Mediacodec
 *   mediaFormat 配置参数
 *   mediacodec.config()
 * 4.编码器和数据源MediaProjection组合在一起输出
 *   encode.createInputSurface()
 *   mediaProjection.createVirtualDisplay
 * attention：Android10 开始录屏需要在前台服务中心执行否则会报错  Media projections require a foreground service of type ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
 */
class RecordActivity : AppCompatActivity() {

    private val mediaProjectionManager: MediaProjectionManager? by lazy {
        getSystemService()
    }
    lateinit var binding: ActivityRecordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = setContentView(this, R.layout.activity_record)
        //1. 动态申请录屏权限
        binding.button1.setOnClickListener {
            mediaProjectionManager?.createScreenCaptureIntent()
                ?.run {
                    register.launch(this)
                } ?: println("mediaProjectionManager 获取失败------")
        }
    }

    private val register =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            println("resultCode------------(${activityResult.resultCode})")
            if (activityResult.resultCode == RESULT_OK) {
                activityResult.data?.apply {
                    component = ComponentName(
                        this@RecordActivity.packageName,
                        RecordService::class.java.name
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        //android 8+ 功能类类似startService 仍然要在 service的startCommand()方法内调用startForeground()
                        startForegroundService(this)
                    } else {
                        startService(this)
                    }
                } ?: println("activityResult.data不存在")
            } else {
                println("不同意-----")
            }
        }

    companion object {
        val size = (720 to 1280)
    }
}