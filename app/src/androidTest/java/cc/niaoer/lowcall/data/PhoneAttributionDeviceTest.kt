package cc.niaoer.lowcall.data

import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneAttributionDeviceTest {

    @Test
    fun realAssetLandlineLookupIsBoundedAndCached() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val data = context.assets.open("phone.dat").use { it.readBytes() }
        val attribution = PhoneAttribution(data)

        val firstStart = SystemClock.elapsedRealtimeNanos()
        val first = attribution.lookupAny("02169619151")
        val firstMs = (SystemClock.elapsedRealtimeNanos() - firstStart) / 1_000_000.0
        val cachedStart = SystemClock.elapsedRealtimeNanos()
        val cached = attribution.lookupAny("02169619151")
        val cachedMs = (SystemClock.elapsedRealtimeNanos() - cachedStart) / 1_000_000.0

        Log.i("PhoneAttribution", "first=$firstMs ms, cached=$cachedMs ms")
        assertEquals("上海", first?.city)
        assertEquals("上海", cached?.city)
        assertTrue(firstMs < 1000.0)
        assertTrue(cachedMs < 100.0)
    }
}
