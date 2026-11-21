package ir.thepoo7an.chand

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import ir.thepoo7an.chand.network.AppUpdateService
import ir.thepoo7an.chand.network.CurrencyCdnResponse
import ir.thepoo7an.chand.network.FrankfurterTimeSeriesResponse
import ir.thepoo7an.chand.network.GitHubReleaseAssetDto
import ir.thepoo7an.chand.network.GitHubReleaseDto
import ir.thepoo7an.chand.network.GoldSpotResponse
import ir.thepoo7an.chand.ui.strings.EnStrings
import ir.thepoo7an.chand.ui.strings.FaStrings
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class)
class ChandAppRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `cold start launches ChandApplication and MainActivity cleanly`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Chand?!").assertExists()
    }

    @Test
    fun `app identity and BuildConfig GitHub repository are configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Chand?!", context.getString(R.string.app_name))
        assertEquals("ir.thepoo7an.chand", BuildConfig.APPLICATION_ID)
        assertTrue(
            "BuildConfig.GITHUB_REPOSITORY should default to owner/repo",
            BuildConfig.GITHUB_REPOSITORY.contains("/")
        )
    }

    @Test
    fun `formatChartJalaliDate and formatDeltaWithArrow render expected Jalali and Toman deltas`() {
        // 2026-11-20 12:00:00 UTC -> Jalali 8/29/1405
        val formatted = NumberFormatters.formatChartJalaliDate(
            timestampSec = 1795176000L,
            usePersianDigits = false,
            includeTime = false,
            includeYear = true
        )
        assertTrue("Expected Jalali year 1405 in $formatted", formatted.contains("1405"))
        assertEquals("↑3.8K", NumberFormatters.formatDeltaWithArrow(3800.0, 1.43, false))
        assertEquals("↓500", NumberFormatters.formatDeltaWithArrow(-500.0, -0.5, false))
    }

    @Test
    fun `moshi codegen parses Frankfurter, GoldSpot, CurrencyCDN, and GitHubRelease cleanly`() {
        val moshi = Moshi.Builder().build()

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

        val ghJson = """{"tag_name":"build-12","name":"Chand?! v1.0.12 (Build 12)","body":"Smaller APK","html_url":"https://github.com/o/r/releases/tag/build-12","assets":[{"name":"Chand-arm64-v8a-release.apk","browser_download_url":"https://example.com/a.apk","size":5242880}]}"""
        val ghResp = moshi.adapter(GitHubReleaseDto::class.java).fromJson(ghJson)
        assertNotNull(ghResp)
        assertEquals(12, AppUpdateService.extractBuildNumber(ghResp!!))
    }

    @Test
    fun `AppUpdateService compares versions and matches device ABI APK`() {
        assertTrue(AppUpdateService.isRemoteNewer("1.0.4", 4, "1.0.5", 5))
        assertTrue(AppUpdateService.isRemoteNewer("1.0", 1, "1.1", null))
        assertFalse(AppUpdateService.isRemoteNewer("1.0.5", 5, "1.0.5", 5))

        val assets = listOf(
            GitHubReleaseAssetDto("Chand-universal-release.apk", "https://u", 10L),
            GitHubReleaseAssetDto("Chand-arm64-v8a-release.apk", "https://arm64", 6L),
            GitHubReleaseAssetDto("Chand-armeabi-v7a-release.apk", "https://arm32", 5L)
        )
        assertEquals("Chand-arm64-v8a-release.apk", AppUpdateService.pickBestApkAsset(assets, arrayOf("arm64-v8a", "armeabi-v7a"))?.name)
        assertEquals("Chand-armeabi-v7a-release.apk", AppUpdateService.pickBestApkAsset(assets, arrayOf("armeabi-v7a"))?.name)
        assertEquals("Chand-universal-release.apk", AppUpdateService.pickBestApkAsset(assets, arrayOf("x86_64"))?.name)
    }

    @Test
    fun `AppUpdateService skips drafts and prereleases when selecting latest stable release`() {
        val releases = listOf(
            GitHubReleaseDto(tagName = "v1.0.9-draft", draft = true, prerelease = false),
            GitHubReleaseDto(tagName = "test-build-8", draft = false, prerelease = true),
            GitHubReleaseDto(tagName = "v1.0.5", draft = false, prerelease = false)
        )
        val picked = AppUpdateService.selectLatestStableRelease(releases)
        assertNotNull(picked)
        assertEquals("v1.0.5", picked!!.tagName)

        val onlyPrereleases = listOf(
            GitHubReleaseDto(tagName = "test-build-8", draft = false, prerelease = true)
        )
        assertEquals(null, AppUpdateService.selectLatestStableRelease(onlyPrereleases))
    }

    @Test
    fun `offline reference banner differs from cached offline banner in both languages`() {
        assertFalse(EnStrings.offlineReferenceBanner == EnStrings.offlineCachedBanner)
        assertFalse(FaStrings.offlineReferenceBanner == FaStrings.offlineCachedBanner)
        assertEquals("thepoo7an/chand-iran-currency-gold-crypto", EnStrings.githubRepoPlaceholder)
        assertEquals("thepoo7an/chand-iran-currency-gold-crypto", FaStrings.githubRepoPlaceholder)
    }
}
