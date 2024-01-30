package com.mymedia.clipaudio.patterns

/**
 *
 */
interface AudioOption {
    fun prepare()
    fun start()
    fun stop()
    fun release()
}