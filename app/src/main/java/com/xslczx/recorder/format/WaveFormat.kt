package com.xslczx.recorder.format

import android.media.MediaFormat
import com.xslczx.recorder.recorder.RecordConfig
import com.xslczx.recorder.container.IContainerWriter
import com.xslczx.recorder.container.WaveContainer

class WaveFormat : Format() {
    override val mimeTypeAudio: String = MediaFormat.MIMETYPE_AUDIO_RAW
    override val passthrough: Boolean = true

    private var frameSize: Int = 0

    override fun getMediaFormat(config: RecordConfig): MediaFormat {
        val bitsPerSample = 16
        frameSize = config.numChannels * bitsPerSample / 8

        val format = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, mimeTypeAudio)
            setInteger(MediaFormat.KEY_SAMPLE_RATE, config.sampleRate)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, config.numChannels)
            setInteger(KEY_X_FRAME_SIZE_IN_BYTES, frameSize)
        }

        return format
    }

    override fun getContainer(path: String?): IContainerWriter {
        if (path == null) {
            throw IllegalArgumentException("Path not provided. Stream is not supported.")
        }

        return WaveContainer(path, frameSize)
    }
}