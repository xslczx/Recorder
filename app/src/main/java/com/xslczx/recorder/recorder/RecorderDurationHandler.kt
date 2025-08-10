package com.xslczx.recorder.recorder

interface RecorderDurationHandler {
    fun sendDurationEvent(durationMs: Long)
}