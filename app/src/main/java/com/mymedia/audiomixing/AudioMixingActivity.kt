package com.mymedia.audiomixing

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import com.mymedia.AndroidObject
import com.mymedia.R
import com.mymedia.databinding.ActivityAudioMixingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files

/**
 * 来自。两个音频PCM合成实现混音和视频剪辑原理
 */
class AudioMixingActivity : AppCompatActivity() {
    var musicVolume = 0
    var voiceVolume = 0
    private lateinit var binding: ActivityAudioMixingBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = setContentView(this, R.layout.activity_audio_mixing)
        binding.musicSeekBar.max = 100
        binding.voiceSeekBar.max = 100
        binding.musicSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                musicVolume = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        binding.voiceSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                voiceVolume = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        binding.mixAudio.setOnClickListener {
            AndroidObject.executorService.execute {
                val videoFile = File(filesDir, "input.mp4")
                val audioFile = File(filesDir, "music.mp3")
                //剪辑好的视频输出放哪里
                val outputFile = File(filesDir, "output.mp4")
                MusicProcess.mixAudioTrack(
                    this@AudioMixingActivity,
                    videoFile.absolutePath,
                    audioFile.absolutePath,
                    outputFile.absolutePath,
                    (100 * 1000 * 1000),
                    (120 * 1000 * 1000) as Int,
                    voiceVolume,
                    musicVolume
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            File(filesDir, "music.mp3").apply {
                if (!exists()) {
                    createNewFile()
                }
                copyAssets("music.mp3", this.absolutePath)
            }

            File(filesDir, "input.mp4").also { input ->
                if (!input.exists()) {
                    input.createNewFile()
                }
                copyAssets("input.mp4", input.absolutePath)
                withContext(Dispatchers.Main) {
                    startPlay(input.absolutePath)
                }
            }
        }

    }

    private var duration = 0
    private fun startPlay(path: String) {
        val layoutParams: ViewGroup.LayoutParams = binding.videoView.layoutParams
        layoutParams.height = 675
        layoutParams.width = 1285
        binding.videoView.layoutParams = layoutParams
        binding.videoView.setVideoPath(path)
        binding.videoView.start()
        binding.videoView.setOnPreparedListener { mp ->
            duration = mp.duration / 1000
//            mp.isLooping = true
        }
    }

    @Throws(IOException::class)
    private fun copyAssets(assetsName: String, path: String) {
        val assetFileDescriptor = assets.openFd(assetsName)
        val from = FileInputStream(assetFileDescriptor.fileDescriptor).channel
        val to = FileOutputStream(path).channel
        from.transferTo(assetFileDescriptor.startOffset, assetFileDescriptor.length, to)
    }


}