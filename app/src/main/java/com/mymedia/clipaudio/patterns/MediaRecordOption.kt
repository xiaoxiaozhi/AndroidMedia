package com.mymedia.clipaudio.patterns

import android.media.MediaRecorder
import android.util.Log
import com.mymedia.clipaudio.ClipAudioActivity
import java.io.File

class MediaRecordOption(private val outPath: String) : AudioOption {

    private val recorder: MediaRecorder by lazy {
        MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setOutputFile(outPath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(2) //设置录制的音频通道数
            // 设置录制的音频编码比特率 采样率*通道数*采样位数(2代表两字节) 采样位数没找到在哪设置，不过这个配置声音已经很清晰了
            setAudioEncodingBitRate(44100 * 2 * 2)
            setAudioSamplingRate(44100) //设置录制的音频采样率
        }
    }

    override fun prepare() {
        recorder.prepare()
    }

    override fun start() {
        recorder.start()
    }

    override fun stop() {
        recorder.stop()
    }

    override fun release() {
        recorder.release()
    }
}