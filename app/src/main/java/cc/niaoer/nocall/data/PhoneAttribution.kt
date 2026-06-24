package cc.niaoer.nocall.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AttributionResult(
    val province: String,
    val city: String,
    val carrier: String
)

class PhoneAttribution(data: ByteArray) {

    private val buffer = ByteBuffer.wrap(data)
        .asReadOnlyBuffer()
        .order(ByteOrder.LITTLE_ENDIAN)

    private val indexOffset: Int

    init {
        // skip version (4 bytes), read first index offset (4 bytes)
        indexOffset = buffer.getInt(4)
    }

    fun lookup(phoneNumber: String): AttributionResult? {
        val normalized = normalizePhone(phoneNumber)
        if (normalized.length < 7) return null
        val prefix = normalized.substring(0, 7).toIntOrNull() ?: return null

        val indexCount = (buffer.capacity() - indexOffset) / INDEX_ENTRY_SIZE
        var left = 0
        var right = indexCount - 1

        while (left <= right) {
            val mid = (left + right) / 2
            val pos = indexOffset + mid * INDEX_ENTRY_SIZE

            buffer.position(pos)
            val midPrefix = buffer.int

            when {
                midPrefix == prefix -> {
                    val recordOffset = buffer.int
                    val ispCode = buffer.get().toInt() and 0xFF
                    val record = readRecord(recordOffset) ?: return null
                    val parts = record.split("|")
                    if (parts.size < 2) return null
                    return AttributionResult(
                        province = parts[0],
                        city = parts[1],
                        carrier = ispToCarrier(ispCode)
                    )
                }
                midPrefix > prefix -> right = mid - 1
                else -> left = mid + 1
            }
        }
        return null
    }

    private fun readRecord(offset: Int): String? {
        buffer.position(offset)
        val bytes = mutableListOf<Byte>()
        while (true) {
            val b = buffer.get()
            if (b == 0.toByte()) break
            bytes.add(b)
        }
        if (bytes.isEmpty()) return null
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun ispToCarrier(code: Int): String = when (code) {
        1 -> "中国移动"
        2 -> "中国联通"
        3 -> "中国电信"
        4 -> "中国电信虚拟运营商"
        5 -> "中国联通虚拟运营商"
        6 -> "中国移动虚拟运营商"
        7 -> "中国广电"
        8 -> "中国广电虚拟运营商"
        else -> "未知运营商"
    }

    companion object {
        private const val INDEX_ENTRY_SIZE = 9
    }
}
