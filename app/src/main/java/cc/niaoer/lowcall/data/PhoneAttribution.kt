package cc.niaoer.lowcall.data

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
    private val landlineIndex: Map<String, AttributionResult> by lazy { buildLandlineIndex() }

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

        val localBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)

        while (left <= right) {
            val mid = (left + right) / 2
            val pos = indexOffset + mid * INDEX_ENTRY_SIZE

            localBuffer.position(pos)
            val midPrefix = localBuffer.int

            when {
                midPrefix == prefix -> {
                    val recordOffset = localBuffer.int
                    val ispCode = localBuffer.get().toInt() and 0xFF
                    val record = readRecord(localBuffer, recordOffset) ?: return null
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

    fun lookupLandline(phoneNumber: String): AttributionResult? {
        var normalized = normalizePhone(phoneNumber)
        if (normalized.startsWith("86") && normalized.length > 11) {
            val national = normalized.removePrefix("86")
            normalized = if (national.startsWith("0")) national else "0$national"
        }
        if (!normalized.startsWith("0")) return null

        val areaCode = listOf(3, 4)
            .filter { normalized.length > it }
            .map { normalized.substring(0, it) }
            .firstOrNull { landlineIndex.containsKey(it) }
            ?: return null
        return landlineIndex[areaCode]
    }

    fun lookupAny(phoneNumber: String): AttributionResult? =
        lookup(phoneNumber) ?: lookupLandline(phoneNumber)

    private fun buildLandlineIndex(): Map<String, AttributionResult> {
        val result = mutableMapOf<String, AttributionResult>()
        val localBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var offset = 8
        while (offset < indexOffset) {
            val start = offset
            while (offset < indexOffset && localBuffer.get(offset) != 0.toByte()) offset++
            if (offset >= indexOffset) break
            val bytes = ByteArray(offset - start)
            localBuffer.position(start)
            localBuffer.get(bytes)
            val record = String(bytes, Charsets.UTF_8)
            val parts = record.split("|")
            if (parts.size >= 4 && parts[3].isNotBlank()) {
                result.putIfAbsent(
                    parts[3].trim(),
                    AttributionResult(
                        province = parts[0],
                        city = parts[1],
                        carrier = ""
                    )
                )
            }
            offset++
        }
        return result
    }

    private fun readRecord(localBuffer: ByteBuffer, offset: Int): String? {
        localBuffer.position(offset)
        val bytes = mutableListOf<Byte>()
        while (true) {
            val b = localBuffer.get()
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
