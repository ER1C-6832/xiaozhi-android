package com.er1cmo.xiaozhiandroid.audio

data class PcmFrame(
    val bytes: ByteArray,
    val presentationTimeUs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PcmFrame) return false
        if (!bytes.contentEquals(other.bytes)) return false
        return presentationTimeUs == other.presentationTimeUs
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + presentationTimeUs.hashCode()
        return result
    }
}
