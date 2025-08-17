package com.xslczx.recorder.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class PlaybackWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paintWave = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }
    private val paintAxis =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; strokeWidth = 1f }
    private val paintProgress =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; strokeWidth = 2f }

    private var pyramid: List<Peaks> = emptyList()
    private var totalSamples: Long = 0
    private var sampleRate: Int = 44100

    // Viewport state
    private var samplesPerPx: Float = 200f // zoom: samples per pixel
    private var offsetSamples: Long = 0L   // leftmost sample index
    private var progressSamples: Long = 0L // playhead position

    private var minSamplesPerPx = 1f
    private var maxSamplesPerPx = 2000f

    private var onSeekListener: ((positionMs: Long) -> Unit)? = null

    fun setOnSeekListener(l: (Long) -> Unit) {
        onSeekListener = l
    }

    fun setData(pyramid: List<Peaks>) {
        this.pyramid = pyramid
        if (pyramid.isNotEmpty()) {
            totalSamples = pyramid[0].totalSamples
            sampleRate = pyramid[0].sampleRate
        } else {
            totalSamples = 0
        }
        // Fit all
        if (width > 0) samplesPerPx = (totalSamples.coerceAtLeast(1) / width.toFloat()).coerceIn(
            minSamplesPerPx,
            maxSamplesPerPx
        )
        offsetSamples = 0
        invalidate()
    }

    fun setProgressMs(ms: Long) {
        progressSamples = msToSamples(ms)
        invalidate()
    }

    private fun msToSamples(ms: Long) = (ms * sampleRate / 1000L)
    private fun samplesToMs(samples: Long) = (samples * 1000L / sampleRate)

    // Gesture support
    private val scaleDetector =
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val focusX = detector.focusX
                val scaleFactor = detector.scaleFactor
                val old = samplesPerPx
                samplesPerPx =
                    (samplesPerPx / scaleFactor).coerceIn(minSamplesPerPx, maxSamplesPerPx)
                // Keep focus point stable
                val centerSample = (offsetSamples + (focusX * old)).toLong()
                offsetSamples = (centerSample - (focusX * samplesPerPx)).toLong().coerceIn(
                    0L,
                    (totalSamples - (width * samplesPerPx)).toLong().coerceAtLeast(0L)
                )
                invalidate()
                return true
            }
        })

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                val deltaSamples = (distanceX * samplesPerPx).toLong()
                offsetSamples = (offsetSamples + deltaSamples).coerceIn(
                    0L,
                    (totalSamples - (width * samplesPerPx)).toLong().coerceAtLeast(0L)
                )
                invalidate()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // Seek to tapped position
                val tappedSamples =
                    (offsetSamples + e.x * samplesPerPx).toLong().coerceIn(0L, totalSamples)
                onSeekListener?.invoke(samplesToMs(tappedSamples))
                return true
            }
        })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val s = scaleDetector.onTouchEvent(event)
        val g = gestureDetector.onTouchEvent(event)
        return s or g or super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Axis center line
        val midY = height / 2f
        canvas.drawLine(0f, midY, width.toFloat(), midY, paintAxis)

        if (pyramid.isEmpty() || totalSamples <= 0) return

        // Select level by resolution
        val level = selectLevelFor(samplesPerPx)
        val peaks = pyramid[level]
        val bucketsOnScreen = max(1, (width))
        val bucketsPerPx = bucketsPerPx(peaks)

        // Which bucket index corresponds to offsetSamples?
        val samplesPerBucket = samplesPerBucket(peaks)
        val firstBucket = (offsetSamples / samplesPerBucket).toInt()
        val lastBucket =
            min(peaks.min.size - 1, firstBucket + (bucketsOnScreen * bucketsPerPx).toInt() + 2)
        val pxPerBucket = 1f / bucketsPerPx

        // Draw min/max as vertical line per pixel
        var b = firstBucket
        while (b <= lastBucket) {
            val x = ((b - firstBucket) * pxPerBucket).toFloat()
            val ix = floor(x).toInt()
            if (ix in 0 until width) {
                val vMin = peaks.min[b]
                val vMax = peaks.max[b]
                val y1 = valueToY(vMax)
                val y2 = valueToY(vMin)
                canvas.drawLine(ix.toFloat(), y1, ix.toFloat(), y2, paintWave)
            }
            b++
        }

        // Progress
        val progX = ((progressSamples - offsetSamples) / samplesPerPx).toFloat()
        if (progX in 0f..width.toFloat()) {
            canvas.drawLine(progX, 0f, progX, height.toFloat(), paintProgress)
        }
    }

    private fun valueToY(v: Int): Float {
        // Map [-32768, 32767] to [padding .. height - padding]
        val pad = height * 0.05f
        val n = v / 32768f
        return height / 2f - n * (height / 2f - pad)
    }

    private fun selectLevelFor(samplesPerPx: Float): Int {
        // Choose coarsest level whose bucket width <= samplesPerPx
        var level = 0
        while (level + 1 < pyramid.size) {
            val nextSamplesPerBucket = samplesPerBucket(pyramid[level + 1])
            if (nextSamplesPerBucket <= samplesPerPx) level++ else break
        }
        return level
    }

    private fun samplesPerBucket(p: Peaks): Float {
        val buckets = p.min.size
        val spb = (p.totalSamples.toFloat() / buckets)
        return spb
    }

    private fun bucketsPerPx(p: Peaks): Float {
        val spb = samplesPerBucket(p)
        return samplesPerPx / spb
    }
}