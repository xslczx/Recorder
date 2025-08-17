package com.xslczx.recorder.visualizer

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class SpectrumProcessor(
    private val fftSize: Int = 1024,
    private val bars: Int = 48,
    private val sampleRate: Int = 44100,
    private val minFreq: Float = 20f,
    private val maxFreq: Float = sampleRate / 2f,
    private val smoothingAlpha: Float = 0.4f,
) {
    private val window = FloatArray(fftSize) { i ->
        (0.5f - 0.5f * cos(2.0 * Math.PI * i / (fftSize - 1)).toFloat())
    }
    private val fft = FftRadix2(fftSize)
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)
    private val last = FloatArray(bars) { -90f }

    fun computeFromPcmShort(frame: ShortArray): FloatArray {
        val n = fftSize
        var i = 0
        while (i < n) {
            val s = if (i < frame.size) frame[i].toFloat() / Short.MAX_VALUE else 0f
            re[i] = s * window[i]
            im[i] = 0f
            i++
        }
        fft.fft(re, im)
        val mags = FloatArray(n / 2)
        i = 0
        while (i < n / 2) {
            val rr = re[i];
            val ii = im[i]
            val mag = sqrt(rr * rr + ii * ii).coerceAtLeast(1e-6f)
            mags[i] = (20f * ln(mag) / ln(10f))
            i++
        }
        val down = downBinLog(mags, bars, sampleRate, n, minFreq, maxFreq)
        // Simple smoothing
        for (b in 0 until bars) {
            last[b] = smoothingAlpha * down[b] + (1f - smoothingAlpha) * last[b]
        }
        return last.copyOf()
    }

    companion object {
        fun downBinLog(
            magsDb: FloatArray,
            bars: Int,
            sampleRate: Int,
            nFft: Int,
            minFreq: Float,
            maxFreq: Float,
        ): FloatArray {
            val out = FloatArray(bars)
            val fMax = sampleRate / 2f
            val lo = max(1f, minFreq)
            val hi = min(fMax, maxFreq)
            val logLo = ln(lo)
            val logHi = ln(hi)
            val step = (logHi - logLo) / bars
            for (b in 0 until bars) {
                val a = exp(logLo + b * step)
                val c = exp(logLo + (b + 1) * step)
                val kStart = ((a / fMax) * (nFft / 2)).toInt().coerceAtLeast(1)
                val kEnd = ((c / fMax) * (nFft / 2)).toInt().coerceAtLeast(kStart + 1)
                var peak = -120f
                var k = kStart
                val end = min(kEnd, magsDb.size)
                while (k < end) {
                    if (magsDb[k] > peak) peak = magsDb[k]; k++
                }
                out[b] = peak
            }
            return out
        }
    }
}