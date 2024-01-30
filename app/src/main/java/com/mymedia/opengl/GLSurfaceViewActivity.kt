package com.mymedia.opengl

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.databinding.DataBindingUtil.setContentView
import com.mymedia.R
import com.mymedia.databinding.ActivityGlsurfaceViewBinding
import com.mymedia.havePermissions

/**
 * [Android官方opengl ES章节](https://developer.android.com/develop/ui/views/graphics/opengl/about-opengl#choosing-version)
 * ![opengl 响应触摸事件，旋转三角形](https://developer.android.com/static/images/opengl/ogl-triangle-touch.png?hl=zh-cn)
 * ---------官方教程看不懂也没有例子-----------------------
 * [CameraX + OpenGL预览的全新版本s](https://juejin.cn/post/7035293015757307918#heading-7)从这篇文章了解到 怎么从正式版camerax获取surfaceTexture，涂老师的方法在正式版中被剔除
 * [Opengl es 入门文章](https://juejin.cn/user/1961184473926766/posts)
 * [轻松入门OpenGL系列,怀疑涂老师就是看的这个](https://juejin.cn/post/7033711226827833351)
 * [Android音视频录制（4）——变速录制](https://blog.csdn.net/daltsoftware/article/details/78855231)提了原理但是没有具体代码；这个系列文章提到用mediacodec录制视频分为buffer录制和surface录制，用数据接收方式区分
 * 1.GLSurfaceView：具体内容查看MyGLSurfaceView
 *  重点三件事 设置版本 setEGLContextClientVersion(2); 设置渲染器 setRenderer(renderer); 设置刷新方式setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
 *
 * 2.GLSurfaceView.Renderer：此类控制在与其关联的 GLSurfaceView 上绘制的内容，
 *  具体内容查看 CameraRender
 *
 * 3.Preview.SurfaceProvider:监听camerax的数据输出，创建一个surfaceTexture 传进去，然后给surfaceTexture设置一个回调onFrameAvailable，当
 *   CameraX的画面想要渲染到GlSurfaceView必须通过surfaceTexture，这个类存放着摄像头输出数据，本例通过重写 Preview.SurfaceProvider，获取surfaceTexture
 *
 * 4.绘制onDrawFrame
 *   绘制部分看CameraFilter
 *
 *
 *
 */
class GLSurfaceViewActivity : AppCompatActivity() {
    lateinit var dataBinding: ActivityGlsurfaceViewBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataBinding = setContentView(this, R.layout.activity_glsurface_view)
        havePermissions(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        ).takeIf {
            it.isNotEmpty()
        }?.also { permissionArray ->
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                for ((key, value) in it) println("key = $key , value = $value")
            }.launch(permissionArray)
        }
    }

    companion object {
        const val TAG = "GLSurfaceViewActivity"
    }
}