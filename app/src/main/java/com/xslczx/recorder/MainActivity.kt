package com.xslczx.recorder

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.xslczx.recorder.databinding.ActivityMainBinding
import com.xslczx.recorder.encoder.AudioEncoder
import com.xslczx.recorder.fftlib.FftFactory
import com.xslczx.recorder.recorder.AudioRecorder
import com.xslczx.recorder.recorder.RecordConfig
import com.xslczx.recorder.recorder.RecordState
import com.xslczx.recorder.recorder.RecorderDurationHandler
import com.xslczx.recorder.recorder.RecorderRecordStreamHandler
import com.xslczx.recorder.recorder.RecorderStateStreamHandler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10

class MainActivity : AppCompatActivity(),
    RecorderStateStreamHandler, RecorderRecordStreamHandler, RecorderDurationHandler {

    private val audioRecorder by lazy {
        AudioRecorder(this)
    }
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private val fftFactory = FftFactory(FftFactory.Level.Original)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.btnRecord.setOnClickListener {
            if (PermissionUtils.isGrantedRecordAudio(this)) {
                if (binding.checkbox.isChecked) {
                    AudioCaptureService.stop(this)
                    startInternalRecording()
                } else {
                    AudioCaptureService.stop(this)
                    audioRecorder.start(
                        RecordConfig(
                            outputPath = filesDir.absolutePath + "/mic.pcm",
                            encoder = AudioEncoder.Pcm16bits.value,
                            sampleRate = 44100,
                            bitRate = 128000,
                            numChannels = 2,
                        )
                    )
                }

            } else {
                PermissionUtils.requestRecordAudio(this)
            }
        }
        binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                audioRecorder.stop(null)
                if (captureService == null) {
                    binding.btnRecord.performClick()
                }
            } else {
                captureService?.unbindServiceSafely(this,captureServiceConnection)
                captureService?.stopRecorder(null)
            }
        }
        binding.btnResume.setOnClickListener {
            if (binding.checkbox.isChecked) {
                captureService?.resumeRecorder()
            } else {
                audioRecorder.resume()
            }
        }
        binding.btnPause.setOnClickListener {
            if (binding.checkbox.isChecked) {
                captureService?.pauseRecorder()
            } else {
                audioRecorder.pause()
            }
        }
        binding.btnStop.setOnClickListener {
            if (binding.checkbox.isChecked) {
                captureService?.unbindServiceSafely(this,captureServiceConnection)
                captureService?.stopRecorder {
                    RecorderConstant.logD("MainActivity", "btnStop", "stop:$it")
                }
            } else {
                audioRecorder.stop {
                    RecorderConstant.logD("MainActivity", "btnStop", "stop:$it")
                }
            }
        }
        audioRecorder.setRecorderRecordStreamHandler(this)
        audioRecorder.setRecorderStateStreamHandler(this)
        audioRecorder.setRecorderDurationHandler(this)
    }

    override fun sendStateEvent(state: RecordState) {
        RecorderConstant.logD("MainActivity", "sendStateEvent", "state:${state.name}")
        binding.tvRecordState.text = buildString {
            append("state:${state.name}")
            append("\n")
            append("isRecording:${audioRecorder.isRecording}")
            append("\n")
            append("isPaused:${audioRecorder.isPaused}")
        }
        if (state == RecordState.STOP) {
            lastTime = 0L
        }
    }

    override fun sendStateErrorEvent(ex: Exception) {
        RecorderConstant.logW("MainActivity", "sendStateErrorEvent", ex)
    }

    override fun sendStreamChunkEvent(buffer: ByteArray) {
    }

    override fun sendPcmEvent(byteArray: ByteArray, shortArray: ShortArray, bytesRead: Int) {
        if (bytesRead > 0) {
            val fft = fftFactory.makeFftData(byteArray)

            val model = FloatArray(fft.size / 2 + 1)
            model[0] = abs(fft[1].toInt()).toFloat()

            var j = 1
            var i = 2
            while (i < fft.size / 2) {
                model[j] = kotlin.math.hypot(fft[i].toDouble(), fft[i + 1].toDouble()).toFloat()
                i += 2
                j++
                model[j] = abs(fft[j].toInt()).toFloat()
            }
            val amplitude = getAmplitude(byteArray, bytesRead)
            binding.visualizer.addAmplitudesFromPcm(byteArray, 2)
        }
    }

    // Assuming the input is signed int 16
    private fun getAmplitude(chunk: ByteArray, size: Int): Double {
        var max = -160

        val buf = ShortArray(size / 2)
        ByteBuffer.wrap(chunk, 0, size).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()[buf]

        for (b in buf) {
            val curSample = abs(b.toInt())
            if (curSample > max) {
                max = curSample
            }
        }

        return 20 * log10(max / 32767.0) // 16 signed bits 2^15 - 1
    }

    var lastTime = 0L
    override fun sendDurationEvent(durationMs: Long) {
        RecorderConstant.logV(
            "MainActivity",
            "sendDurationEvent",
            "durationMs:$durationMs"
        )
        if (durationMs - lastTime > 100) {
            lastTime = durationMs
            runOnUiThread {
                binding.tvTime.text = String.format(
                    "%02d:%02d:%03d",
                    durationMs / 1000 / 60,
                    durationMs / 1000 % 60,
                    durationMs % 1000
                )
            }
        }
    }

    object PermissionUtils {
        fun isGrantedRecordAudio(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun requestRecordAudio(activity: Activity) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                3
            )
        }
    }

    private fun startInternalRecording() {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = projectionManager.createScreenCaptureIntent()
        startActivityForResult(captureIntent, 1001)
    }

    private var captureService: AudioCaptureService? = null
    private val captureServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            p0: ComponentName?,
            p1: IBinder?
        ) {
            RecorderConstant.logD("MainActivity", "onServiceConnected")
            captureService = (p1 as? AudioCaptureService.AudioCaptureBinder)?.service
            captureService?.setRecorderRecordStreamHandler(this@MainActivity)
            captureService?.setRecorderDurationHandler(this@MainActivity)
            captureService?.setRecorderStateStreamHandler(this@MainActivity)
            onServiceConnected()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            RecorderConstant.logD("MainActivity", "onServiceDisconnected")
            captureService = null
        }
    }

    private fun onServiceConnected() {
        captureService?.startRecorder(
            RecordConfig(
                outputPath = filesDir.absolutePath + "/internal.wav",
                encoder = AudioEncoder.Wav.value,
                sampleRate = 44100,
                bitRate = 128000,
                numChannels = 2,
                internalMode = true
            )
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            AudioCaptureService.start(this, data, captureServiceConnection)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder.dispose()
        AudioCaptureService.stop(this)
    }

}