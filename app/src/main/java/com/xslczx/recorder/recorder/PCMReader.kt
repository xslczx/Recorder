package com.xslczx.recorder.recorder

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaFormat
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.xslczx.recorder.RecorderConstant
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PCMReader(
    private val config: RecordConfig,
    private val mediaFormat: MediaFormat,
) {
    companion object {
        private const val TAG = ">>>:PCMReader"
    }

    private val audioRecord: AudioRecord = createAudioRecord()
    private var automaticGainControl: AutomaticGainControl? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var bufferSize = 0

    init {
        enableAutomaticGainControl()
        enableEchoSuppressor()
        enableNoiseSuppressor()
    }

    fun start() {
        audioRecord.startRecording()
    }

    fun stop() {
        try {
            if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop()
            }
            totalDurationMillis = 0L
        } catch (_: IllegalStateException) {
        }
    }

    var totalDurationMillis = 0L

    @Throws(Exception::class)
    fun read(): ReadResult {
        val buffer = ByteArray(bufferSize)
        val bytesRead = audioRecord.read(buffer, 0, buffer.size)
        if (bytesRead < 0) {
            throw Exception(getReadFailureReason(bytesRead))
        }
        val sampleRate = config.sampleRate
        val bitsPerSample = if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) 16 else 8
        val channelCount = if (channels == AudioFormat.CHANNEL_IN_MONO) 1 else 2
        val durationMillis =
            bytesRead.toDouble() * 1000 / (sampleRate * channelCount * (bitsPerSample / 8))
        totalDurationMillis += durationMillis.toLong()

        return ReadResult(buffer, buffer.toShortArray(), bytesRead, totalDurationMillis)
    }

    fun ByteArray.toShortArray(): ShortArray {
        return ByteBuffer.wrap(this)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .let { buf ->
                val shorts = ShortArray(buf.remaining())
                buf.get(shorts)
                shorts
            }
    }

    fun release() {
        audioRecord.release()
        automaticGainControl?.release()
        acousticEchoCanceler?.release()
        noiseSuppressor?.release()
    }

    @SuppressLint("MissingPermission")
    @Throws(Exception::class)
    private fun createAudioRecord(): AudioRecord {
        val sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        bufferSize = getMinBufferSize(sampleRate, channels, audioFormat)

        val reader = try {
            if (config.internalMode && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                if (config.mediaProjection == null) {
                    throw Exception("MediaProjection is null")
                }
                RecorderConstant.logD(PCMReader::class.java.simpleName, "createAudioRecord","internalMode")
                val playbackConfig =
                    AudioPlaybackCaptureConfiguration.Builder(config.mediaProjection!!)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .build()
                AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channels)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(playbackConfig)
                    .build()
            } else {
                RecorderConstant.logD(PCMReader::class.java.simpleName, "createAudioRecord")
                AudioRecord(
                    config.audioSource,
                    sampleRate,
                    channels,
                    audioFormat,
                    bufferSize
                )
            }
        } catch (e: IllegalArgumentException) {
            throw Exception("Unable to instantiate PCM reader.", e)
        }
        if (reader.state != AudioRecord.STATE_INITIALIZED) {
            throw Exception("PCM reader failed to initialize.")
        }

        if (config.device != null) {
            if (!reader.setPreferredDevice(config.device)) {
                Log.w(TAG, "Unable to set device ${config.device.productName}")
            }
        }

        return reader
    }

    private val audioFormat: Int
        get() {
            return AudioFormat.ENCODING_PCM_16BIT
        }

    private val channels: Int
        get() {
            return if (mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1) {
                AudioFormat.CHANNEL_IN_MONO
            } else {
                AudioFormat.CHANNEL_IN_STEREO
            }
        }

    @Throws(Exception::class)
    private fun getMinBufferSize(sampleRate: Int, channelConfig: Int, audioFormat: Int): Int {
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            throw Exception("Recording config is not supported by the hardware, or an invalid config was provided.")
        }

        return bufferSize * 2
    }

    private fun enableAutomaticGainControl() {
        if (AutomaticGainControl.isAvailable()) {
            automaticGainControl = AutomaticGainControl.create(audioRecord.audioSessionId)
            automaticGainControl?.enabled = config.autoGain
        } else if (config.autoGain) {
            Log.d(TAG, "Auto gain effect is not available.")
        }
    }

    private fun enableNoiseSuppressor() {
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioRecord.audioSessionId)
            noiseSuppressor?.enabled = config.noiseSuppress
        } else if (config.noiseSuppress) {
            Log.d(TAG, "Noise suppressor effect is not available.")
        }
    }

    private fun enableEchoSuppressor() {
        if (AcousticEchoCanceler.isAvailable()) {
            acousticEchoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)
            acousticEchoCanceler?.enabled = config.echoCancel
        } else if (config.echoCancel) {
            Log.d(TAG, "Echo canceler effect is not available.")
        }
    }

    private fun getReadFailureReason(errorCode: Int): String {
        val str = StringBuilder("Error when reading audio data:").appendLine()

        when (errorCode) {
            AudioRecord.ERROR_INVALID_OPERATION -> str.append("ERROR_INVALID_OPERATION: Failure due to the improper use of a method.")
            AudioRecord.ERROR_BAD_VALUE -> str.append("ERROR_BAD_VALUE: Failure due to the use of an invalid value.")
            AudioRecord.ERROR_DEAD_OBJECT -> str.append("ERROR_DEAD_OBJECT: Object is no longer valid and needs to be recreated.")
            AudioRecord.ERROR -> str.append("ERROR: Generic operation failure")
            else -> str.append("Unknown errorCode: (").append(errorCode).append(")")
        }

        return str.toString()
    }
}