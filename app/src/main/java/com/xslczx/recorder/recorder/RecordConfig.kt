package com.xslczx.recorder.recorder

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.IntRange
import com.xslczx.recorder.DeviceUtils
import com.xslczx.recorder.encoder.AudioEncoder

data class RecordConfig(
    val outputPath: String?,//输出文件
    val encoder: String,//编码器
    val sampleRate: Int,//采样率
    val bitRate: Int,//比特率
    @IntRange(from = 1, to = 2)
    val numChannels: Int,//声道数

    val audioSource: Int = MediaRecorder.AudioSource.MIC,//音频源
    val internalMode: Boolean = false,
    val device: AudioDeviceInfo? = null,//音频设备
    var maxDurationMs: Long = 600000,//最长录制时长
    var minDurationMs: Long = 1000,//最短录制时长
    var autoGain: Boolean = false,//AGC，自动增益控制
    var echoCancel: Boolean = false,//AEC，声学回声消除
    var noiseSuppress: Boolean = false,//NS，噪声抑制
    var muteAudio: Boolean = false,//静音
    var speakerphone: Boolean = false,//扬声器
    var audioManagerMode: Int = AudioManager.MODE_NORMAL,//音频管理器模式
    var interruption: Int = 0,//音频中断
    var mediaProjection: MediaProjection? = null,
) {

    val audioInterruption: AudioInterruption = when (interruption) {
        0 -> AudioInterruption.NONE
        1 -> AudioInterruption.PAUSE
        2 -> AudioInterruption.PAUSE_RESUME
        else -> AudioInterruption.PAUSE
    }

    enum class AudioInterruption {
        NONE,
        PAUSE,
        PAUSE_RESUME
    }

    val channelConfig: Int = when (numChannels) {
        1 -> AudioFormat.CHANNEL_IN_MONO
        2 -> AudioFormat.CHANNEL_IN_STEREO
        else -> AudioFormat.CHANNEL_IN_MONO
    }

    val deviceInfo = if (device != null) buildString {
        append("名称:")
        append(device.productName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            append("(${device.address})")
        }
        append("类型:")
        append(DeviceUtils.Companion.typeToString(device.type))
        append("是否输出:")
        append(device.isSink)
        append("支持的采样率:")
        append(device.sampleRates.joinToString(","))
        append("支持的声道数:")
        append(device.channelCounts.joinToString(","))
    } else ""

    val audioEncoder: AudioEncoder = when (encoder) {
        "aacLc" -> AudioEncoder.AacLc
        "aacEld" -> AudioEncoder.AacEld
        "aacHe" -> AudioEncoder.AacHe
        "amrNb" -> AudioEncoder.AmrNb
        "amrWb" -> AudioEncoder.AmrWb
        "flac" -> AudioEncoder.Flac
        "pcm16bits" -> AudioEncoder.Pcm16bits
        "opus" -> AudioEncoder.Opus
        "wav" -> AudioEncoder.Wav
        else -> AudioEncoder.AacLc
    }
}