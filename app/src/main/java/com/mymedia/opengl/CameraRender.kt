package com.mymedia.opengl

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import com.mymedia.R
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 数据流，camerax--->SurfaceProvider---->surfaceTexture---->onDrawFrame绘制
 * 1. 创建纹理surfaceTexture
 * 2. 自定义SurfaceProvider，提供一个用于预览的surface，相机数据通过回调函数onSurfaceRequested() 传入第一步创建的纹理，
 *    给纹理设置监听OnFrameAvailableListener ，每当摄像头有数据时就回调onFrameAvailable，在这里调用GlSurfaceView的手动刷新requestRender
 * 3. 绘制图像, 实测发现surfaceTexture接受数据时主动接受，接受摄像头一阵数据，如果不手动调用 surfaceTexture?.updateTexImage()就不会接受下一帧数据
 *    3.1 加载顶点程序和片元程序
 *
 * attention:AbstractFilter子类必须放在onSurfaceCreated 执行，否则报错
 */
class CameraRender(private val context: Context, private val glSurface: GLSurfaceView) : GLSurfaceView.Renderer,
    OnFrameAvailableListener,
    Preview.SurfaceProvider {
    private val executor = Executors.newSingleThreadExecutor()
    private val cameraXHelper: CameraXHelper = CameraXHelper()
    private val textures = IntArray(1)
    private var surfaceTexture: SurfaceTexture? = null
    private lateinit var cameraFilter: CameraFilter
    private val mtx = FloatArray(16)
    private lateinit var imageFilter: ImageFilter

    init {
        glSurface.post {
            Log.i(TAG, "1 glSurface.width= " + glSurface.width + " glSurface.height=" + glSurface.height);
        }

    }

    /**
     * 系统在创建 GLSurfaceView 时调用此方法一次。使用此方法可执行仅需执行一次的操作，例如设置OpenGL环境参数或初始化OpenGL图形对象。
     */
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        cameraXHelper.setUpCamera(context, this)
        //1. 创建纹理
        gl?.also {
            it.glGenTextures(textures.size, textures, 0)
            surfaceTexture = SurfaceTexture(textures[0])
            cameraFilter = CameraFilter(context)
            imageFilter = ImageFilter(context)

            //这个例子说明在 onDrawFrame 渲染不是必须的。只要CameraRender创建完成就能渲染，我猜摄像头数据在onDrawFrame中渲染是因为
            //onDrawFrame能回调很多次，TODO GLSurfaceView 不设置渲染器，可以渲染bitmap吗？
//            val textureId = imageFilter.init(BitmapFactory.decodeResource(context.resources, R.drawable.dog))
//            imageFilter.drawFrame(textureId)

        } ?: Log.w(TAG, "onSurfaceCreated----gl is null")
    }

    /**
     * 当 GLSurfaceView 几何形状发生变化时，系统调用此方法，包括 GLSurfaceView 的大小或设备屏幕的方向发生变化。
     * 例如，当设备从纵向更改为横向时，系统调用此方法。使用此方法响应 GLSurfaceView 容器中的更改。
     */
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged-------width=$width----height=$height--")
        GLES20.glViewport(0, 0, width, height)
    }

    /**
     * 3.绘制图像
     * 系统在每次重绘 GLSurfaceView 时调用此方法。使用此方法作为绘制（和重新绘制）图形对象的主要执行点。
     */
    override fun onDrawFrame(gl: GL10?) {
        //渲染 摄像头数据----------------------------------------------
        Log.i(TAG, "onDrawFrame-------")
        surfaceTexture?.updateTexImage()
        gl?.glClearColor(0f, 0f, 0f, 0f)
        gl?.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        surfaceTexture?.getTransformMatrix(mtx)
        cameraFilter.onDraw(glSurface.width, glSurface.height, mtx, textures[0])

        //渲染Bitmap-----------------------
//        val textureId = imageFilter.init(BitmapFactory.decodeResource(context.resources, R.drawable.dog))
//        imageFilter.drawFrame(textureId)
        //多层滤镜----------------------------------------------

    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
//        Log.i(TAG, "onFrameAvailable-------")
        glSurface.requestRender()
    }

    //2.自定义SurfaceProvider
    override fun onSurfaceRequested(request: SurfaceRequest) {
        Log.i(TAG, "request------width=${request.resolution.width}-----height=${request.resolution.height}")
        surfaceTexture?.apply {
            setOnFrameAvailableListener(this@CameraRender)
            setDefaultBufferSize(request.resolution.width, request.resolution.height)
            request.provideSurface(Surface(surfaceTexture), executor) {
                Log.i(TAG, "SurfaceRequest.Result------${it.resultCode}")
                it.surface.release()
                surfaceTexture?.release()

            }
        } ?: Log.i(TAG, "surfaceTexture is null--------")
    }

    companion object {
        const val TAG = "CameraRender"
    }
}