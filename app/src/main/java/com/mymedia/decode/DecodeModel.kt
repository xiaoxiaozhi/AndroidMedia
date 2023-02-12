package com.mymedia.decode

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.flow

class DecodeModel : ViewModel() {
    val flow = flow<ByteArray> {

    }
}