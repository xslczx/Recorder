package com.xslczx.recorder.recorder

interface RecorderStateStreamHandler {
    fun sendStateEvent(state: RecordState)
    fun sendStateErrorEvent(ex: Exception)
}