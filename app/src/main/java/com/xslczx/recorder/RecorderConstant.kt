package com.xslczx.recorder

import android.util.Log

object RecorderConstant {

    fun logV(tag: String, method: String, message: String? = null) {
        val str = if (message != null) "==>$message" else ""
        Log.v(">>>:Recorder", "$tag.$method $str")
    }

    fun logD(tag: String, method: String, message: String? = null) {
        val str = if (message != null) "==>$message" else ""
        Log.d(">>>:Recorder", "$tag.$method $str")
    }

    fun logW(tag: String, method: String, t: Throwable) {
        Log.w(">>>:Recorder", "$tag.$method", t)
    }
}