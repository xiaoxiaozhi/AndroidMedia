package com.mymedia.projection.receive

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface

/**
 * 同步 先 mediaCodec.start()       后synchronousDecodeH264()
 * 异步 先 asynchronousDecodeH264() 后mediaCodec.start()
 * A resource failed to call close. 报错可能跟mediacodec的状态没有重置有关系
 */
class H264Decoder(private val surface: Surface) {

    //MediaCodec.createByCodecName() 这个创建方式是干嘛的？？？
    private val mediaCodec: MediaCodec by lazy { MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC) }//如果设备不支持，这里会报错，TODO 用模拟器试试会不会报错
    private val mediaFormat: MediaFormat by lazy {
        //解码宽高好像随便写，因为宽高信息都保存在h264中
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 368, 368).apply {
            setInteger(MediaFormat.KEY_FRAME_RATE, 15)// 管用吗？？？
        }
    }

    init {
        mediaCodec.configure(mediaFormat, surface, null, 0)//解码0 编码1
        mediaCodec.start()
    }
//    mediaCodec.stop()
//    mediaCodec.release()


    public fun decodeH264(src: ByteArray) {
        val info = MediaCodec.BufferInfo()
        //timeoutUs 小于0 无限期等待直到返回，等于0立马返回结果，大于0 超时时间,该例的超时时间设置为10ms
        val index =
            mediaCodec.dequeueInputBuffer(1000 * 10)//返回 -1 没有可用缓冲区，大于0  可以用的InputBuffer索引
        if (index > 0) {
            val buffer = mediaCodec.getInputBuffer(index)
            buffer?.put(src, 0, src.size)
            mediaCodec.queueInputBuffer(index, 0, src.size, 0, 0)
        }
        var outIndex = mediaCodec.dequeueOutputBuffer(info, 10000)
        Log.i("H264Decoder", "解码后长度  : " + info.size)
        while (outIndex >= 0) {
            mediaCodec.releaseOutputBuffer(outIndex, true)
            outIndex = mediaCodec.dequeueOutputBuffer(info, 0)
        }
    }
}
