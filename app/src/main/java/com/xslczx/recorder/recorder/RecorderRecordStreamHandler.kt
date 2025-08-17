package com.xslczx.recorder.recorder

interface RecorderRecordStreamHandler {
    fun sendStreamChunkEvent(buffer: ByteArray)
    fun sendPcmEvent(byteArray: ByteArray, bytesRead: Int)
}