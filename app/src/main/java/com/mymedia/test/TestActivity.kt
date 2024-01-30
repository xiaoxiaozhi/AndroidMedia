package com.mymedia.test

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.databinding.DataBindingUtil.setContentView
import com.mymedia.R
import com.mymedia.databinding.ActivityTestBinding

/**
 *1.
 */
class TestActivity : AppCompatActivity() {
    lateinit var binding: ActivityTestBinding
    private val viewModel: TestViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = setContentView(this, R.layout.activity_test)
        viewModel.toString()
    }
}