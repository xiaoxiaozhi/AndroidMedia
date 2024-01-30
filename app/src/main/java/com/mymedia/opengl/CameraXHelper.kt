package com.mymedia.opengl

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class CameraXHelper {
    fun setUpCamera(context: Context, surfaceProvider: Preview.SurfaceProvider) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview: Preview = Preview.Builder().build()

//            val imageCapture = ImageCapture.Builder()
//                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
//                .setTargetResolution(Size(480, 640))
////                .setTargetRotation(this.display.rotation)
//                .build()

            preview.setSurfaceProvider(surfaceProvider)

            cameraProvider.unbindAll()

            val camera =
                cameraProvider.bindToLifecycle(
                    context as LifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )
            val cameraInfo = camera.cameraInfo
            val cameraControl = camera.cameraControl
        }, ContextCompat.getMainExecutor(context))
    }
}