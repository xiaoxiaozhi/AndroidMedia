package com.mymedia.golomb

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceView

class FitSurfaceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    SurfaceView(context, attrs, defStyle) {
    private var aspectRatio = 0f

    /**
     * Sets the aspect ratio for this view. The size of the view will be
     * measured based on the ratio calculated from the parameters.
     *
     * @param width  Camera resolution horizontal size
     * @param height Camera resolution vertical size
     */
    fun setAspectRatio(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Size cannot be negative" }
        aspectRatio = width.toFloat() / height.toFloat()
        Log.i(TAG, "aspectRatio----$aspectRatio")
        holder.setFixedSize(width, height)//设置SurfaceView的分辨率，尝试一次缩小2倍，会发现越来越模糊
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    }

    companion object {
        private const val TAG = "FitSurfaceView"
    }
}