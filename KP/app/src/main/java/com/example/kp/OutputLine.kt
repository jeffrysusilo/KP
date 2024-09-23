package com.example.kp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class OutputLine(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var outputPose: PoseLandmarkerResult? = null
    private var poinLandmark = Paint()
    private var garisLandmark = Paint()

    private var scale: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        beriWarna()
    }

    private fun beriWarna() {
        garisLandmark.color =
            ContextCompat.getColor(context!!, R.color.yellow)
        garisLandmark.strokeWidth = LANDMARK_STROKE_WIDTH
        garisLandmark.style = Paint.Style.STROKE

        poinLandmark.color = Color.YELLOW
        poinLandmark.strokeWidth = LANDMARK_STROKE_WIDTH
        poinLandmark.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        outputPose?.let { poseLandmarkerResult ->
            for(landmark in poseLandmarkerResult.landmarks()) {
                for(normalizedLandmark in landmark) {
                    canvas.drawPoint(
                        normalizedLandmark.x() * imageWidth * scale,
                        normalizedLandmark.y() * imageHeight * scale,
                        poinLandmark
                    )
                }

                PoseLandmarker.POSE_LANDMARKS.forEach {
                    canvas.drawLine(
                        poseLandmarkerResult.landmarks().get(0).get(it!!.start()).x() * imageWidth * scale,
                        poseLandmarkerResult.landmarks().get(0).get(it.start()).y() * imageHeight * scale,
                        poseLandmarkerResult.landmarks().get(0).get(it.end()).x() * imageWidth * scale,
                        poseLandmarkerResult.landmarks().get(0).get(it.end()).y() * imageHeight * scale,
                        garisLandmark)
                }
            }
        }
    }

    fun setOutput(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        outputPose = poseLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scale = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 12F
    }

//    fun clear() {
//        results = null
//        poinLandmark.reset()
//        garisLandmark.reset()
//        invalidate()
//        beriWarna()
//    }
    
}