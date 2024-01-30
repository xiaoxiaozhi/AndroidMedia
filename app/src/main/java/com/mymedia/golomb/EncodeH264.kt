package com.mymedia.golomb

import android.content.res.Resources
import android.media.MediaCodec
import android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.io.File
import java.io.InputStream
import java.lang.Byte
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * 同步 先 mediaCodec.start()       后synchronousDecodeH264()
 * 异步 先 asynchronousDecodeH264() 后mediaCodec.start()
 * A resource failed to call close. 报错可能跟mediacodec的状态没有重置有关系
 */
//class H264Player(private val path: File, private val surface: Surface) : Runnable {
class EncodeH264() : Runnable {

    //MediaCodec.createByCodecName() 这个创建方式是干嘛的？？？
    private val mediaCodec: MediaCodec by lazy { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC) }//如果设备不支持，这里会报错，TODO 用模拟器试试会不会报错
    private val mediaFormat: MediaFormat by lazy {
        //解码宽高好像随便写，因为宽高信息都保存在h264中
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080).apply {
            setInteger(MediaFormat.KEY_FRAME_RATE, 24)//帧率，该例是1S中24帧
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 30)//I帧间隔， 30帧一个I帧
            setInteger(MediaFormat.KEY_BIT_RATE, 1920 * 1080)// 码率，编码是一个压缩的过程，码率越高越清晰。
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }
    }

    init {
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)//解码0 编码1
    }

    private val mInputSurface: Surface by lazy {
        mediaCodec.createInputSurface()
    }

    /**
     * Returns the encoder's input surface.
     */
    fun getInputSurface(): Surface {
        return mInputSurface
    }

    override fun run() {
        kotlin.runCatching {
            //--------同步---------------
//            synchronousDecodeH264()
            //--------异步----------------
            asynchronousDecodeH264()
            mediaCodec.start()
            //-----------------------------
        }.onFailure {
            it.printStackTrace()
            println("${it.message}")
            mediaCodec.stop()
            mediaCodec.release()
        }
    }

    fun play() {
        //-------同步-------
//        mediaCodec.start()
        //-----------------
        Thread(this).start()
    }

    private fun asynchronousDecodeH264() {
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
                        Log.i(TAG, "优先级----${(nal.toInt() shr 5) and 0x06}")
                        val type = nal.toInt() and 0x1f
                        Log.i(TAG, "frame type ${type}")
                        if (type == 7) {
                            sps_pps_buffer = ByteArray(limit())
                            get(sps_pps_buffer)
                            Log.i(
                                TAG, "sps_pps " + sps_pps_buffer.map {
                                    it.toUByte().toString(16)
                                }.toString()
                            )

                            codec.releaseOutputBuffer(index, false)
                            mediaCodec.stop()
                            mediaCodec.release()
                        } else if (type == 5) {
//                            val bb = ByteBuffer.allocate(limit() + sps_pps_buffer.size)
//                            val iFrame = ByteArray(limit()).also { i_ByteArray ->
//                                get(i_ByteArray)
//                            }
//                            bb.put(sps_pps_buffer)
//                            bb.put(iFrame)
//                            bb.flip()
//                            socketManager.sendFrame(bb.array())
                        } else {
//                            ByteArray(limit()).also { frameArray ->
//                                get(frameArray)
//                                socketManager.sendFrame(frameArray)
//                            }
                        }

                    }
                } ?: println("mediaCodec.getOutputBuffer is null--------------------")
                //flag 利用位掩码保存信息，位运算与得到标志位 [参照](https://blog.csdn.net/zhangzeyuaaa/article/details/125081146)
//                println("接收到的flag-----${(info.flags and BUFFER_FLAG_END_OF_STREAM) == BUFFER_FLAG_END_OF_STREAM}")
//                codec.releaseOutputBuffer(index, false)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            }

            //TODO 在编解码之前 mediaCodec已经调用config配置过 MediaFormat。为什么输出格式会改变？？？
            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            }

        })
    }

    companion object {
        private const val TAG = "EncodeH264"
    }
}