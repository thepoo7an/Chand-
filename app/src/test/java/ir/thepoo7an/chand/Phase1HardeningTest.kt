package ir.thepoo7an.chand

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.MarketUiState
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.AppUpdateState
import ir.thepoo7an.chand.network.HistoryNormalizer
import ir.thepoo7an.chand.ui.components.SettingsSheet
import ir.thepoo7an.chand.ui.screens.MarketFeedScreen
import ir.thepoo7an.chand.ui.strings.EnStrings
import ir.thepoo7an.chand.ui.theme.ChandTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class)
class Phase1HardeningTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleUsdAsset(nowMillis: Long = 1_750_000_000_000L) = MarketAsset(
        id = "USD",
        symbol = "USD",
        nameFa = "دلار آمریکا",
        nameEn = "US Dollar",
        category = AssetCategory.FIAT,
        badgeText = "🇺🇸",
        accentHex = 0xFF10B981,
        priceToman = 115_000.0,
        change24hPercent = 0.5,
        change24hToman = 500.0,
        freshness = DataFreshness.LIVE,
        updatedAtMillis = nowMillis,
        fetchedAtMillis = nowMillis
    )

    @Test
    fun `support development card is hidden by default and opens GitHub URL when enabled`() {
        val defaultPrefs = AppPreferences(language = AppLanguage.EN, usePersianDigits = false)
        assertFalse("showSupportCard must default to false", defaultPrefs.showSupportCard)

        val now = 1_750_000_000_000L
        val assets = listOf(sampleUsdAsset(now))

        // 1. Default prefs: Support card must NOT exist
        composeRule.setContent {
            ChandTheme(language = AppLanguage.EN) {
                MarketFeedScreen(
                    uiState = MarketUiState.Ready(
                        assets = assets,
                        lastUpdatedMillis = now,
                        sourceSummary = "TGJU",
                        freshness = DataFreshness.LIVE,
                        isRefreshing = false
                    ),
                    filteredAssets = assets,
                    prefs = defaultPrefs,
                    strings = EnStrings,
                    selectedCategory = AssetCategory.ALL,
                    searchQuery = "",
                    onSelectCategory = {},
                    onSearchQueryChange = {},
                    onTogglePin = {},
                    onToggleGridView = {},
                    onAssetClick = {},
                    onRefreshClick = {},
                    referenceNowMillis = now
                )
            }
        }
        composeRule.onNodeWithTag("support_development_card").assertDoesNotExist()
    }

    @Test
    fun `support development card renders when showSupportCard is true and launches GitHub link`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).clearNextStartedActivities()

        val now = 1_750_000_000_000L
        val assets = listOf(sampleUsdAsset(now))
        val prefs = AppPreferences(
            language = AppLanguage.EN,
            usePersianDigits = false,
            isGridView = false,
            showSupportCard = true
        )

        composeRule.setContent {
            ChandTheme(language = AppLanguage.EN) {
                MarketFeedScreen(
                    uiState = MarketUiState.Ready(
                        assets = assets,
                        lastUpdatedMillis = now,
                        sourceSummary = "TGJU",
                        freshness = DataFreshness.LIVE,
                        isRefreshing = false
                    ),
                    filteredAssets = assets,
                    prefs = prefs,
                    strings = EnStrings,
                    selectedCategory = AssetCategory.ALL,
                    searchQuery = "",
                    onSelectCategory = {},
                    onSearchQueryChange = {},
                    onTogglePin = {},
                    onToggleGridView = {},
                    onAssetClick = {},
                    onRefreshClick = {},
                    referenceNowMillis = now
                )
            }
        }

        composeRule.onNodeWithTag("support_development_card")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        val startedIntent = shadowOf(app).nextStartedActivity
        assertNotNull("Expected tapping Support Development card to fire Intent", startedIntent)
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        assertEquals(
            "https://github.com/thepoo7an/chand-iran-currency-gold-crypto",
            startedIntent.dataString
        )
    }

    @Test
    fun `HistoryNormalizer preserves sustained regime shifts and 15pct corroborated spikes while filtering isolated noise`() {
        val base = 1_750_000_000L

        // Sustained regime jump (> 20% step up that stays at new level)
        val regimeShift = listOf(
            AssetHistoryPoint(base, 100_000.0),
            AssetHistoryPoint(base + 3600, 100_500.0),
            AssetHistoryPoint(base + 7200, 125_000.0),
            AssetHistoryPoint(base + 10800, 125_400.0),
            AssetHistoryPoint(base + 14400, 125_100.0)
        )
        val shiftNormalized = HistoryNormalizer.normalizeWithStats(
            points = regimeShift,
            category = AssetCategory.FIAT,
            range = TimeRange.H24
        )
        assertEquals(5, shiftNormalized.points.size)
        assertEquals(0, shiftNormalized.removedSpikeCount)

        // Corroborated 15% transient spike is kept when secondary provider confirms move
        val spikedSeries = listOf(
            AssetHistoryPoint(base, 100_000.0),
            AssetHistoryPoint(base + 3600, 100_000.0),
            AssetHistoryPoint(base + 7200, 118_000.0), // +18% spike that reverts
            AssetHistoryPoint(base + 10800, 100_200.0)
        )
        val uncorroborated = HistoryNormalizer.normalizeWithStats(
            points = spikedSeries,
            category = AssetCategory.FIAT,
            range = TimeRange.H24
        )
        assertEquals(3, uncorroborated.points.size)
        assertEquals(1, uncorroborated.removedSpikeCount)

        val corroborated = HistoryNormalizer.normalizeWithStats(
            points = spikedSeries,
            category = AssetCategory.FIAT,
            range = TimeRange.H24,
            corroborating = listOf(AssetHistoryPoint(base + 7260, 117_000.0))
        )
        assertEquals(4, corroborated.points.size)
        assertEquals(0, corroborated.removedSpikeCount)
    }

    @Test
    fun `HistoryNormalizer avoids false gaps under 5 points, enforces range cutoff, and deduplicates deterministically`() {
        val nowSec = 1_750_000_000L
        // 4 sparse points spread across 18H -> must NOT produce gaps
        val sparse = listOf(
            AssetHistoryPoint(nowSec - 18 * 3600L, 110_000.0),
            AssetHistoryPoint(nowSec - 12 * 3600L, 111_000.0),
            AssetHistoryPoint(nowSec - 6 * 3600L, 112_000.0),
            AssetHistoryPoint(nowSec - 600L, 113_000.0)
        )
        val sparseSeries = HistoryNormalizer.prepareChartSeries(
            points = sparse,
            range = TimeRange.H24,
            nowSec = nowSec,
            applyRangeCutoff = true
        )
        assertFalse("Under 5 observations must never show false outage gaps", sparseSeries.hasGaps)
        assertEquals(1, sparseSeries.segments.size)

        // Cutoff drops points older than lookback + grace, keeps duplicates deterministic (latest wins)
        val mixed = listOf(
            AssetHistoryPoint(nowSec - 48 * 3600L, 99_000.0), // outside 24H + 1H grace
            AssetHistoryPoint(nowSec - 3600L, 114_000.0),
            AssetHistoryPoint(nowSec - 3600L, 114_500.0), // duplicate ts -> latest wins
            AssetHistoryPoint(nowSec - 60L, 115_000.0)
        )
        val clipped = HistoryNormalizer.prepareChartSeries(
            points = mixed,
            range = TimeRange.H24,
            nowSec = nowSec,
            applyRangeCutoff = true
        )
        assertEquals(listOf(114_500.0, 115_000.0), clipped.points.map { it.priceToman })
    }

    @Test
    @Config(sdk = [29])
    fun `lockscreen widget falls back to home_screen before Android 12 and SettingsSheet shows notice`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockXmlResId = context.resources.getIdentifier(
            "chand_widget_lockscreen_info",
            "xml",
            context.packageName
        )
        assertTrue(lockXmlResId != 0)
        val parser = context.resources.getXml(lockXmlResId)
        var widgetCategory: String? = null
        while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                parser.name == "appwidget-provider"
            ) {
                widgetCategory = parser.getAttributeValue(
                    "http://schemas.android.com/apk/res/android",
                    "widgetCategory"
                )
            }
        }
        assertEquals("home_screen", widgetCategory)

        composeRule.setContent {
            ChandTheme(language = AppLanguage.EN) {
                SettingsSheet(
                    prefs = AppPreferences(language = AppLanguage.EN),
                    strings = EnStrings,
                    updateState = AppUpdateState.Idle,
                    onSetThemeMode = {},
                    onSetLanguage = {},
                    onSetPersianDigits = {},
                    onSetIsGridView = {},
                    onSetRefreshInterval = {},
                    onSetWidgetTranslucent = {},
                    onSetWidgetOpacityPercent = {},
                    onSetWidgetHeroAsset = {},
                    onSetGithubRepoSlug = {},
                    onCheckForUpdates = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag("lock_widget_pre12_notice")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `lockscreen widget includes keyguard on Android 12+ and hides pre-12 notice`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockXmlResId = context.resources.getIdentifier(
            "chand_widget_lockscreen_info",
            "xml",
            context.packageName
        )
        assertTrue(lockXmlResId != 0)
        val parser = context.resources.getXml(lockXmlResId)
        var widgetCategory: String? = null
        while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                parser.name == "appwidget-provider"
            ) {
                widgetCategory = parser.getAttributeValue(
                    "http://schemas.android.com/apk/res/android",
                    "widgetCategory"
                )
            }
        }
        // AppWidgetProviderInfo widgetCategory bitmask: 1=home_screen, 2=keyguard => "keyguard|home_screen" or "3"
        assertTrue(
            "Expected keyguard in widgetCategory on API 36, got $widgetCategory",
            widgetCategory != null && (widgetCategory.contains("keyguard") || widgetCategory == "3" || widgetCategory == "0x3")
        )

        composeRule.setContent {
            ChandTheme(language = AppLanguage.EN) {
                SettingsSheet(
                    prefs = AppPreferences(language = AppLanguage.EN),
                    strings = EnStrings,
                    updateState = AppUpdateState.Idle,
                    onSetThemeMode = {},
                    onSetLanguage = {},
                    onSetPersianDigits = {},
                    onSetIsGridView = {},
                    onSetRefreshInterval = {},
                    onSetWidgetTranslucent = {},
                    onSetWidgetOpacityPercent = {},
                    onSetWidgetHeroAsset = {},
                    onSetGithubRepoSlug = {},
                    onCheckForUpdates = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag("lock_widget_pre12_notice").assertDoesNotExist()
    }

    @Test
    fun `CHANGELOG and release workflow align versioning and release notes`() {
        // Repo root is parent of app/ during Gradle tests
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val root = if (File(cwd, "CHANGELOG.md").exists()) cwd else cwd.parentFile
        val changelog = File(root, "CHANGELOG.md")
        assertTrue("CHANGELOG.md must exist at repo root", changelog.exists())
        val changelogText = changelog.readText()
        assertTrue(changelogText.contains("## [Unreleased]"))
        assertTrue(changelogText.contains("## [1.0.0]"))

        val workflow = File(root, ".github/workflows/release-apk.yml")
        assertTrue("release-apk.yml must exist", workflow.exists())
        val workflowText = workflow.readText()
        assertTrue(workflowText.contains("fetch-depth: 0"))
        assertTrue(workflowText.contains("git log"))
    }
}
