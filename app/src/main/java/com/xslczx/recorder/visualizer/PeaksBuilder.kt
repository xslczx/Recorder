package com.xslczx.recorder.visualizer

import kotlin.math.max
import kotlin.math.min

object PeaksBuilder {
    /** Build level-0 peaks from a PCM 16-bit mono stream (all data present). */
    fun fromPcmShort(
        pcm: ShortArray,
        sampleRate: Int,
        bucket: Int = 512
    ): Peaks {
        val n = ((pcm.size + bucket - 1) / bucket)
        val minA = IntArray(n)
        val maxA = IntArray(n)
        var i = 0
        var idx = 0
        while (i < pcm.size) {
            var mn = Int.MAX_VALUE
            var mx = Int.MIN_VALUE
            val end = min(pcm.size, i + bucket)
            var j = i
            while (j < end) {
                val v = pcm[j].toInt()
                if (v < mn) mn = v
                if (v > mx) mx = v
                j++
            }
            minA[idx] = if (mn == Int.MAX_VALUE) 0 else mn
            maxA[idx] = if (mx == Int.MIN_VALUE) 0 else mx
            idx++
            i += bucket
        }
        return Peaks(minA, maxA, sampleRate, pcm.size.toLong())
    }

    /** Build a pyramid: each next level merges pairs of buckets (2:1). */
    fun buildPyramid(base: Peaks): List<Peaks> {
        val out = mutableListOf<Peaks>()
        out += base
        var cur = base
        while (cur.min.size >= 2) {
            val n2 = cur.min.size / 2
            val minA = IntArray(n2)
            val maxA = IntArray(n2)
            for (i in 0 until n2) {
                val i0 = i * 2
                val i1 = i0 + 1
                minA[i] = min(cur.min[i0], cur.min.getOrElse(i1) { cur.min[i0] })
                maxA[i] = max(cur.max[i0], cur.max.getOrElse(i1) { cur.max[i0] })
            }
            cur = Peaks(minA, maxA, cur.sampleRate, cur.totalSamples)
            out += cur
            if (n2 < 2) break
        }
        return out
    }
}