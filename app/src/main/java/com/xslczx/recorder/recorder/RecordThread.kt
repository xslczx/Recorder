package com.xslczx.recorder.recorder

import com.xslczx.recorder.Utils
import com.xslczx.recorder.encoder.AudioEncoder
import com.xslczx.recorder.encoder.EncoderListener
import com.xslczx.recorder.encoder.IEncoder
import com.xslczx.recorder.format.AacFormat
import com.xslczx.recorder.format.AmrNbFormat
import com.xslczx.recorder.format.AmrWbFormat
import com.xslczx.recorder.format.FlacFormat
import com.xslczx.recorder.format.Format
import com.xslczx.recorder.format.OpusFormat
import com.xslczx.recorder.format.PcmFormat
import com.xslczx.recorder.format.WaveFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

class RecordThread(
    private val config: RecordConfig,
    private val recorderListener: OnAudioRecordListener
) : EncoderListener {
    private var mPcmReader: PCMReader? = null
    private var mEncoder: IEncoder? = null

    private val mIsRecording = AtomicBoolean(false)
    private val mIsPaused = AtomicBoolean(false)
    private val mIsPausedSem = Semaphore(0)
    private var mHasBeenCanceled = false

    private val mExecutorService = Executors.newSingleThreadExecutor()

    override fun onEncoderFailure(ex: Exception) {
        recorderListener.onFailure(ex)
    }

    override fun onEncoderStream(bytes: ByteArray) {
        recorderListener.onStream(bytes)
    }

    override fun onEncodeDuration(durationMs: Long) {
        recorderListener.onEncodeDuration(durationMs)
    }

    fun isRecording(): Boolean {
        return mEncoder != null && mIsRecording.get()
    }

    fun isPaused(): Boolean {
        return mEncoder != null && mIsPaused.get()
    }

    fun pauseRecording() {
        if (isRecording()) {
            pauseState()
        }
    }

    fun resumeRecording() {
        if (isPaused()) {
            recordState()
        }
    }

    fun stopRecording() {
        if (isRecording()) {
            mIsRecording.set(false)
            mIsPaused.set(false)
            mIsPausedSem.release()
        }
    }

    fun cancelRecording() {
        if (isRecording()) {
            mHasBeenCanceled = true
            stopRecording()
        } else {
            Utils.deleteFile(config.outputPath)
        }
    }

    fun startRecording() {
        val startLatch = CountDownLatch(1)

        mExecutorService.execute {
            try {
                val format = selectFormat()
                val (encoder, adjustedFormat) = format.getEncoder(config, this)

                val pcmReader = PCMReader(config, adjustedFormat)
                mPcmReader = pcmReader
                pcmReader.start()

                mEncoder = encoder
                encoder.startEncoding()

                recordState()

                startLatch.countDown()

                while (isRecording()) {
                    if (isPaused()) {
                        recorderListener.onPause()
                        mIsPausedSem.acquire()
                    } else {
                        val result = pcmReader.read()
                        val buffer = result.byteArray
                        recorderListener.onPcmData(buffer, result.shortArray, result.bytesRead)
                        recorderListener.onEncodeDuration(result.durationMillis)
                        if (buffer.isNotEmpty()) {
                            encoder.encode(buffer)
                        }
                    }
                }
            } catch (ex: Exception) {
                recorderListener.onFailure(ex)
            } finally {
                startLatch.countDown()
                stopAndRelease()
            }
        }

        startLatch.await()
    }

    private fun stopAndRelease() {
        try {
            mPcmReader?.stop()
            mPcmReader?.release()
            mPcmReader = null

            mEncoder?.stopEncoding()
            mEncoder = null

            if (mHasBeenCanceled) {
                Utils.deleteFile(config.outputPath)
            }
        } catch (ex: Exception) {
            recorderListener.onFailure(ex)
        } finally {
            recorderListener.onStop()
        }
    }

    private fun selectFormat(): Format {
        return when (config.audioEncoder) {
            AudioEncoder.AacLc, AudioEncoder.AacEld, AudioEncoder.AacHe -> AacFormat()
            AudioEncoder.AmrNb -> AmrNbFormat()
            AudioEncoder.AmrWb -> AmrWbFormat()
            AudioEncoder.Flac -> FlacFormat()
            AudioEncoder.Pcm16bits -> PcmFormat()
            AudioEncoder.Opus -> OpusFormat()
            AudioEncoder.Wav -> WaveFormat()
        }
    }

    private fun pauseState() {
        mIsRecording.set(true)
        mIsPaused.set(true)
    }

    private fun recordState() {
        mIsRecording.set(true)
        mIsPaused.set(false)
        mIsPausedSem.release()
        recorderListener.onRecord()
    }
}