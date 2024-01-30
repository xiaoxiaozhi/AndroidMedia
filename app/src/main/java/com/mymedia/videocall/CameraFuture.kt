package com.mymedia.videocall

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.mymedia.FileUtils
import com.mymedia.videocall.push.ViewCallActivity
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 相机管理不能作为数据层，因为数据的产生要依靠界面PreviewView类，只能作为界面层，属于Activity的一部分
 *
 */
class CameraFuture constructor(
    private val previewView: PreviewView,
) {
    private val context: Context = previewView.context
    private val lifecycleOwner: LifecycleOwner = previewView.context as ViewCallActivity
    private val executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> by lazy {
        ProcessCameraProvider.getInstance(
            previewView.context
        )
    }
    private lateinit var cameraProvider: ProcessCameraProvider
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var cameraSelector: CameraSelector? = null
    private var preview: Preview? = null
    private var aspectRatio = 0
    private var imageAnalyzer: ImageAnalysis? = null
    fun preview() {
        cameraProviderFuture.addListener(Runnable {
            cameraProvider = cameraProviderFuture.get()
            Log.i(TAG, "cameraProvider----${cameraProvider.hashCode()}")
            Log.i(TAG, "previewView.width=${previewView.width} previewView.height=${previewView.height}")
            aspectRatio = aspectRatio(previewView.width, previewView.height)
            cameraSelector = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } //摄像头选择器

            previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE// PreviewView选择模式
            previewView.scaleType = PreviewView.ScaleType.FIT_CENTER //FIT_CENTER 默认缩放；类型

            //2. 预览UserCase
//            println("rotation---${binding.previewView.display?.rotation}")//display 在控件绘制完成前，display=null 所以要在post配置
            preview = Preview.Builder().apply {
                setTargetAspectRatio(aspectRatio) //不能与setTargetResolution 一同设置，默认4:3  k30pro 4:3时输出分辨率一直是1600X1200 16:9时输出分辨率1920X1080 这个值是固定的吗？？？好像不能自定义宽高比
//                    when (binding.previewView.display?.rotation) {
//                        0 -> setTargetResolution(Size(480, 720))//0 竖屏
//                        3 -> setTargetResolution(Size(720, 480))//3 横屏
//                    }
                setTargetRotation(previewView.display.rotation)// 屏幕旋转的时候要重新设置rotation通过重新创建preview的形式
            }.build()
            preview!!.setSurfaceProvider(previewView.surfaceProvider)
            cameraProvider!!.unbindAll()
            val camera = cameraProvider!!.bindToLifecycle(
                lifecycleOwner, cameraSelector!!, preview
            )//调用bindToLifecycle的时候最好包含所有用例  k30pro添加超过三个用例的时候报错。
        }, ContextCompat.getMainExecutor(context))
    }

    fun obtainCameraData() {

    }

    /**
     * 图像分析
     * TODO 摄像机状态怎么判断，多次执行record 发现报错 IllegalArgumentException: No supported surface combination is found for camera device - Id
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun record() {
        cameraProviderFuture.addListener(Runnable {
            cameraProvider = cameraProviderFuture.get()
            Log.i(TAG, "cameraProvider----${cameraProvider.hashCode()}")
            val file = File(context.filesDir, "camera.h264.txt").apply {
                if (!exists()) {
                    createNewFile()
                }
            }
            val size = when (previewView.display?.rotation) {
                0 -> Size(480, 720)//0 竖屏
                else -> Size(720, 480)//3 横屏
            }

            Log.i(TAG, "Rotation----${previewView.display.rotation}")
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetRotation(previewView.display.rotation)//无论设置何值，从YUVImage得到的图片总是逆时针旋转90
                .setTargetAspectRatio(aspectRatio)
//                .setTargetResolution(size)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)//仅支持 YUV_420_888 和 RGBA_8888。默认格式为 YUV_420_888。
                .setBackgroundExecutor(executorService)//默认有执行器，也可以设置
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)//设置背压策略，默认0
                .setImageQueueDepth(6)//当背压策略设置为 STRATEGY_BLOCK_PRODUCER 时有效默认值为6
                .build().also {
                    it.setAnalyzer(executorService) { imageProxy ->
                        imageProxy.image?.apply {
                            val byteArray = yuv420Image2Nv21(this)
                            if (byteArray != null) {
                                File(context.filesDir, "yuv.jpg").apply {
                                    if (!exists()) {
                                        createNewFile()
                                    }
                                    YuvImage(byteArray, ImageFormat.NV21, width, height, null).compressToJpeg(
                                        Rect(
                                            0,
                                            0,
                                            width,
                                            height
                                        ), 100,
                                        this.outputStream()
                                    )
                                }
////                                FileUtils.writeContent(byteArray, file)
                                it.clearAnalyzer()
                            } else {
                                Log.w(TAG, "image byteArray is null")
                            }
                        }
                    }//设置分析器接受图像
//                      it.clearAnalyzer()//停止分析数据
                }
            Log.e(TAG, "cameraProvider is = $cameraProvider")

            val camera = cameraProvider.bindToLifecycle(
                context as AppCompatActivity, cameraSelector!!, imageAnalyzer
            )//调用bindToLifecycle的时候最好包含所有用例  k30pro添加超过三个用例的时候报错。
        }, ContextCompat.getMainExecutor(context))


    }

    /**
     * 计算Preview纵横比
     * previewRatio 更接近4:3 还是 16:9，以此来判断使用哪个比例
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        println("previewRatio----$previewRatio")
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun getDataFromImage(image: Image): ByteArray? {
        val format: Int = image.format
        val width: Int = image.width
        val height: Int = image.height
        // Read image data
        val planes: Array<Image.Plane> = image.planes
        // Check image validity
        checkAndroidImageFormat(image)
        Log.v(TAG, "get data from " + planes.size + " planes")
        Log.v(TAG, "image.format---${image.format} cropRect=${image.cropRect}")
        Log.v(TAG, "width $width")
        Log.v(TAG, "height $height")
        val yuvData = ByteBuffer.allocate((width * height * 1.5).toInt())
        val yBuffer = ByteBuffer.allocate(width * height)

        val yuvRowArray = ByteArray(width)
        val yuvRowArray_1 = ByteArray(width - 1)
        //图片 width * height 有奇数吗？？？
        //Y分量
        val yPlane = image.planes[0]
        assert(yPlane.pixelStride == 1)
        val stuffing = abs(yPlane.rowStride - width)//由于cpu的对齐作用，一行结束后还有填充。(y、u、v都有) 试验手机K30Pro
        Log.i(TAG, "stuffing=$stuffing")
        while (yPlane.buffer.remaining() > 0) {
            yPlane.buffer.get(yuvRowArray)
            yBuffer.put(yuvRowArray)
            if (yPlane.buffer.remaining() > 0) {
                yPlane.buffer.get(ByteArray(stuffing))
            }
        }
        yuvData.put(yBuffer.array())
        for (index in 1..2) {
            val uvPlane = planes[index]// uv 步长 所以放在一起
            val uvBuffer = ByteBuffer.allocate(width * height / 4)
            ByteArray(10).also { array ->
                uvPlane.buffer.get(array)
                Log.i(
                    TAG, "前五个---${
                        array.map {
                            it.toInt()
                        }.toString()
                    }"
                )
                uvPlane.buffer.rewind()
            }

            //u v 步长要么是1 要么是2.目前代码只针对 步长2情况
            assert(uvPlane.pixelStride <= 2)
            //先u后v情况
            while (uvPlane.buffer.remaining() > 0) {
                if (uvPlane.buffer.remaining() == width - 1) {
                    uvPlane.buffer.get(yuvRowArray_1)
                    for (index in yuvRowArray_1.indices step 2) {
                        uvBuffer.put(yuvRowArray_1[index])
                    }
                } else {
                    uvPlane.buffer.get(yuvRowArray)
                    for (index in yuvRowArray.indices step 2) {
                        uvBuffer.put(yuvRowArray[index])
                    }
                    uvPlane.buffer.get(ByteArray(stuffing))
                }

            }
            yuvData.put(uvBuffer.array())
        }
        Log.i(TAG, "yuvData.remaining() = ${yuvData.remaining()}")
        return yuvData.array()
    }

    /**
     * TODO yuv转nv21之后发现 图片要顺时针旋转90度才会摆正，这个跟什么有关？？？？
     */
    private fun yuv420Image2Nv21(image: Image): ByteArray {
        val format: Int = image.format
        val width: Int = image.width
        val height: Int = image.height
        // Read image data
        val planes: Array<Image.Plane> = image.planes
        // Check image validity
        checkAndroidImageFormat(image)
        Log.v(TAG, "get data from " + planes.size + " planes")
        Log.v(TAG, "image.format---${image.format} cropRect=${image.cropRect}")
        Log.v(TAG, "width $width")
        Log.v(TAG, "height $height")

        val yuvData = ByteBuffer.allocate((width * height * 1.5).toInt())
        val yBuffer = ByteBuffer.allocate(width * height)
        val yuvRowArray = ByteArray(width)
        val yuvRowArray_1 = ByteArray(width - 1)
        //图片 width * height 有奇数吗？？？
        //Y分量
        val yPlane = image.planes[0]
        assert(yPlane.pixelStride == 1)
        val stuffing = abs(yPlane.rowStride - width)//由于cpu的对齐作用，一行结束后还有填充。(y、u、v都有) 试验手机K30Pro
        Log.i(TAG, "stuffing=$stuffing")
        while (yPlane.buffer.remaining() > 0) {
            yPlane.buffer.get(yuvRowArray)
            yBuffer.put(yuvRowArray)
            if (yPlane.buffer.remaining() > 0) {
                yPlane.buffer.get(ByteArray(stuffing))
            }
        }
        yuvData.put(yBuffer.array())
        //uv分量
        val nv21Plane = image.planes[2]
        val uvBuffer = ByteBuffer.allocate(width * height / 2)
        while (nv21Plane.buffer.remaining() > 0) {
            if (nv21Plane.buffer.remaining() == width - 1) {
                nv21Plane.buffer.get(yuvRowArray_1)
                uvBuffer.put(yuvRowArray_1)
                uvBuffer.put(0.toByte())
            } else {
                nv21Plane.buffer.get(yuvRowArray)
                uvBuffer.put(yuvRowArray)
                nv21Plane.buffer.get(ByteArray(stuffing))
            }

        }
        yuvData.put(uvBuffer.array())
        return yuvData.array()
    }

    /**
     *
     * Check android image format validity for an image, only support below formats:
     *
     *
     * Valid formats are YUV_420_888/NV21/YV12 for video decoder
     */
    private fun checkAndroidImageFormat(image: Image) {
        val format = image.format
        val planes = image.planes
        when (format) {
            ImageFormat.YUV_420_888, ImageFormat.NV21, ImageFormat.YV12 -> true
            else -> false
        }
    }

    private val COLOR_FormatI420 = 1
    private val COLOR_FormatNV21 = 2

    private fun isImageFormatSupported(image: Image): Boolean {
        val format = image.format
        when (format) {
            ImageFormat.YUV_420_888, ImageFormat.NV21, ImageFormat.YV12 -> return true
        }
        return false
    }

    private fun getDataFromImage(image: Image, colorFormat: Int): ByteArray? {
        require(!(colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21)) { "only support COLOR_FormatI420 " + "and COLOR_FormatNV21" }
        if (!isImageFormatSupported(image)) {
            throw RuntimeException("can't convert Image to byte array, format " + image.format)
        }
        val crop: Rect = image.cropRect
        val format = image.format
        val width: Int = crop.width()
        val height: Int = crop.height()
        val planes = image.planes
        val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
        val rowData = ByteArray(planes[0].rowStride)
        Log.v(TAG, "get data from " + planes.size + " planes")
        var channelOffset = 0
        var outputStride = 1
        for (i in planes.indices) {
            when (i) {
                0 -> {
                    channelOffset = 0
                    outputStride = 1
                }
                1 -> if (colorFormat == COLOR_FormatI420) {
                    channelOffset = width * height
                    outputStride = 1
                } else if (colorFormat == COLOR_FormatNV21) {
                    channelOffset = width * height + 1
                    outputStride = 2
                }
                2 -> if (colorFormat == COLOR_FormatI420) {
                    channelOffset = (width * height * 1.25).toInt()
                    outputStride = 1
                } else if (colorFormat == COLOR_FormatNV21) {
                    channelOffset = width * height
                    outputStride = 2
                }
            }
            val buffer = planes[i].buffer
            val rowStride = planes[i].rowStride
            val pixelStride = planes[i].pixelStride
            Log.v(TAG, "pixelStride $pixelStride")
            Log.v(TAG, "rowStride $rowStride")
            Log.v(TAG, "width $width")
            Log.v(TAG, "height $height")
            Log.v(TAG, "buffer size " + buffer.remaining())
            val shift = if (i == 0) 0 else 1
            val w = width shr shift
            val h = height shr shift
            buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
            for (row in 0 until h) {
                var length: Int
                if (pixelStride == 1 && outputStride == 1) {
                    length = w
                    buffer[data, channelOffset, length]
                    channelOffset += length
                } else {
                    length = (w - 1) * pixelStride + 1
                    buffer[rowData, 0, length]
                    for (col in 0 until w) {
                        data[channelOffset] = rowData[col * pixelStride]
                        channelOffset += outputStride
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
            Log.v(TAG, "Finished reading data from plane $i")
        }
        return data
    }

    companion object {
        private const val TAG = "CameraFuture"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }
}