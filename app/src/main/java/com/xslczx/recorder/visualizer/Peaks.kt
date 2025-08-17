package com.xslczx.recorder.visualizer

data class Peaks(
    val min: IntArray,
    val max: IntArray,
    val sampleRate: Int,
    val totalSamples: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Peaks

        if (sampleRate != other.sampleRate) return false
        if (totalSamples != other.totalSamples) return false
        if (!min.contentEquals(other.min)) return false
        if (!max.contentEquals(other.max)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sampleRate
        result = 31 * result + totalSamples.hashCode()
        result = 31 * result + min.contentHashCode()
        result = 31 * result + max.contentHashCode()
        return result
    }
}