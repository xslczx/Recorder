package com.xslczx.recorder.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xslczx.recorder.Utils
import com.xslczx.recorder.encoder.AudioEncoder
import java.io.IOException
import kotlin.math.log10

@SuppressLint("ObsoleteSdkInt")
class MediaRecorder(
    private val context: Context,
    private val recorderStateStreamHandler: RecorderStateStreamHandler,
) : IRecorder {
    companion object {
        private val TAG = MediaRecorder::class.java.simpleName
    }

    private var mIsRecording = false
    private var mIsPaused = false
    private var mRecorder: MediaRecorder? = null
    private var mMaxAmplitude = -160.0
    private var mConfig: RecordConfig? = null

    @Throws(Exception::class)
    override fun start(config: RecordConfig) {
        stopRecording()

        val recorder = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            MediaRecorder()
        } else {
            MediaRecorder(context)
        }

        recorder.setAudioSource(config.audioSource)
        recorder.setAudioEncodingBitRate(config.bitRate)
        recorder.setAudioSamplingRate(config.sampleRate)
        recorder.setAudioChannels(config.numChannels)
        recorder.setOutputFormat(getOutputFormat(config.audioEncoder))
        recorder.setAudioEncoder(getEncoder(config.audioEncoder))
        recorder.setOutputFile(config.outputPath)

        try {
            recorder.prepare()
            recorder.start()

            mConfig = config
            mRecorder = recorder

            updateState(RecordState.RECORD)
        } catch (e: IOException) {
            recorder.release()
            throw Exception(e)
        } catch (e: IllegalStateException) {
            recorder.release()
            throw Exception(e)
        }
    }

    override fun stop(stopCb: ((path: String?) -> Unit)?) {
        stopRecording()
        stopCb?.invoke(mConfig?.outputPath)
    }

    override fun cancel() {
        stopRecording()
        Utils.deleteFile(mConfig?.outputPath)
    }

    override fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            pauseRecording()
        }
    }

    override fun resume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resumeRecording()
        }
    }

    override val isRecording: Boolean
        get() = mIsRecording
    override val isPaused: Boolean
        get() = mIsPaused

    fun getAmplitude(): Pair<Double, Double> {
        var current = -160.0

        if (mIsRecording) {
            current = 20 * log10(mRecorder!!.maxAmplitude / 32768.0)

            if (current > mMaxAmplitude) {
                mMaxAmplitude = current
            }
        }

        return current to mMaxAmplitude
    }

    override fun dispose() {
        stopRecording()
    }

    private fun stopRecording() {
        if (mRecorder != null) {
            try {
                if (mIsRecording || mIsPaused) {
                    mRecorder!!.stop()
                }
            } catch (ex: RuntimeException) {
            } finally {
                mRecorder!!.reset()
                mRecorder!!.release()
                mRecorder = null
            }
        }

        updateState(RecordState.STOP)
        mMaxAmplitude = -160.0
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun pauseRecording() {
        if (mRecorder != null) {
            try {
                if (mIsRecording) {
                    mRecorder!!.pause()
                    updateState(RecordState.PAUSE)
                }
            } catch (ex: IllegalStateException) {
                Log.w(
                    TAG,
                    """
                        Did you call pause() before before start() or after stop()?
                        ${ex.message}
                        """.trimIndent()
                )
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun resumeRecording() {
        if (mRecorder != null) {
            try {
                if (mIsPaused) {
                    mRecorder!!.resume()
                    updateState(RecordState.RECORD)
                }
            } catch (ex: IllegalStateException) {
                Log.w(
                    TAG,
                    """
                        Did you call resume() before before start() or after stop()?
                        ${ex.message}
                        """.trimIndent()
                )
            }
        }
    }

    private fun updateState(state: RecordState) {
        when (state) {
            RecordState.PAUSE -> {
                mIsRecording = true
                mIsPaused = true
                recorderStateStreamHandler.sendStateEvent(RecordState.PAUSE)
            }

            RecordState.RECORD -> {
                mIsRecording = true
                mIsPaused = false
                recorderStateStreamHandler.sendStateEvent(RecordState.RECORD)
            }

            RecordState.STOP -> {
                mIsRecording = false
                mIsPaused = false
                recorderStateStreamHandler.sendStateEvent(RecordState.STOP)
            }
        }
    }

    private fun getOutputFormat(encoder: AudioEncoder): Int {
        return when (encoder) {
            AudioEncoder.AacLc, AudioEncoder.AacEld, AudioEncoder.AacHe -> MediaRecorder.OutputFormat.MPEG_4
            AudioEncoder.AmrNb, AudioEncoder.AmrWb -> MediaRecorder.OutputFormat.THREE_GPP
            AudioEncoder.Opus -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaRecorder.OutputFormat.OGG
                } else {
                    MediaRecorder.OutputFormat.MPEG_4
                }
            }

            else -> MediaRecorder.OutputFormat.DEFAULT
        }
    }

    private fun getEncoder(encoder: AudioEncoder): Int {
        return when (encoder) {
            AudioEncoder.AacLc -> MediaRecorder.AudioEncoder.AAC
            AudioEncoder.AacEld -> MediaRecorder.AudioEncoder.AAC_ELD
            AudioEncoder.AacHe -> MediaRecorder.AudioEncoder.HE_AAC
            AudioEncoder.AmrNb -> MediaRecorder.AudioEncoder.AMR_NB
            AudioEncoder.AmrWb -> MediaRecorder.AudioEncoder.AMR_WB
            AudioEncoder.Opus -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaRecorder.AudioEncoder.OPUS
                } else {
                    Log.w(TAG, "Falling back to AAC LC")
                    MediaRecorder.AudioEncoder.AAC
                }
            }

            else -> {
                Log.w(TAG, "Falling back to AAC LC")
                MediaRecorder.AudioEncoder.AAC
            }
        }
    }
}
