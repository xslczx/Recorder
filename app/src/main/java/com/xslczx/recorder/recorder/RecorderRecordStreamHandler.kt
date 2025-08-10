package com.xslczx.recorder.recorder

interface RecorderRecordStreamHandler {
    fun sendStreamChunkEvent(buffer: ByteArray)
    fun sendPcmEvent(byteArray: ByteArray,shortArray: ShortArray,bytesRead: Int)
}