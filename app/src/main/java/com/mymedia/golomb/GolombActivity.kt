package com.mymedia.golomb

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.getSystemService
import com.mymedia.R
import com.mymedia.databinding.ActivityGolombBinding
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import com.mymedia.AndroidObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class GolombActivity : AppCompatActivity() {
    private val cameraManager: CameraManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private val cameraHandler: Handler = Handler(HandlerThread("cameraHandlerThread").apply { start() }.looper)
    private var session: CameraCaptureSession? = null
    private val encode = EncodeH264()
    private lateinit var device: CameraDevice
    private lateinit var binding: ActivityGolombBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = setContentView(this, R.layout.activity_golomb)
        binding.button1.setOnClickListener {
            val previewRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                .apply { addTarget(binding.surface.holder.surface) }//设置请求的surface必须属于 createCaptureSession(targets) targets的列表中
            session?.setRepeatingRequest(previewRequest.build(), null, cameraHandler)

            val record = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(encode.getInputSurface())
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
            }
            session?.setRepeatingRequest(record.build(), null, cameraHandler)
            encode.play()
        }
        binding.surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                initCamera()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Log.i(
                    TAG,
                    "surfaceChanged------format:${format2String(format)} (width,height) = ${(width to height)}"
                )
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {

            }

        })
    }

    /**
     * 默认后置摄像头1
     *    前置摄像头0
     */
    private fun initCamera(orientation: Int = 1) {
        lifecycleScope.launch {
            val identifier = cameraManager.run {
                cameraIdList.first { identifier ->
                    getCameraCharacteristics(identifier).get(CameraCharacteristics.LENS_FACING) == orientation
                }
            }//固定摄像头标识符都是从0开始的整数。可插拔不是
            val characteristics = cameraManager.getCameraCharacteristics(identifier)
            //跟局SurfaceView的大小算出最合适的分辨率

            val size = getPreviewOutputSize(
                Size(binding.surface.width, binding.surface.height), characteristics, SurfaceHolder::class.java
            )
            Log.i(TAG, "getPreviewOutputSize----$size")
            val smartSize = SmartSize(size.width, size.height)
            //
            if (binding.surface.display.rotation == 0) {
                (binding.surface.layoutParams as ConstraintLayout.LayoutParams).apply {
                    width = smartSize.short
                    height = smartSize.long
                    binding.surface.requestLayout()

                }
            } else {
                (binding.surface.layoutParams as ConstraintLayout.LayoutParams).apply {
                    width = smartSize.long
                    height = smartSize.short
                    binding.surface.requestLayout()
                }
            }
            binding.surface.setAspectRatio(//设置流分辨率，不是外观，如果和外观不匹配会发生形变
                size.width, size.height
            )

            //打开摄像头获取CameraDevice
            device = openCamera(cameraManager, identifier, cameraHandler)
            // Creates list of Surfaces where the camera will output frames
            val targets = listOf(binding.surface.holder.surface, encode.getInputSurface())
            //1. 创建 CameraCaptureSession
            // Start a capture session using our open camera and list of Surfaces where frames will go
            session = createCaptureSession(device, targets, cameraHandler)
            Log.i(TAG, "initCamera success!!!")
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun openCamera(manager: CameraManager, cameraId: String, handler: Handler? = null): CameraDevice =
        suspendCancellableCoroutine { cont ->
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) = cont.resume(device)

                override fun onDisconnected(device1: CameraDevice) {
                    println("Camera $cameraId has been disconnected")
                    device1.close()
                }

                override fun onError(device: CameraDevice, error: Int) {
                    val msg = when (error) {
                        ERROR_CAMERA_DEVICE -> "Fatal (device)"
                        ERROR_CAMERA_DISABLED -> "Device policy"
                        ERROR_CAMERA_IN_USE -> "Camera in use"
                        ERROR_CAMERA_SERVICE -> "Fatal (service)"
                        ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                        else -> "Unknown"
                    }
                    val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                    println(exc.message)
                    if (cont.isActive) cont.resumeWithException(exc)
                }
            }, handler)

        }

    /**
     * Starts a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine
     */
    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->
        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            device.createCaptureSession(
                SessionConfiguration(SessionConfiguration.SESSION_REGULAR, targets.map {
                    OutputConfiguration(it)
                }, AndroidObject.executorService, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cont.resume(session)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        val exc = RuntimeException("Camera ${device.id} session configuration failed")
                        println(exc.message)
                        cont.resumeWithException(exc)
                    }

                })
            )
        } else {
            device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {

                override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    val exc = RuntimeException("Camera ${device.id} session configuration failed")
                    println(exc.message)
                    cont.resumeWithException(exc)
                }
            }, handler)
        }
    }

    private fun format2String(type: Int) = when (type) {
        ImageFormat.RAW_SENSOR -> Log.i(TAG, "ImageFormat.RAW_SENSOR")
        ImageFormat.PRIVATE -> Log.i(TAG, "ImageFormat.PRIVATE")
        ImageFormat.RAW_PRIVATE -> Log.i(TAG, "ImageFormat.RAW_PRIVATE")
        ImageFormat.YUV_420_888 -> Log.i(TAG, "ImageFormat.YUV_420_888")
        ImageFormat.RAW10 -> Log.i(TAG, "ImageFormat.RAW10")
        ImageFormat.JPEG -> Log.i(TAG, "ImageFormat.JPEG")
        ImageFormat.YV12 -> Log.i(TAG, "ImageFormat.YV12")
        ImageFormat.NV21 -> Log.i(TAG, "ImageFormat.NV21")//PixelFormat.YCbCr_420_SP值一样
        ImageFormat.HEIC -> Log.i(TAG, "ImageFormat.HEIC")
        PixelFormat.RGBA_8888 -> Log.i(TAG, "PixelFormat.RGBA_8888")
        PixelFormat.RGB_565 -> Log.i(TAG, "PixelFormat.RGB_565")
        else -> Log.i(TAG, "其它格式0X${type.toString(16)}")
    }

    companion object {
        private const val TAG = "GolombActivity"
    }
}