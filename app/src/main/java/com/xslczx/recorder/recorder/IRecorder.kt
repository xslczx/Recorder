package com.xslczx.recorder.recorder


interface IRecorder {
    @Throws(Exception::class)
    fun start(config: RecordConfig)

    /**
     * Stops the recording.
     */
    fun stop(stopCb: ((path: String?) -> Unit)?)

    /**
     * Stops the recording and delete file.
     */
    fun cancel()

    /**
     * Pauses the recording if currently running.
     */
    fun pause()

    /**
     * Resumes the recording if currently paused.
     */
    fun resume()

    /**
     * Gets the state the of recording
     *
     * @return True if recording. False otherwise.
     */
    val isRecording: Boolean

    /**
     * Gets the state the of recording
     *
     * @return True if paused. False otherwise.
     */
    val isPaused: Boolean

    fun dispose()
}