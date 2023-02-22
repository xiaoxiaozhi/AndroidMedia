package com.mymedia.decode

import android.content.res.Resources
import android.media.MediaCodec
import android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM
import android.media.MediaFormat
import android.view.Surface
import java.io.File
import java.io.InputStream
import java.nio.channels.FileChannel

/**
 * 同步 先 mediaCodec.start()       后synchronousDecodeH264()
 * 异步 先 asynchronousDecodeH264() 后mediaCodec.start()
 * A resource failed to call close. 报错可能跟mediacodec的状态没有重置有关系
 */
class H264Player1(private val path: File, private val surface: Surface) : Runnable {

    //MediaCodec.createByCodecName() 这个创建方式是干嘛的？？？
    private val mediaCodec: MediaCodec by lazy { MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC) }//如果设备不支持，这里会报错，TODO 用模拟器试试会不会报错
    private val mediaFormat: MediaFormat by lazy {
        //解码宽高好像随便写，因为宽高信息都保存在h264中
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 368, 368).apply {
            setInteger(MediaFormat.KEY_FRAME_RATE, 15)// 管用吗？？？
        }
    }
    private val resourceByteArray by lazy {
        println("初始化----resourceByteArray")
        val byteBuffer = path.inputStream().channel.run {
            map(FileChannel.MapMode.READ_ONLY, 0, size())
        }
        val byteArray = ByteArray(byteBuffer.capacity())
        byteBuffer.get(byteArray, 0, byteBuffer.capacity())
        byteArray
    }

    init {
        mediaCodec.configure(mediaFormat, surface, null, 0)//解码0 编码1
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
        var startIndex = 0
        mediaCodec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                val inputBuffer = codec.getInputBuffer(index);
                val nextIndex =
                    findByFrame(resourceByteArray, startIndex + 2, resourceByteArray.size)
                if (nextIndex == -1) {
                    return
                }
                //[最后一帧把BUFFER_FLAG_END_OF_STREAM标志传递出去](https://developer.android.google.cn/reference/android/media/MediaCodec?hl=en#end-of-stream-handling)
//                if (nextIndex == -1) {
//                    //-1 说明视频流里面的帧都已经解码完毕，这里要传输一个空的缓冲区并加上流末尾标志位BUFFER_FLAG_END_OF_STREAM
//                    codec.queueInputBuffer(
//                        index, 0, 0,
//                        0,//这通常是该缓冲区应该显示(呈现)的媒体时间,微秒为单位
//                        BUFFER_FLAG_END_OF_STREAM// 接收端收到后利用位运算得到次标志
//                    )
//                } else {
                val length = nextIndex - startIndex
                println("nextIndex----($nextIndex) startIndex------($startIndex) length-----($length)")
                inputBuffer?.put(resourceByteArray, startIndex, length)
                codec.queueInputBuffer(index, 0, length, 0, 0);
//                }


                startIndex = nextIndex
                MediaCodec.CONFIGURE_FLAG_ENCODE
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                //flag 利用位掩码保存信息，位运算与得到标志位 [参照](https://blog.csdn.net/zhangzeyuaaa/article/details/125081146)
                println("接收到的flag-----${(info.flags and BUFFER_FLAG_END_OF_STREAM) == BUFFER_FLAG_END_OF_STREAM}")
                codec.releaseOutputBuffer(index, true)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            }

            //TODO 在编解码之前 mediaCodec已经调用config配置过 MediaFormat。为什么输出格式会改变？？？
            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            }

        })
    }

    private fun synchronousDecodeH264() {
        val info = MediaCodec.BufferInfo()
        var startIndex = 0
        while (true) {
            //timeoutUs 小于0 无限期等待直到返回，等于0立马返回结果，大于0 超时时间,该例的超时时间设置为10ms
            val index =
                mediaCodec.dequeueInputBuffer(1000 * 10)//返回 -1 没有可用缓冲区，大于0  可以用的InputBuffer索引
            if (index > 0) {
                val buffer = mediaCodec.getInputBuffer(index)
                //每次向ByteBuffer传入1帧的数据
                val nextIndex =
                    findByFrame(resourceByteArray, startIndex + 2, resourceByteArray.size)
                val length = nextIndex - startIndex
                println("nextIndex----($nextIndex) startIndex------($startIndex) length-----($length)")
                buffer?.put(resourceByteArray, startIndex, length)
                mediaCodec.queueInputBuffer(index, 0, length, 0, 0)
                startIndex = nextIndex
            }
            val outIndex = mediaCodec.dequeueOutputBuffer(info, 10000)
            println("outIndex------$outIndex")
            if (outIndex >= 0) {
                try {
                    Thread.sleep(33)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                mediaCodec.releaseOutputBuffer(outIndex, true)
            }
        }
    }

    private fun findByFrame(bytes: ByteArray, start: Int, totalSize: Int): Int {
        for (i in start..totalSize - 4) {
            if (bytes[i].toInt() == 0x00 && bytes[i + 1].toInt() == 0x00 && bytes[i + 2].toInt() == 0x00 && bytes[i + 3].toInt() == 0x01
                || bytes[i].toInt() == 0x00 && bytes[i + 1].toInt() == 0x00 && bytes[i + 2].toInt() == 0x01
            ) {
                return i
            }
        }
        return -1
    }
}