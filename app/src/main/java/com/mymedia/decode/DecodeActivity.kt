package com.mymedia.decode

import android.content.res.Resources
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
        MediaCodecInfo.CodecCapabilities.COLOR_Format24bitBGR888


        //想用cameraX里面的Preview代替，发现不可以
        binding.surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                println("surfaceCreated-------------------")
                val inputStream = resources.openRawResource(R.raw.out)
                H264Player(inputStream, holder.surface).play()
//                H264Player1(File(filesDir, "out.h264"), holder.surface).play()
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
    }

}