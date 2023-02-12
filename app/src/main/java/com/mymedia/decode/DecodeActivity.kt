package com.mymedia.decode

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil.setContentView
import com.mymedia.R
import com.mymedia.databinding.ActivityMainBinding
import java.io.File
import java.nio.channels.FileChannel


/**
 * 解码一段H264目的是熟悉 Mediacodec
 * cpu解码是软解，dsp芯片解码属于硬解码
 * 视频保存在 内部存储空间 files下面。另外在app/one.h264 另保存一份
 * 1.创建显示控件SurfaceView
 *   创建SurfaceView，在回调surfaceCreated 中，开启播放
 * 2.配置Mediacodec
 *   MediaFormat设置参数帧率、码率、I帧间隔等等。创建MediaFormat MediaFormat.createVideoFormat(videoType, width, height);
 *   MediaFormat.MIMETYPE_VIDEO_AVC（H.264）  MediaFormat.MIMETYPE_VIDEO_HEVC（H.265）
 *   最后调用Mediacodec.config(mediaFormat,surface....) 。查看代码 H264Player
 * 3.解码
 *   文件转成byte[]送入Mediacodec的getInputBuffer队列
 *
 * MediaCodecList:允许您枚举可用的编解码器(返回的编码器信息保存在MediaCodecInfo 对象) ，查找给定格式的编解码器并查询给定编解码器的功能。
 *                 MediaCodecList(int) 构造参数有两种
 *                 REGULAR_CODECS(0):只枚举适合于常规(缓冲区到缓冲区)解码或编码的编码解码器。注意: 这些是在 API 21之前返回的编解码器，使用现在不推荐使用的静态方法。(不推荐这个吗？？？)
 *                 ALL_CODECS(1)：枚举所有编码解码器，即使是不适合常规(缓冲区到缓冲区)解码或编码的编码器。这些包括编解码器，例如，只能与特殊的输入或输出表面一起工作的编解码器
 * MediaCodecInfo:提供有关设备上可用的给定媒体编解码器的信息。可以通过查询 MediaCodecList 迭代所有可用的编解码器。例如，如何找到支持给定 MIME 类型的编码器: TODO 内部类是什么用法？？？
 * MediaFormat:描述媒体数据格式信息的对象，无论是音频还是视频。媒体数据的格式被指定为 键值对
 */
class DecodeActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = setContentView(this, R.layout.activity_main)

        //想用cameraX里面的Preview代替，发现不可以
        binding.surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                println("surfaceCreated-------------------")
                H264Player(File(filesDir, "out.h264"), holder.surface).play()
//                H264Player1(File(filesDir, "out.h264").absolutePath, holder.surface).play()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {

            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {

            }

        })

//        mediaCodecInfo()
    }

    /**
     * 打印所有编解码器的基本信息
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaCodecInfo() {
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
    }

    /**
     * 得到MediaCodecInfo.canonicalName作为参数创建 MediaCodec.createByCodecName(name) 感觉这种方式繁琐，不如直接利用MIME值创建
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