package com.mymedia.videocall

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.mymedia.AndroidObject
import com.mymedia.FileUtils
import java.io.File

/**
 * [AudioRecord vs MediaRecord](https://stackoverflow.com/questions/5886872/android-audiorecord-vs-mediarecorder-for-recording-audio)
 * 实时分析用AudioRecord，需要录音文件用MediaRecord
 * AudioRecord状态图 AudioRecord.webp
 *
 */
class AudioRecordLive(val context: Context) {
    private val isRecording = true

    private val miniBuffer =
        AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private val dataFrame = ByteArray(miniBuffer)

    @delegate:SuppressLint("MissingPermission")
    private val audioRecord: AudioRecord by lazy {
        AudioRecord(
            MediaRecorder.AudioSource.MIC,//音频来源，一般是麦克风
            44100,//采样频率
            AudioFormat.CHANNEL_IN_MONO,//声道数
            AudioFormat.ENCODING_PCM_16BIT,//采样位数
            miniBuffer
        )
    }

    init {
        //First check whether the above object actually initialized
        audioRecord.takeIf {
            it.state == AudioRecord.STATE_INITIALIZED
        }?.startRecording() ?: Log.e(TAG, "audioRecord state is STATE_UNINITIALIZED ")
    }

    fun recordAudio(block: (ByteArray) -> Unit) {
        AndroidObject.executorService.execute {
            while (isRecording) {
                audioRecord.read(dataFrame, 0, miniBuffer)
                //打印数据
                FileUtils.writeContent(dataFrame, File(context.filesDir, "audio.txt"))
                block(dataFrame)
            }
//            while () {
//                audioRecord.read()
//            }
        }
    }

    fun stopRecording() {
        if (audioRecord != null) {
//            isRecordingAudio = false
            audioRecord!!.stop()
            audioRecord!!.release()
//            audioRecord = null
//            recordingThread = null
// triggers recordingThread to exit while loop
        }
    }

    companion object {
        private const val TAG = "AudioRecordLive"
    }
}
