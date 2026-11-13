package cc.niaoer.lowcall.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PhoneAttributionTest {

    private lateinit var attribution: PhoneAttribution

    @Before
    fun setUp() {
        // Build a minimal valid phone.dat in memory
        // Records: "广东|深圳|518000|0755\0"
        //           "北京|北京|100000|010\0"
        val record1 = "广东|深圳|518000|0755".toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val record2 = "北京|北京|100000|010".toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val record3 = "上海|上海|200000|021".toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val recordArea = record1 + record2 + record3

        val headerSize = 8
        val recordAreaStart = headerSize
        val indexOffset = recordAreaStart + recordArea.size

        // Index entries (9 bytes each): [prefix:4 LE][record_offset:4 LE][isp_code:1]
        val entry1 = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(1380013)          // prefix 1380013
            putInt(recordAreaStart)  // offset to "广东|深圳..."
            put(1)                   // 中国移动
        }.array()

        val entry2 = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(1390000)          // prefix 1390000
            putInt(recordAreaStart + record1.size)  // offset to "北京|北京..."
            put(2)                   // 中国联通
        }.array()

        val header = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(1701)
            putInt(indexOffset)
        }.array()

        val fullData = header + recordArea + entry1 + entry2
        attribution = PhoneAttribution(fullData)
    }

    @Test
    fun `lookup known prefix returns attribution`() {
        val result = attribution.lookup("13800138000")
        assertNotNull(result)
        assertEquals("广东", result!!.province)
        assertEquals("深圳", result.city)
        assertEquals("中国移动", result.carrier)
    }

    @Test
    fun `lookup second known prefix`() {
        val result = attribution.lookup("13900001111")
        assertNotNull(result)
        assertEquals("北京", result!!.province)
        assertEquals("北京", result.city)
        assertEquals("中国联通", result.carrier)
    }

    @Test
    fun `lookup unknown prefix returns null`() {
        val result = attribution.lookup("13099998888")
        assertNull(result)
    }

    @Test
    fun `lookup short number returns null`() {
        val result = attribution.lookup("123")
        assertNull(result)
    }

    @Test
    fun `lookup non-numeric string returns null`() {
        val result = attribution.lookup("abcdefg")
        assertNull(result)
    }

    @Test
    fun `lookup with dashed number returns correct attribution`() {
        // normalizePhone strips non-digits: "138-0013-8000" → "13800138000"
        val result = attribution.lookup("138-0013-8000")
        assertNotNull(result)
        assertEquals("广东", result!!.province)
    }

    @Test
    fun `isp code 3 maps to China Telecom`() {
        // Build a minimal phone.dat with isp code 3
        val record = "上海|上海|200000|021".toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(1701)
            putInt(8 + record.size) // indexOffset right after record area
        }.array()
        val index = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(1330000)
            putInt(8) // record at byte 8
            put(3)    // 中国电信
        }.array()
        val data = header + record + index
        val attr = PhoneAttribution(data)

        val result = attr.lookup("13300001111")
        assertNotNull(result)
        assertEquals("中国电信", result!!.carrier)
    }

    @Test
    fun `lookup landline with two digit area code`() {
        val result = attribution.lookupLandline("02169619151")
        assertNotNull(result)
        assertEquals("上海", result!!.province)
        assertEquals("上海", result.city)
        assertEquals("", result.carrier)
    }

    @Test
    fun `lookup landline with three digit area code`() {
        val result = attribution.lookupLandline("075512345678")
        assertNotNull(result)
        assertEquals("广东", result!!.province)
        assertEquals("深圳", result.city)
    }

    @Test
    fun `lookup unknown landline area code returns null`() {
        assertNull(attribution.lookupLandline("099912345678"))
    }

    @Test
    fun `lookupAny supports both mobile and landline`() {
        assertEquals("深圳", attribution.lookupAny("13800138000")?.city)
        assertEquals("上海", attribution.lookupLandline("+862169619151")?.city)
        assertEquals("上海", attribution.lookupAny("+862169619151")?.city)
    }

    @Test
    fun `landline index skips records without area code`() {
        val malformed = "江苏|南京".toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val blank = "浙江|杭州|310000|".toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val valid = "北京|北京|100000|010".toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val records = malformed + blank + valid
        val data = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(1701)
            putInt(8 + records.size)
        }.array() + records
        val attr = PhoneAttribution(data)

        assertNull(attr.lookupLandline("02512345678"))
        assertNull(attr.lookupLandline("057112345678"))
        assertEquals("北京", attr.lookupLandline("01012345678")?.city)
    }
}
