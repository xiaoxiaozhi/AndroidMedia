package com.mymedia.projection.receive

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import androidx.lifecycle.lifecycleScope
import com.mymedia.R
import com.mymedia.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import javax.inject.Inject
import androidx.databinding.DataBindingUtil.setContentView
import com.mymedia.databinding.ActivityReceiveBinding
import com.mymedia.databinding.ActivityRecordBinding
import com.mymedia.decode.H264Player1
import okio.ByteString
import java.io.File

@AndroidEntryPoint
class ReceiveActivity : AppCompatActivity() {
    @Inject
    lateinit var okhttp: OkHttpClient
    lateinit var binding: ActivityReceiveBinding
    lateinit var decoder: H264Decoder
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = setContentView(this, R.layout.activity_receive)
        binding.surface.holder.addCallback(surfaceCallback)
        lifecycleScope.launch(Dispatchers.IO) {
            val request = Request.Builder().get().url("ws://192.168.3.109:1819").build();
            okhttp.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    println("WebSocket-----onOpen")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    super.onMessage(webSocket, text)
                    Log.i("ReceiveActivity", "onMessage---text=$text")
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    super.onMessage(webSocket, bytes)
                    decoder?.decodeH264(bytes.toByteArray())
                    Log.i("ReceiveActivity", "onMessage---ByteString---size=${bytes.toByteArray().size}")
                }

                override fun onFailure(
                    webSocket: WebSocket, t: Throwable,
                    response: Response?
                ) {
                    println("WebSocket-----onFailure")
                    println("Throwable-----${t.message}")
                }
            })
        }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.i("ReceiveActivity", "surfaceCreated")
            decoder = H264Decoder(holder.surface)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.i("ReceiveActivity", "surfaceChanged")
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.i("ReceiveActivity", "surfaceDestroyed")
        }
    }
}