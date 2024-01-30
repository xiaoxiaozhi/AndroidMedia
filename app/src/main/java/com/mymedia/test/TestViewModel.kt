package com.mymedia.test

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecCapabilities.*
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel

/**
 * 1.打印所有编解码器的信息(H264、H265等等)
 * 2.是否有支持H264的编解码器
 * 3.H264编解码器支持的颜色空间(即YUV存储方式)
 */
class TestViewModel : ViewModel() {
    private val TAG = "TestViewModel"

    init {
        mediaCodecInfo()
        //3. 支持H264的编解码器支持的颜色空间，也就是YUV存储方式
        selectCodec(MediaFormat.MIMETYPE_VIDEO_AVC)?.apply {
            getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats.forEach { it ->
                when (it) {
                    COLOR_FormatYUV420Flexible -> println("支持的YUV---- COLOR_FormatYUV420Flexible")
                    COLOR_FormatYUV420SemiPlanar -> println("支持的YUV---- COLOR_FormatYUV420SemiPlanar")
                    COLOR_QCOM_FormatYUV420SemiPlanar -> println("支持的YUV---- COLOR_QCOM_FormatYUV420SemiPlanar")
                    COLOR_FormatSurface -> println("支持的YUV---- COLOR_FormatSurface")
                    else -> println("支持的YUV---- 10进制=$it 16进制${it.toString(16)}")
                }

            }
        } ?: Log.i(TAG, "本机不支持MediaFormat.MIMETYPE_VIDEO_AVC类型")
    }

    /**
     * 1. 打印所有编解码器的基本信息
     */
    private fun mediaCodecInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                .filter { !it.isAlias }//过滤掉是别名的编码器
                .forEach {
                    println("编解码器名称-------${it.canonicalName}")
                    println("编解码器别名-------${it.name}")//设备实现可以为相同的底层编解码器提供多个别名
                    println("编解码器支持的媒体类型-----${it.supportedTypes.toList()}")
                    println("是否是别名---------${it.isAlias}")
                    println("是否是编码器---------${it.isEncoder}")
                    println("是否支持硬件加速-----_${it.isHardwareAccelerated}")//硬件制造商提供，不能保证与真实情况相符
                    println("编解码器是否是软件实现--${it.isSoftwareOnly}")//软编解码器更安全但不保证性能
                    println("编解码器Android提供还是设备制造商----${if (it.isVendor) "device manufacturer" else "Android"}")
                    println("----------------------------------------------")
                }
        } else {
            Log.w(TAG, "mediaCodecInfo-----只在android 29以上版本运行")
        }

    }

    /**
     * 2. 得到MediaCodecInfo.canonicalName作为参数创建 MediaCodec.createByCodecName(name) 感觉这种方式繁琐，不如直接利用MIME值创建
     * MIME在MediaFormat这个类中
     */
    private fun selectCodec(mimeType: String): MediaCodecInfo? {
        return MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filter { it.isEncoder }
            .filter { it.supportedTypes.contains(mimeType) }
            .takeIf {
                it.isNotEmpty()
            }?.first()
    }
}