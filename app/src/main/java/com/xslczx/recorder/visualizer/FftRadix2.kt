package com.xslczx.recorder.visualizer

class FftRadix2(private val n: Int) {
    private val rev = IntArray(n)
    private val cos = FloatArray(n / 2)
    private val sin = FloatArray(n / 2)
    init {
        require(n and (n - 1) == 0) { "FFT size must be power of 2" }
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) rev[i] = Integer.reverse(i) ushr (32 - bits)
        for (i in 0 until n / 2) {
            val ang = -2.0 * Math.PI * i / n
            cos[i] = kotlin.math.cos(ang).toFloat()
            sin[i] = kotlin.math.sin(ang).toFloat()
        }
    }
    fun fft(re: FloatArray, im: FloatArray) {
        val n = this.n
        // bit-reverse copy
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var i = 0
            while (i < n) {
                var k = 0
                for (j in 0 until half) {
                    val wr = cos[k]; val wi = sin[k]
                    val uR = re[i + j]; val uI = im[i + j]
                    val vR = re[i + j + half] * wr - im[i + j + half] * wi
                    val vI = re[i + j + half] * wi + im[i + j + half] * wr
                    re[i + j] = uR + vR; im[i + j] = uI + vI
                    re[i + j + half] = uR - vR; im[i + j + half] = uI - vI
                    k += step
                }
                i += len
            }
            len = len shl 1
        }
    }
}
