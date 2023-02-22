package com.mymedia.projection.send

import android.content.ComponentName
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.databinding.DataBindingUtil.setContentView
import com.mymedia.R
import com.mymedia.databinding.ActivitySendBinding
import com.mymedia.projection.receive.ReceiveActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 *
 */
@AndroidEntryPoint
class SendActivity : AppCompatActivity() {
    lateinit var binding: ActivitySendBinding
    private val mediaProjectionManager: MediaProjectionManager? by lazy {
        getSystemService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = setContentView<ActivitySendBinding>(this, R.layout.activity_send)
        binding.button1.setOnClickListener {
            mediaProjectionManager?.createScreenCaptureIntent()
                ?.run {
                    register.launch(this)
                } ?: println("mediaProjectionManager 获取失败------")
        }
        binding.button2.setOnClickListener {
            Intent().apply {
                putExtra("type", STATUS_TEST)
                startSend(this)
            }
        }
        binding.button3.setOnClickListener {
            Intent().apply {
                component =
                    ComponentName(this@SendActivity.packageName, ReceiveActivity::class.java.name)
                startActivity(this)
            }
        }
    }

    private val register =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            println("resultCode------------(${activityResult.resultCode})")
            if (activityResult.resultCode == RESULT_OK) {
                activityResult.data?.putExtra("type", STATUS_OPEN)?.apply(::startSend)
                    ?: println("activityResult.data不存在")
            } else {
                println("不同意-----")
            }
        }

    companion object {
        val size = (720 to 1280)
        const val STATUS_NORMAL = 0
        const val STATUS_TEST = 1
        const val STATUS_OPEN = 2
        const val STATUS_CLOSE = 4
    }

    private fun startSend(intent: Intent) {
        intent.apply {
            component = ComponentName(
                this@SendActivity.packageName,
                SendService::class.java.name
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                //android 8+ 功能类类似startService 仍然要在 service的startCommand()方法内调用startForeground()
                startForegroundService(this)
            } else {
                startService(this)
            }
        }
    }
}