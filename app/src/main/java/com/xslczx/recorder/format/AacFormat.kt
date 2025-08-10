package com.xslczx.recorder.format

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.xslczx.recorder.recorder.RecordConfig
import com.xslczx.recorder.container.AdtsContainer
import com.xslczx.recorder.container.IContainerWriter
import com.xslczx.recorder.container.MuxerContainer
import com.xslczx.recorder.encoder.AudioEncoder

class AacFormat : Format() {
    override val mimeTypeAudio: String = MediaFormat.MIMETYPE_AUDIO_AAC
    override val passthrough: Boolean = false

    private var sampleRate: Int = 44100
    private var numChannels: Int = 2
    private var aacProfile: Int = MediaCodecInfo.CodecProfileLevel.AACObjectLC

    override fun getMediaFormat(config: RecordConfig): MediaFormat {
        val format = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, mimeTypeAudio)
            setInteger(MediaFormat.KEY_SAMPLE_RATE, config.sampleRate)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, config.numChannels)
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitRate)

            // Specifics
            @Suppress("CascadeIf")
            if (config.audioEncoder == AudioEncoder.AacLc) {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
            } else if (config.audioEncoder == AudioEncoder.AacEld) {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectELD
                )
            } else if (config.audioEncoder == AudioEncoder.AacHe) {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectHE
                )
            }
        }

        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        numChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        aacProfile = format.getInteger(MediaFormat.KEY_AAC_PROFILE)

        return format
    }

    override fun adjustSampleRate(format: MediaFormat, sampleRate: Int) {
        super.adjustSampleRate(format, sampleRate)
        this.sampleRate = sampleRate
    }

    override fun adjustNumChannels(format: MediaFormat, numChannels: Int) {
        super.adjustNumChannels(format, numChannels)
        this.numChannels = numChannels
    }

    override fun getContainer(path: String?): IContainerWriter {
        if (path == null) {
            if (aacProfile != MediaCodecInfo.CodecProfileLevel.AACObjectLC) {
                throw IllegalArgumentException("Stream is not supported.")
            }

            return AdtsContainer(sampleRate, numChannels, aacProfile)
        }

        return MuxerContainer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }
}