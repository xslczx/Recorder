package com.xslczx.recorder.recorder

data class ReadResult(
    val byteArray: ByteArray,
    val shortArray: ShortArray,
    val bytesRead: Int,
    val durationMillis: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReadResult

        if (bytesRead != other.bytesRead) return false
        if (durationMillis != other.durationMillis) return false
        if (!byteArray.contentEquals(other.byteArray)) return false
        if (!shortArray.contentEquals(other.shortArray)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytesRead
        result = 31 * result + durationMillis.hashCode()
        result = 31 * result + byteArray.contentHashCode()
        result = 31 * result + shortArray.contentHashCode()
        return result
    }

}