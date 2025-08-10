package com.xslczx.recorder.encoder

enum class AudioEncoder(val value: String) {
    AacLc("aacLc"),
    AacEld("aacEld"),
    AacHe("aacHe"),
    AmrNb("amrNb"),
    AmrWb("amrWb"),
    Flac("flac"),
    Pcm16bits("pcm16bits"),
    Opus("opus"),
    Wav("wav")
}