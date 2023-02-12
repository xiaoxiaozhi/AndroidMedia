package com.mymedia.record

import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodec.Callback
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import java.io.File
import kotlin.concurrent.thread

class H264Encode(
    private val mediaProjection: MediaProjection,
    private val outFile: File,
    private val outTxt: File
) :
    Runnable {
    private val mediaFormat by lazy {
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 720, 1280)//宽高可以跟视频源不一致吗？？？
            .apply {
                setInteger(MediaFormat.KEY_FRAME_RATE, 24)//帧率，该例是1S中24帧
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 30)//I帧间隔， 30帧一个I帧
                setInteger(MediaFormat.KEY_BIT_RATE, 720 * 1280)// 码率，编码是一个压缩的过程，码率越高越清晰。
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )//色彩空间，这个设置是什么意思？？？
            }
    }
    private val mediaCodec by lazy { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC) }


    override fun run() {
        mediaCodec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {

            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                mediaCodec.getOutputBuffer(index)?.run {
                    val array = if (hasArray()) {
                        println("hasArray-----")
                        array()
                    } else {
                        //这里返回的ByteBuffer处于读模式
                        val byteArray = ByteArray(limit())
                        get(byteArray)
                        byteArray
                    }
                    println(
                        "array---------${
                            array.toList().take(5).map { it.toString(16) }
                        }"
                    )//kotlin扩展函数进制转换
                    outFile.appendBytes(array)//TODO 本来是想用 channel的但是还不会channel续写模式
//                    outTxt.appendText(array.map { it.toString(16) }.toList().toString())
                    FileUtils.writeContent(array, outTxt.absolutePath)
                    mediaCodec.releaseOutputBuffer(index, false)
                } ?: println("mediaCodec.getOutputBuffer is null--------------------")
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {

            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {

            }

        })

        //3. 配置编码器
        //解码的时候不需要输入 宽高因为这些信息都在视频里保存着。编码的时候需要输入宽高
        mediaCodec.configure(
            mediaFormat,
            null,//该例是编码，不需要显示这里不填
            null,//加密视频才需要填
            MediaCodec.CONFIGURE_FLAG_ENCODE//行为 该例是编码
        )
        //4.
        val surface = mediaCodec.createInputSurface()//
        mediaProjection.createVirtualDisplay(
            "AndroidMedia", 720, 1280,
            2,// 什么意思？？？
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,//什么意思
            surface,
            null,// 录屏暂停、恢复、停止 回调
            null
        ) ?: println("createVirtualDisplay 创建失败，mediaProjection为空")
        mediaCodec.start()
        println("run------------")
    }

    fun encode() {
        Thread(this).start()
    }
}