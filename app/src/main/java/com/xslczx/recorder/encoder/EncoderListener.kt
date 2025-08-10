package com.xslczx.recorder.encoder

interface EncoderListener {
    /**
     * Called when an error occured during the encoding process
     */
    fun onEncoderFailure(ex: Exception)

    /**
     * Provides encoded data available for streaming
     */
    fun onEncoderStream(bytes: ByteArray)

    /**
     * Provides the duration of the encoding process in milliseconds
     */
    fun onEncodeDuration(durationMs: Long)
}