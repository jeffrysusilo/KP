package com.example.kp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerInference(
    var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    var currentModel: Int = MODEL_POSE_LANDMARKER_FULL,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    val PoseLandmarkerInferenceListener: LandmarkerListener? = null
) {
    private var poseLandmarker: PoseLandmarker? = null
    init {
        setupPoseLandmarker()
    }
    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    fun isClose(): Boolean {
        return poseLandmarker == null
    }

    fun setupPoseLandmarker() {

        val baseOptionBuilder = BaseOptions.builder()

        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }
        }

        val modelName = "pose_landmarker_full.task"

        baseOptionBuilder.setModelAssetPath(modelName)

        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (PoseLandmarkerInferenceListener == null) {
                    throw IllegalStateException(
                        "PoseLandmarkerInferenceListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }

            else -> {}
        }

        try {
            val baseOptions = baseOptionBuilder.build()
            val optionsBuilder =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                    .setMinTrackingConfidence(minPoseTrackingConfidence)
                    .setMinPosePresenceConfidence(minPosePresenceConfidence)
                    .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            poseLandmarker =
                PoseLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            PoseLandmarkerInferenceListener?.onError(
                "error"
            )
            Log.e(
                TAG, "error saat load model " + e
                    .message
            )
        } catch (e: RuntimeException) {
            PoseLandmarkerInferenceListener?.onError(
                "error", GPU_ERROR
            )
            Log.e(
                TAG,
                "error modelnya " + e.message
            )
        }
    }

    // Convert the ImageProxy to MP Image and feed it to PoselandmakerHelper.
    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "live stream tidak jalan"
            )
        }
        val frameTime = SystemClock.uptimeMillis()

        val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )

        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        detectAsync(mpImage, frameTime)
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }
    private fun returnLivestreamResult(
        result: PoseLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        PoseLandmarkerInferenceListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        PoseLandmarkerInferenceListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    companion object {
        const val TAG = "PoseLandmarkerInference"

        const val DELEGATE_CPU = 0
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.5F
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val MODEL_POSE_LANDMARKER_FULL = 0
    }

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}