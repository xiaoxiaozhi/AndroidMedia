package com.mymedia.opengl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class MyGLSurfaceView(private val context: Context, private val attrs: AttributeSet) : GLSurfaceView(context, attrs) {
    init {
        val renderer = CameraRender(context,this)
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        /**
         * 刷新方式：
         * RENDERMODE_WHEN_DIRTY 手动刷新，調用requestRender();
         * RENDERMODE_CONTINUOUSLY 自動刷新，大概16ms自動回調一次onDrawFrame方法
         */
        //注意必须在setRenderer 后面。
        renderMode = RENDERMODE_WHEN_DIRTY
    }

}