package com.xslczx.recorder.recorder

interface OnAudioRecordListener {
    fun onRecord()
    fun onPause()
    fun onStop()
    fun onFailure(ex: Exception)
    fun onStream(chunk: ByteArray)
    fun onEncodeDuration(durationMs: Long)
    fun onPcmData(byteArray: ByteArray, bytesRead: Int)
}