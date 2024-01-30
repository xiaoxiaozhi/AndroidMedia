package com.mymedia.videocall.push

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import com.mymedia.R
import com.mymedia.databinding.ActivityViewCallBinding
import com.mymedia.havePermissions
import com.mymedia.videocall.AudioRecordLive
import com.mymedia.videocall.CameraFuture
import com.mymedia.web.SocketManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import javax.inject.Inject


/**
 * camerax开发小组明确回应,camrax的videoCapture不支持获取h264码流，如果需要请从imageAnalyzer获取YUV数据后转换。
 * [连接再此](https://groups.google.com/a/android.com/g/camerax-developers/c/gAAJ9cskMsY)
 *
 *
 * [tools:context=“XX.XX”为布局文件添加context](https://stackoverflow.com/questions/68797319/what-is-the-replacement-for-onclick-depecrated-in-kotlin)
 *
 */
@AndroidEntryPoint
class ViewCallActivity : AppCompatActivity() {
    @Inject
    lateinit var record: SocketManager
    private val audioRecordLive by lazy { AudioRecordLive(this) }
    lateinit var binding: ActivityViewCallBinding

    @Inject
    lateinit var cameraExecutor: ExecutorService

    private val openSetting = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {

    }

    lateinit var cameraFuture: CameraFuture


    override fun onStart() {
        super.onStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = setContentView(this, R.layout.activity_view_call)
        //获取相机权限
        havePermissions(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        ).takeIf {
            it.isNotEmpty()
        }?.also { permission ->
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionResult ->
                // Do checking here
                for ((key, value) in permissionResult) println("key = $key , value = $value")
            }.launch(permission)
        }
        //开启预览
        binding.previewView.post {
            cameraFuture = CameraFuture(binding.previewView)
            cameraFuture.preview()
        }
        //拨打电话
        binding.button1.setOnClickListener {
            cameraFuture.record()
        }
    }

    private fun showRational(permission: String) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            println("用户拒绝后显示原因")//
        } else {
            println("用户点击了禁止再次访问")// 这时候要 导航到权限设置窗口，手动设置
            // 必须在 LifecycleOwners的STARTED 之前调用 registerForActivityResult. 否则报错 推荐用委托形式
            openSetting.launch(Intent().apply {
                action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.parse("package:$packageName")
            })
        }
    }

    companion object {
        private const val TAG = "ViewCallActivity"
    }
}


//    private fun requestPermission(permissionArray: Array<String>, index: Int) {
//        if (index >= permissionArray.size) {
//            return
//        }
//        if (havePermission(permissionArray[index])) {
//            requestPermission(permissionArray, index + 1)
//        } else {
//            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
//                println("RECORD_AUDIO------$it")
//                if (it) {
//                    requestPermission(permissionArray, index + 1)
//                } else {
//                    ActivityCompat.shouldShowRequestPermissionRationale(this, permissionArray[index]).apply {
//                        println("shouldShowRequestPermissionRationale------$this")
//                    }
//                }
//            }.launch(permissionArray[index])
//        }
//    }




