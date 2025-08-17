package com.xslczx.recorder.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.xslczx.recorder.RecorderConstant

@SuppressLint("ObsoleteSdkInt")
class AudioRecorder(private val appContext: Context) : IRecorder, OnAudioRecordListener {

    private var recorderThread: RecordThread? = null
    private var config: RecordConfig? = null
    private var stopCb: ((path: String?) -> Unit)? = null
    private var amPrevMuteSettings = HashMap<Int, Int>()
    private val muteStreams = arrayOf(
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_DTMF,
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_VOICE_CALL,
    )
    private var amPrevAudioMode: Int = AudioManager.MODE_NORMAL
    private var amPrevSpeakerphone = false

    private var afChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    private var afRequest: AudioFocusRequest? = null

    private var recorderStateStreamHandler: RecorderStateStreamHandler? = null
    private var recorderRecordStreamHandler: RecorderRecordStreamHandler? = null
    private var recorderDurationHandler: RecorderDurationHandler? = null

    init {
        saveAudioManagerSettings()
    }

    fun setRecorderStateStreamHandler(handler: RecorderStateStreamHandler) {
        recorderStateStreamHandler = handler
    }

    fun setRecorderRecordStreamHandler(handler: RecorderRecordStreamHandler) {
        recorderRecordStreamHandler = handler
    }

    fun setRecorderDurationHandler(handler: RecorderDurationHandler) {
        recorderDurationHandler = handler
    }

    @Throws(Exception::class)
    override fun start(config: RecordConfig) {
        this.config = config

        recorderThread = RecordThread(config, this)
        recorderThread!!.startRecording()

        assignAudioManagerSettings(config)
    }

    override fun stop(stopCb: ((path: String?) -> Unit)?) {
        this.stopCb = stopCb

        recorderThread?.stopRecording()
    }

    override fun cancel() {
        recorderThread?.cancelRecording()
    }

    override fun pause() {
        if (isRecording) {
            restoreAudioManagerSettings()
        }

        recorderThread?.pauseRecording()
    }

    override fun resume() {
        if (isPaused) {
            assignAudioManagerSettings(config)
        }

        recorderThread?.resumeRecording()
    }

    override val isRecording: Boolean
        get() = recorderThread?.isRecording() == true

    override val isPaused: Boolean
        get() = recorderThread?.isPaused() == true

    override fun dispose() {
        stop(null)
    }

    override fun onRecord() {
        recorderStateStreamHandler?.sendStateEvent(RecordState.RECORD)
    }

    override fun onPause() {
        recorderStateStreamHandler?.sendStateEvent(RecordState.PAUSE)
    }

    override fun onStop() {
        restoreAudioManagerSettings()

        stopCb?.invoke(config?.outputPath)
        stopCb = null

        recorderStateStreamHandler?.sendStateEvent(RecordState.STOP)
    }

    override fun onFailure(ex: Exception) {
        recorderStateStreamHandler?.sendStateErrorEvent(ex)
    }

    override fun onStream(chunk: ByteArray) {
        recorderRecordStreamHandler?.sendStreamChunkEvent(chunk)
    }

    override fun onEncodeDuration(durationMs: Long) {
        recorderDurationHandler?.sendDurationEvent(durationMs)
    }

    override fun onPcmData(
        byteArray: ByteArray,
        bytesRead: Int
    ) {
        recorderRecordStreamHandler?.sendPcmEvent(byteArray, bytesRead)
    }

    @Suppress("DEPRECATION")
    private fun saveAudioManagerSettings() {
        amPrevMuteSettings.clear()

        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        muteStreams.forEach { stream ->
            amPrevMuteSettings[stream] = audioManager.getStreamVolume(stream)
        }

        amPrevAudioMode = audioManager.mode
        amPrevSpeakerphone = audioManager.isSpeakerphoneOn
    }

    @Suppress("DEPRECATION")
    private fun assignAudioManagerSettings(config: RecordConfig?) {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        requestAudioFocus(audioManager)

        val conf = config ?: return

        if (conf.muteAudio) {
            muteAudio(audioManager, true)
        }
        if (conf.audioManagerMode != AudioManager.MODE_NORMAL) {
            audioManager.mode = conf.audioManagerMode
        }
        if (conf.speakerphone) {
            audioManager.isSpeakerphoneOn = true
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreAudioManagerSettings() {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        abandonAudioFocus(audioManager)

        val conf = config ?: return

        if (conf.muteAudio) {
            muteAudio(audioManager, false)
        }
        if (conf.audioManagerMode != AudioManager.MODE_NORMAL) {
            audioManager.mode = amPrevAudioMode
        }
        if (conf.speakerphone) {
            audioManager.isSpeakerphoneOn = amPrevSpeakerphone
        }
    }

    private fun muteAudio(audioManager: AudioManager, mute: Boolean) {
        val muteValue = AudioManager.ADJUST_MUTE
        val unmuteValue = AudioManager.ADJUST_UNMUTE

        muteStreams.forEach { stream ->
            val volumeLevel = if (mute) muteValue else (amPrevMuteSettings[stream] ?: unmuteValue)
            audioManager.setStreamVolume(stream, volumeLevel, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocus(audioManager: AudioManager) {
        val conf = config ?: return
        afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                if (conf.audioInterruption != RecordConfig.AudioInterruption.NONE) {
                    RecorderConstant.logD(
                        AudioRecorder::class.java.simpleName,
                        "requestAudioFocus",
                        "pauseRecording"
                    )
                    recorderThread?.pauseRecording()
                }
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                if (conf.audioInterruption == RecordConfig.AudioInterruption.PAUSE_RESUME) {
                    RecorderConstant.logD(
                        AudioRecorder::class.java.simpleName,
                        "requestAudioFocus",
                        "resumeRecording"
                    )
                    recorderThread?.resumeRecording()
                }
            }
        }

        if (conf.internalMode) {
            return
        }
        RecorderConstant.logD(
            AudioRecorder::class.java.simpleName,
            "onAudioFocusChange",
            "requestAudioFocus"
        )

        if (Build.VERSION.SDK_INT >= 26) {
            afRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    build()
                })
                setOnAudioFocusChangeListener(afChangeListener!!, Handler(Looper.getMainLooper()))
                build()
            }.apply {
                audioManager.requestAudioFocus(this)
            }
        } else {
            audioManager.requestAudioFocus(
                afChangeListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocus(audioManager: AudioManager) {
        if (config?.internalMode == true) return

        RecorderConstant.logD(AudioRecorder::class.java.simpleName, "abandonAudioFocus")

        if (Build.VERSION.SDK_INT >= 26) {
            if (afRequest != null) {
                audioManager.abandonAudioFocusRequest(afRequest!!)
                afRequest = null
            }
        } else if (afChangeListener != null) {
            audioManager.abandonAudioFocus(afChangeListener)
        }

        afChangeListener = null
    }
}