package com.xslczx.recorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.io.File
import java.io.FileInputStream
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PcmWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { REALTIME, FILE }

    private var mode = Mode.REALTIME

    private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        style = Paint.Style.FILL
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 2f
    }

    private val backgroundPaint = Paint().apply {
        color = Color.WHITE
    }

    private val realTimeData = ArrayDeque<Int>()
    private var fileData: ByteArray = byteArrayOf()

    private var maxRealTimeSize = 2000
    private var progress: Float = 0f

    private var lumpWidth = 4
    private var lumpSpace = 2
    private var lumpMaxHeight = 200
    private var scale = lumpMaxHeight / 127f

    fun setMode(mode: Mode) {
        this.mode = mode
        invalidate()
    }

    fun addAmplitude(value: Int) {
        if (mode != Mode.REALTIME) return
        val clamped = min(127, abs(value))
        if (realTimeData.size >= maxRealTimeSize) {
            realTimeData.pollFirst()
        }
        realTimeData.addLast(clamped)
        invalidate()
    }

    fun setWaveDataFromFile(file: File) {
        if (!file.exists()) return
        FileInputStream(file).use { fis ->
            fileData = fis.readBytes()
        }
        mode = Mode.FILE
        invalidate()
    }

    fun addAmplitudesFromPcm(pcm: ByteArray, sampleSize: Int = 2) {
        if (mode != Mode.REALTIME) return
        val step = sampleSize
        var i = 0
        while (i + 1 < pcm.size) {
            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF))
            val absSample = abs(sample)
            val amplitude = (absSample shr 8).coerceAtMost(127)
            if (realTimeData.size >= maxRealTimeSize) {
                realTimeData.pollFirst()
            }
            realTimeData.addLast(amplitude)
            i += step
        }
        invalidate()
    }

    fun setProgress(progress: Float) {
        this.progress = progress.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val centerY = height / 2f
        val drawList = when (mode) {
            Mode.REALTIME -> realTimeData.toList()
            Mode.FILE -> {
                if (fileData.isEmpty()) emptyList()
                else {
                    val list = mutableListOf<Int>()
                    fileData.forEachIndexed { index, byte ->
                        val i = if (index % 2 == 0) null else abs(byte.toInt()) and 0xFF
                        if (i != null)
                            list.add(i)
                    }
                    list
                }
            }
        }

        val lumpTotal = drawList.size
        if (lumpTotal == 0) return

        val totalWidth = width
        val availableWidth = lumpTotal * (lumpWidth + lumpSpace)
        val startX = if (availableWidth < totalWidth) (totalWidth - availableWidth) / 2 else 0

        for (i in drawList.indices) {
            val value = drawList[i]
            if (value == null) continue
            val x = startX + i * (lumpWidth + lumpSpace)
            val halfHeight = max(2f, value * scale)
            canvas.drawRect(
                x.toFloat(),
                centerY - halfHeight,
                x + lumpWidth.toFloat(),
                centerY + halfHeight,
                waveformPaint
            )
        }

        // 绘制播放进度线
        if (mode == Mode.FILE && drawList.isNotEmpty()) {
            val progressX = (drawList.size * (lumpWidth + lumpSpace)) * progress + startX
            canvas.drawLine(progressX, 0f, progressX, height.toFloat(), progressPaint)
        }
    }
}
