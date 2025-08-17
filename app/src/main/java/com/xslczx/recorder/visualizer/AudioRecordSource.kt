package com.xslczx.recorder.visualizer

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioRecordSource(
    private val sampleRate: Int = 44100,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val frameSize: Int = 1024,
) {
    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope): Channel<ShortArray> {
        val out = Channel<ShortArray>(Channel.BUFFERED)
        scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                (minBuf * 2).coerceAtLeast(frameSize * 4)
            )
            val buf = ShortArray(frameSize)
            try {
                record.startRecording()
                while (isActive) {
                    val n = record.read(buf, 0, buf.size)
                    if (n > 0) out.trySend(buf.copyOf(n))
                }
            } finally {
                try {
                    record.stop()
                } catch (_: Throwable) {
                }
                record.release()
                out.close()
            }
        }
        return out
    }
}