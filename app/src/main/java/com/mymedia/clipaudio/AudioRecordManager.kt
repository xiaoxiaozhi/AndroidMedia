package com.mymedia.clipaudio

import com.mymedia.clipaudio.patterns.AudioOption

class AudioRecordManager constructor(private val option: AudioOption) : AudioOption {
    override fun prepare() {
        option.prepare()
    }

    override fun start() {
        option.start()
    }

    override fun stop() {
        option.stop()
    }

    override fun release() {
        option.release()
    }

}