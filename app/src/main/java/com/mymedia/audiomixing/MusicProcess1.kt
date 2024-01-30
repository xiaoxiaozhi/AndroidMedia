package com.mymedia.audiomixing

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

object MusicProcess1 {
    var TAG = "MusicProcess"

    /**
     * @param videoInput
     * @param audioInput
     * @param output
     * @param startTimeUs 剪辑开始时间 单位微秒  1秒乘以10的6次方
     * @param endTimeUs   剪辑结束时间
     * @param videoVolume 视频音量大小0-100
     * @param aacVolume   音频音量大小0-100
     * @throws Exception
     */
    @Throws(Exception::class)
    fun mixAudioTrack(
        context: Context, videoInput: String?,  //
        audioInput: String?,
        output: String?,
        startTimeUs: Int,  //剪辑开始时间
        endTimeUs: Int,  //剪辑结束时间
        videoVolume: Int,  //视频音量大小0-100
        aacVolume: Int //音频音量大小0-100
    ) {

//mp3  混音     压缩  数据    pcm
//还没生成
        val videoPcmFile = File(context.filesDir, "video" + ".pcm")
        //
        decodeToPCM(videoInput, videoPcmFile.absolutePath, startTimeUs, endTimeUs)

//        下载下来的音乐转换城pcm
        val aacPcmFile = File(context.filesDir, "audio" + ".pcm")
        decodeToPCM(
            audioInput,
            aacPcmFile.absolutePath, startTimeUs, endTimeUs
        )


//        混音
        val adjustedPcm = File(context.filesDir, "混合后的" + ".pcm")
        mixPcm(
            videoPcmFile.absolutePath, aacPcmFile.absolutePath,
            adjustedPcm.absolutePath, videoVolume, aacVolume
        )
        val wavFile = File(
            context.filesDir, adjustedPcm.name
                    + ".wav"
        )
        PcmToWavUtil(
            44100, AudioFormat.CHANNEL_IN_STEREO,
            2, AudioFormat.ENCODING_PCM_16BIT
        ).pcmToWav(
            adjustedPcm.absolutePath, wavFile.absolutePath
        )
    }

    @Throws(IOException::class)
    fun mixPcm(
        pcm1Path: String?, pcm2Path: String?, toPath: String?, vol1: Int, vol2: Int
    ) {
        val volume1 = vol1 / 100f * 1
        val volume2 = vol2 / 100f * 1
        //待混音的两条数据流 还原   傅里叶  复杂
        val is1 = FileInputStream(pcm1Path)
        val is2 = FileInputStream(pcm2Path)
        var end1 = false
        var end2 = false
        //        输出的数据流
        val fileOutputStream = FileOutputStream(toPath)
        val buffer1 = ByteArray(2048)
        val buffer2 = ByteArray(2048)
        val buffer3 = ByteArray(2048)
        var temp2: Short
        var temp1: Short
        while (!end1 || !end2) {
            if (!end2) {
                end2 = is2.read(buffer2) == -1
            }
            if (!end1) {
                end1 = is1.read(buffer1) == -1
            }
            var voice = 0
            //2个字节
            var i = 0
            while (i < buffer2.size) {

//前 低字节  1  后面低字节 2  声量值
//                32767         -32768
                temp1 = (buffer1[i].toInt() and 0xff or (buffer1[i + 1].toInt() and 0xff shl 8)).toShort()
                temp2 = (buffer2[i].toInt() and 0xff or (buffer2[i + 1].toInt() and 0xff shl 8)).toShort()
                voice = (temp1 * volume1 + temp2 * volume2).toInt()
                if (voice > 32767) {
                    voice = 32767
                } else if (voice < -32768) {
                    voice = -32768
                }
                //
                buffer3[i] = (voice and 0xFF).toByte()
                buffer3[i + 1] = (voice ushr 8 and 0xFF).toByte()
                i += 2
            }
            fileOutputStream.write(buffer3)
        }
        is1.close()
        is2.close()
        fileOutputStream.close()
    }

    @SuppressLint("WrongConstant")
    @Throws(Exception::class)
    fun decodeToPCM(
        musicPath: String?,
        outPath: String?, startTime: Int, endTime: Int
    ) {
        if (endTime < startTime) {
            return
        }
        val mediaExtractor = MediaExtractor()
        //        设值路径
        mediaExtractor.setDataSource(musicPath!!)
        //        音频索引
        val audioTrack = selectTrack(mediaExtractor)
        //       剪辑
        //选择轨道
        mediaExtractor.selectTrack(audioTrack)
        //        耗费内存 和    cpu
//        seek   UI优化     缓存优化  加载视频       200ms   一帧    缓存图片
//         会长多 肯定 剪影  500  800M
        //SEEK_TO_CLOSEST_SYNC  最近I帧
        //SEEK_TO_NEXT_SYNC 下一个I帧
        //SEEK_TO_PREVIOUS_SYNC 上一个I帧   音频会seek到对应I帧的那一个点，seek以视频为主。seek到一个时间点，实际上是从0开始解码到那个点比较好内存
        mediaExtractor.seekTo(startTime.toLong(), MediaExtractor.SEEK_TO_NEXT_SYNC)
        val audioFormat = mediaExtractor.getTrackFormat(audioTrack)
        val mediaCodec = MediaCodec.createDecoderByType(audioFormat.getString(MediaFormat.KEY_MIME)!!)
        mediaCodec.configure(audioFormat, null, null, 0)
        mediaCodec.start()
        var maxBufferSize = 100 * 1000
        maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        } else {
            100 * 1000
        }
        val pcmFile = File(outPath)
        val writeChannel = FileOutputStream(pcmFile).channel
        //        10M   造成内存浪费     10k   异常
        val buffer = ByteBuffer.allocateDirect(maxBufferSize)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val inIndex = mediaCodec.dequeueInputBuffer(1000)
            if (inIndex >= 0) {
//                获取到       视频容器  里面读取的当前时间戳
                val sampleTimeUs = mediaExtractor.sampleTime
                if (sampleTimeUs == -1L) {
                    break
                } else if (sampleTimeUs < startTime) {
//                    丢弃的意思
                    mediaExtractor.advance()
                } else if (sampleTimeUs > endTime) {
                    break
                }
                //                mediaExtractor
                info.size = mediaExtractor.readSampleData(buffer, 0)
                info.presentationTimeUs = sampleTimeUs
                info.flags = mediaExtractor.sampleFlags
                //                压缩1   原始数据 2
                val content = ByteArray(buffer.remaining())
                buffer[content]
                //                压缩1   未压缩 原始数据2
//                FileUtils.INSTANCE.writeContent(content,"");
                val inputBuffer = mediaCodec.getInputBuffer(inIndex)
                inputBuffer!!.put(content)
                mediaCodec.queueInputBuffer(inIndex, 0, info.size, info.presentationTimeUs, info.flags)
                //                释放上一帧的压缩数据
                mediaExtractor.advance()
            }
            var outIndex = -1
            outIndex = mediaCodec.dequeueOutputBuffer(info, 1000)
            if (outIndex >= 0) {
                val decodeOutputBuffer = mediaCodec.getOutputBuffer(outIndex)
                //数据 音频数据     压缩1   原始数据2
                writeChannel.write(decodeOutputBuffer)
                mediaCodec.releaseOutputBuffer(outIndex, false)
            }
        }
        writeChannel.close()
        mediaExtractor.release()
        mediaCodec.stop()
        mediaCodec.release()
    }

    //    寻找音频轨
    private fun selectTrack(extractor: MediaExtractor): Int {
        return (0..extractor.trackCount).filter {
            val format = extractor.getTrackFormat(it)
            val mime = format.getString(MediaFormat.KEY_MIME)
            Log.i(TAG, "MediaFormat---$format")
            mime!!.startsWith("audio")
        }.first()
    }
}