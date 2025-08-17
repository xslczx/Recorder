package com.xslczx.recorder.visualizer

import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ln

class MediaRecorderLevelSource(
    private val recorder: MediaRecorder,
    private val intervalMs: Long = 33L
) {
    fun start(scope: CoroutineScope): Channel<Float> {
        val out = Channel<Float>(Channel.BUFFERED)
        scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val amp = runCatching { recorder.maxAmplitude }.getOrDefault(0)
                    val db = amplitudeToDb(amp)
                    out.trySend(db)
                    kotlinx.coroutines.delay(intervalMs)
                }
            } finally { out.close() }
        }
        return out
    }

    private fun amplitudeToDb(ampl: Int): Float {
        val norm = ampl / 32767f
        val clamped = if (norm <= 0f) 1e-6f else norm
        return (20f * ln(clamped) / ln(10f)).coerceAtMost(0f) // 0 dBFS max
    }
}