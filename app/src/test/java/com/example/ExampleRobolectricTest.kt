package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.network.CurrencyCdnResponse
import com.example.network.FrankfurterTimeSeriesResponse
import com.example.network.GoldSpotResponse
import com.example.ui.strings.NumberFormatters
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Chand?!", appName)
  }

  @Test
  fun `formatChartJalaliDate includes Jalali year for multi-day ranges`() {
    // 2026-11-20 12:00:00 UTC -> Jalali 8/29/1405
    val formatted = NumberFormatters.formatChartJalaliDate(
      timestampSec = 1795176000L,
      usePersianDigits = false,
      includeTime = false,
      includeYear = true
    )
    assertTrue("Expected Jalali year 1405 in $formatted", formatted.contains("1405"))
  }

  @Test
  fun `formatDeltaWithArrow displays non-zero delta cleanly`() {
    assertEquals("↑3.8K", NumberFormatters.formatDeltaWithArrow(3800.0, 1.43, false))
    assertEquals("↓500", NumberFormatters.formatDeltaWithArrow(-500.0, -0.5, false))
  }

  @Test
  fun `moshi parses Frankfurter, GoldSpot, and CurrencyCDN responses`() {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    val frankfurterJson = """{"amount":1.0,"base":"USD","start_date":"2026-11-01","end_date":"2026-11-02","rates":{"2026-11-01":{"EUR":0.92},"2026-11-02":{"EUR":0.93}}}"""
    val fResp = moshi.adapter(FrankfurterTimeSeriesResponse::class.java).fromJson(frankfurterJson)
    assertNotNull(fResp)
    assertEquals(0.92, fResp!!.rates["2026-11-01"]?.get("EUR") ?: 0.0, 0.001)

    val goldJson = """{"name":"Gold","price":2645.5,"symbol":"XAU","updatedAt":"2026-11-20T18:00:00Z"}"""
    val gResp = moshi.adapter(GoldSpotResponse::class.java).fromJson(goldJson)
    assertNotNull(gResp)
    assertEquals(2645.5, gResp!!.price, 0.01)

    val cdnJson = """{"date":"2026-11-20","usd":{"aed":3.6725,"xau":0.000378}}"""
    val cResp = moshi.adapter(CurrencyCdnResponse::class.java).fromJson(cdnJson)
    assertNotNull(cResp)
    assertEquals(3.6725, cResp!!.usd["aed"] ?: 0.0, 0.0001)
  }
}
