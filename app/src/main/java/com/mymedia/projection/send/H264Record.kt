package com.mymedia.projection.send

import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodec.Callback
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.util.Log
import java.io.File
import java.lang.Byte
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.experimental.and

class H264Record(
    private val mediaProjection: MediaProjection,
    private val socketManager: SocketManager
) :
    Runnable {
    private val TAG = this.javaClass.simpleName
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
            lateinit var sps_pps_buffer: ByteArray
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {

            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                mediaCodec.getOutputBuffer(index)?.run {
                    if (get(2) == Byte.valueOf(0x01)) {
                        get(3)
                    } else {
                        get(4)
                    }.also { nal ->
                        Log.i(TAG, "nal----${nal.toString(16)}")
                        if ((nal.toInt() shr 7) == 1) {
                            mediaCodec.releaseOutputBuffer(index, false)
                            return@run
                        }
                        Log.i("H264Record", "优先级----${(nal.toInt() shr 5) and 0x06}")
                        val type = nal.toInt() and 0x1f
                        Log.i(TAG, "frame type ${type}")
                        if (type == 7) {
                            sps_pps_buffer = ByteArray(limit())
                            get(sps_pps_buffer)
                        } else if (type == 5) {
                            val bb = ByteBuffer.allocate(limit() + sps_pps_buffer.size)
                            val iFrame = ByteArray(limit()).also { i_ByteArray ->
                                get(i_ByteArray)
                            }
                            bb.put(sps_pps_buffer)
                            bb.put(iFrame)
                            bb.flip()
                            socketManager.sendFrame(bb.array())
                        } else {
                            ByteArray(limit()).also { frameArray ->
                                get(frameArray)
                                socketManager.sendFrame(frameArray)
                            }
                        }
                    }
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