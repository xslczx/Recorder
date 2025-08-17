package com.xslczx.recorder.visualizer

data class Frame(
    val waveform: ByteArray? = null,
    val fft: ByteArray? = null,
    val captureRate: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Frame

        if (captureRate != other.captureRate) return false
        if (!waveform.contentEquals(other.waveform)) return false
        if (!fft.contentEquals(other.fft)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = captureRate
        result = 31 * result + (waveform?.contentHashCode() ?: 0)
        result = 31 * result + (fft?.contentHashCode() ?: 0)
        return result
    }
}
