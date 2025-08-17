package com.xslczx.recorder.visualizer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ScrollingSpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var backBitmap: Bitmap? = null
    private var backCanvas: Canvas? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var minDb = -90f
    private var maxDb = 0f

    /** Push one spectrum frame (dB per bar). Call on main thread. */
    fun push(specDb: FloatArray) {
        if (width == 0 || height == 0) return
        ensureBitmap()
        val bmp = backBitmap!!
        val c = backCanvas!!
        // Shift left by 1px
        val src = Rect(1, 0, bmp.width, bmp.height)
        val dst = Rect(0, 0, bmp.width - 1, bmp.height)
        c.drawBitmap(bmp, src, dst, null)
        // Clear rightmost column
        paint.color = Color.TRANSPARENT
        c.drawRect(Rect(bmp.width - 1, 0, bmp.width, bmp.height), Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
        // Draw new column at right
        val h = height.toFloat()
        val bars = specDb.size
        val bandH = h / bars
        for (i in 0 until bars) {
            val v = ((specDb[i] - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
            val barLen = v * bandH
            val y0 = h - (i + 1) * bandH
            val y1 = y0 + barLen
            paint.color = Color.WHITE
            c.drawLine((width - 1).toFloat(), y1, (width - 1).toFloat(), y0, paint)
        }
        invalidate()
    }

    private fun ensureBitmap() {
        if (backBitmap == null || backBitmap!!.width != width || backBitmap!!.height != height) {
            backBitmap?.recycle()
            backBitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            backCanvas = Canvas(backBitmap!!)
            backCanvas!!.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }
    }

    override fun onDraw(canvas: Canvas) {
        backBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }
}