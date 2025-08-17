package com.xslczx.recorder.visualizer

import android.media.audiofx.Visualizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VisualizerSource(
    private val audioSessionId: Int,
    private val withWaveform: Boolean = true,
    private val withFft: Boolean = true,
) {

    fun start(scope: CoroutineScope): Channel<Frame> {
        val out = Channel<Frame>(Channel.BUFFERED)
        scope.launch(Dispatchers.IO) {
            var vis: Visualizer? = null
            try {
                vis = Visualizer(audioSessionId)
                val sizeRange = Visualizer.getCaptureSizeRange()
                vis.captureSize = sizeRange[1]
                vis.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?, data: ByteArray?, rate: Int
                    ) {
                        if (withWaveform && data != null) {
                            out.trySend(Frame(waveform = data.copyOf(), captureRate = rate))
                        }
                    }
                    override fun onFftDataCapture(
                        visualizer: Visualizer?, data: ByteArray?, rate: Int
                    ) {
                        if (withFft && data != null) {
                            out.trySend(Frame(fft = data.copyOf(), captureRate = rate))
                        }
                    }
                }, Visualizer.getMaxCaptureRate(), withWaveform, withFft)
                vis.enabled = true
                // Keep alive until cancelled
                while (scope.isActive) kotlinx.coroutines.delay(250)
            } catch (_: Throwable) {
            } finally {
                try { vis?.enabled = false } catch (_: Throwable) {}
                vis?.release()
                out.close()
            }
        }
        return out
    }
}