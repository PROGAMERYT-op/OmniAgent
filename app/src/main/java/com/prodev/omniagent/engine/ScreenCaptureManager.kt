package com.prodev.omniagent.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.prodev.omniagent.service.FloatingOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureManager"

        @Volatile
        private var projectionData: Intent? = null
        private var projectionResultCode: Int = Activity.RESULT_CANCELED

        fun setProjectionResult(resultCode: Int, data: Intent?) {
            projectionResultCode = resultCode
            projectionData = data
        }

        fun hasProjectionPermission(): Boolean {
            return projectionData != null && projectionResultCode == Activity.RESULT_OK
        }

        fun clearProjectionResult() {
            projectionData = null
            projectionResultCode = Activity.RESULT_CANCELED
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var scaledWidth: Int = 0
    private var scaledHeight: Int = 0

    @Synchronized
    private fun initSession(): Boolean {
        if (virtualDisplay != null && imageReader != null && mediaProjection != null) {
            return true
        }

        val data = projectionData ?: return false
        if (projectionResultCode != Activity.RESULT_OK) return false

        // Android 14+ requires an active MediaProjection foreground service
        if (!FloatingOverlayService.isServiceActive.value) {
            Log.d(TAG, "Screen capture skipped: FloatingOverlayService is not active. Using DOM hierarchy fallback.")
            return false
        }

        try {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                ?: return false

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            scaledWidth = metrics.widthPixels / 2
            scaledHeight = metrics.heightPixels / 2
            val density = metrics.densityDpi

            val reader = ImageReader.newInstance(scaledWidth, scaledHeight, PixelFormat.RGBA_8888, 2)
            val projection = try {
                projectionManager.getMediaProjection(projectionResultCode, data.clone() as Intent)
            } catch (se: SecurityException) {
                Log.w(TAG, "MediaProjection token expired or rejected: ${se.message}")
                clearProjectionResult()
                try { reader.close() } catch (_: Throwable) {}
                return false
            } catch (e: Exception) {
                Log.w(TAG, "Failed to obtain MediaProjection: ${e.message}")
                try { reader.close() } catch (_: Throwable) {}
                return false
            } ?: run {
                try { reader.close() } catch (_: Throwable) {}
                return false
            }

            val handler = Handler(Looper.getMainLooper())

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped by system")
                    stopSession()
                }
            }, handler)

            val display = try {
                projection.createVirtualDisplay(
                    "OmniAgentScreenCapture",
                    scaledWidth,
                    scaledHeight,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface,
                    null,
                    handler
                )
            } catch (se: SecurityException) {
                Log.w(TAG, "createVirtualDisplay rejected: ${se.message}")
                clearProjectionResult()
                try { reader.close() } catch (_: Throwable) {}
                try { projection.stop() } catch (_: Throwable) {}
                return false
            } catch (e: Exception) {
                Log.w(TAG, "createVirtualDisplay failed: ${e.message}")
                try { reader.close() } catch (_: Throwable) {}
                try { projection.stop() } catch (_: Throwable) {}
                return false
            }

            this.mediaProjection = projection
            this.virtualDisplay = display
            this.imageReader = reader
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Screen capture session initialization failed: ${e.message}")
            stopSession()
            return false
        }
    }

    suspend fun captureScreen(): Bitmap? = withContext(Dispatchers.IO) {
        if (!initSession()) {
            return@withContext null
        }

        val reader = imageReader ?: return@withContext null

        try {
            // Allow a short frame render stabilization
            kotlinx.coroutines.delay(100)

            var image = reader.acquireLatestImage()
            if (image == null) {
                kotlinx.coroutines.delay(100)
                image = reader.acquireLatestImage()
            }

            var capturedBitmap: Bitmap? = null
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * scaledWidth

                val bitmap = Bitmap.createBitmap(
                    scaledWidth + rowPadding / pixelStride,
                    scaledHeight,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                // Crop out any row padding
                capturedBitmap = Bitmap.createBitmap(bitmap, 0, 0, scaledWidth, scaledHeight)
            }

            capturedBitmap
        } catch (e: Exception) {
            Log.w(TAG, "Screen frame acquisition error: ${e.message}")
            null
        }
    }

    @Synchronized
    fun stopSession() {
        try {
            virtualDisplay?.release()
        } catch (_: Throwable) {}
        virtualDisplay = null

        try {
            mediaProjection?.stop()
        } catch (_: Throwable) {}
        mediaProjection = null

        try {
            imageReader?.close()
        } catch (_: Throwable) {}
        imageReader = null
    }
}
