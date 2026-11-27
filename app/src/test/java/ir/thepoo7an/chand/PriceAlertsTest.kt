package ir.thepoo7an.chand

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.startup.AppInitializer
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkManagerInitializer
import ir.thepoo7an.chand.alerts.PriceAlertEvaluator
import ir.thepoo7an.chand.alerts.PriceAlertScheduler
import ir.thepoo7an.chand.alerts.PriceAlertWorker
import ir.thepoo7an.chand.data.PreferencesRepository
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.PriceAlert
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.ui.components.AssetDetailSheet
import ir.thepoo7an.chand.ui.screens.AlertsScreen
import ir.thepoo7an.chand.ui.strings.EnStrings
import ir.thepoo7an.chand.ui.theme.ChandTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class)
class PriceAlertsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun ensureWorkManager(context: Context): WorkManager {
        if (runCatching { WorkManager.getInstance(context) }.isFailure) {
            AppInitializer.getInstance(context)
                .initializeComponent(WorkManagerInitializer::class.java)
        }
        return WorkManager.getInstance(context)
    }

    private fun asset(
        id: String = "USD",
        priceToman: Double = 115_000.0,
        freshness: DataFreshness = DataFreshness.LIVE
    ) = MarketAsset(
        id = id,
        symbol = id,
        nameFa = "دلار آمریکا",
        nameEn = "US Dollar",
        category = AssetCategory.FIAT,
        badgeText = "🇺🇸",
        accentHex = 0xFF10B981,
        priceToman = priceToman,
        freshness = freshness,
        updatedAtMillis = 1_750_000_000_000L,
        fetchedAtMillis = 1_750_000_000_000L
    )

    @Test
    fun `PriceAlertEvaluator enforces once-per-crossing hysteresis auto rearm manual rearm and skips unavailable`() {
        val alert = PriceAlert(
            id = "a1",
            assetId = "USD",
            upperBoundToman = 120_000.0,
            lowerBoundToman = 110_000.0,
            isEnabled = true
        )

        // 1. Inside bounds -> no trigger
        val inside = PriceAlertEvaluator.evaluate(listOf(alert), listOf(asset(priceToman = 115_000.0)), nowMillis = 1000L)
        assertTrue(inside.triggeredMatches.isEmpty())
        assertFalse(inside.updatedAlerts.single().isTriggered)

        // 2. Crosses upper bound -> fires once
        val crossedUp = PriceAlertEvaluator.evaluate(inside.updatedAlerts, listOf(asset(priceToman = 121_000.0)), nowMillis = 2000L)
        assertEquals(1, crossedUp.triggeredMatches.size)
        assertTrue(crossedUp.triggeredMatches.single().matchedUpper)
        assertTrue(crossedUp.updatedAlerts.single().triggeredUpper)

        // 3. Stays above upper bound -> suppressed by hysteresis
        val stillHigh = PriceAlertEvaluator.evaluate(crossedUp.updatedAlerts, listOf(asset(priceToman = 122_000.0)), nowMillis = 3000L)
        assertTrue(stillHigh.triggeredMatches.isEmpty())
        assertTrue(stillHigh.updatedAlerts.single().triggeredUpper)

        // 4. UNAVAILABLE quote must NOT reset triggered state or fire lower
        val unavailable = PriceAlertEvaluator.evaluate(
            stillHigh.updatedAlerts,
            listOf(asset(priceToman = 0.0, freshness = DataFreshness.UNAVAILABLE)),
            nowMillis = 4000L
        )
        assertTrue(unavailable.triggeredMatches.isEmpty())
        assertTrue(unavailable.updatedAlerts.single().triggeredUpper)

        // 5. Returns strictly inside bounds -> auto re-arms
        val backInside = PriceAlertEvaluator.evaluate(unavailable.updatedAlerts, listOf(asset(priceToman = 116_000.0)), nowMillis = 5000L)
        assertTrue(backInside.triggeredMatches.isEmpty())
        assertFalse(backInside.updatedAlerts.single().triggeredUpper)

        // 6. Crosses lower bound -> fires lower, and manual rearm clears state
        val crossedLow = PriceAlertEvaluator.evaluate(backInside.updatedAlerts, listOf(asset(priceToman = 109_000.0)), nowMillis = 6000L)
        assertEquals(1, crossedLow.triggeredMatches.size)
        assertTrue(crossedLow.triggeredMatches.single().matchedLower)
        val rearmed = PriceAlertEvaluator.rearm(crossedLow.updatedAlerts.single())
        assertFalse(rearmed.isTriggered)
    }

    @Test
    fun `PreferencesRepository persists and reloads PriceAlert list via DataStore`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PreferencesRepository(context)
        val sample = listOf(
            PriceAlert(
                id = "usd_alert",
                assetId = "USD",
                upperBoundToman = 125_000.0,
                lowerBoundToman = 112_000.0,
                isEnabled = true,
                createdAtMillis = 123456L
            )
        )
        repo.savePriceAlerts(sample)
        val loaded = repo.getPriceAlerts()
        assertEquals(sample, loaded)
    }

    @Test
    fun `PriceAlertScheduler only schedules periodic work when at least one alert is enabled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wm = ensureWorkManager(context)

        val disabled = PriceAlert(
            id = "disabled",
            assetId = "USD",
            upperBoundToman = 120_000.0,
            isEnabled = false
        )
        PriceAlertScheduler.syncSchedule(context, listOf(disabled))
        val cancelledInfos = wm.getWorkInfosForUniqueWork(PriceAlertScheduler.WORK_NAME).get()
        assertTrue(
            "No active periodic work should be queued when all alerts disabled",
            cancelledInfos.none { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        )

        val enabled = disabled.copy(isEnabled = true)
        PriceAlertScheduler.syncSchedule(context, listOf(enabled))
        val activeInfos = wm.getWorkInfosForUniqueWork(PriceAlertScheduler.WORK_NAME).get()
        assertTrue("Periodic alert work should be ENQUEUED", activeInfos.any { it.state == WorkInfo.State.ENQUEUED })
        val workInfo = activeInfos.first { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(NetworkType.CONNECTED, workInfo.constraints.requiredNetworkType)
        assertEquals(
            PriceAlertScheduler.CHECK_INTERVAL_MINUTES * 60_000L,
            workInfo.periodicityInfo?.repeatIntervalMillis
        )

        // Disabling cancels periodic work
        PriceAlertScheduler.syncSchedule(context, emptyList())
        val afterCancel = wm.getWorkInfosForUniqueWork(PriceAlertScheduler.WORK_NAME).get()
        assertTrue(afterCancel.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `PriceAlertWorker completes cleanly when no enabled alerts are configured`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ensureWorkManager(context)
        val repo = PreferencesRepository(context)
        repo.savePriceAlerts(emptyList())

        val result = PriceAlertWorker.runAlertCheck(context)
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `AlertsScreen supports validation, creation, toggle, rearm, delete, and test alert`() {
        var savedCalls = 0
        var lastSavedUpper: Double? = null
        var toggledId: String? = null
        var rearmedId: String? = null
        var deletedId: String? = null
        var testAlertSent = false

        val triggeredAlert = PriceAlert(
            id = "a_gold",
            assetId = "GOLD_18K",
            upperBoundToman = 6_000_000.0,
            lowerBoundToman = null,
            isEnabled = true,
            triggeredUpper = true
        )
        val assets = listOf(
            asset("USD", 115_000.0),
            asset("GOLD_18K", 6_100_000.0)
        )

        composeRule.setContent {
            ChandTheme(language = AppLanguage.EN) {
                AlertsScreen(
                    alerts = listOf(triggeredAlert),
                    availableAssets = assets,
                    prefs = AppPreferences(language = AppLanguage.EN, usePersianDigits = false),
                    strings = EnStrings,
                    notificationsEnabledOverride = true,
                    onSaveAlert = { _, _, upper, _, _ ->
                        savedCalls++
                        lastSavedUpper = upper
                    },
                    onToggleAlertEnabled = { toggledId = it },
                    onRearmAlert = { rearmedId = it },
                    onDeleteAlert = { deletedId = it },
                    onSendTestAlert = {
                        testAlertSent = true
                        true
                    }
                )
            }
        }

        // Test alert triggers callback + banner
        composeRule.onNodeWithTag("test_alert_button").performClick()
        composeRule.onNodeWithTag("alert_test_sent_banner").assertIsDisplayed()
        assertTrue(testAlertSent)

        // Toggle, rearm, and delete existing triggered card
        composeRule.onNodeWithTag("alert_toggle_a_gold").performClick()
        assertEquals("a_gold", toggledId)
        composeRule.onNodeWithTag("alert_rearm_a_gold").performClick()
        assertEquals("a_gold", rearmedId)
        composeRule.onNodeWithTag("alert_delete_a_gold").performClick()
        assertEquals("a_gold", deletedId)

        // Open Add Alert sheet -> test invalid bounds -> test valid save
        composeRule.onNodeWithTag("add_alert_button").performClick()
        composeRule.onNodeWithTag("alert_editor_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("alert_upper_input").performTextInput("100000")
        composeRule.onNodeWithTag("alert_lower_input").performTextInput("120000")
        composeRule.onNodeWithTag("alert_save_button").performClick()
        composeRule.onNodeWithTag("alert_bounds_error").assertIsDisplayed()
        assertEquals(0, savedCalls)

        composeRule.onNodeWithTag("alert_lower_input").performTextInput("")
        // Replace invalid bounds using valid upper-only check by reopening
        composeRule.setContent {
            ChandTheme(language = AppLanguage.EN) {
                AlertsScreen(
                    alerts = emptyList(),
                    availableAssets = assets,
                    prefs = AppPreferences(language = AppLanguage.EN, usePersianDigits = false),
                    strings = EnStrings,
                    initialAssetIdForCreation = "USD",
                    notificationsEnabledOverride = true,
                    onSaveAlert = { _, _, upper, _, _ ->
                        savedCalls++
                        lastSavedUpper = upper
                    }
                )
            }
        }
        composeRule.onNodeWithTag("alerts_empty_state").assertIsDisplayed()
        composeRule.onNodeWithTag("alert_upper_input").performTextInput("125000")
        composeRule.onNodeWithTag("alert_save_button").performClick()
        assertEquals(1, savedCalls)
        assertEquals(125_000.0, lastSavedUpper)
    }

    @Test
    fun `AlertsScreen reports a failed test alert instead of pretending success`() {
        var attempts = 0

        composeRule.setContent {
            ChandTheme(language = AppLanguage.EN) {
                AlertsScreen(
                    alerts = emptyList(),
                    availableAssets = listOf(asset("USD", 115_000.0)),
                    prefs = AppPreferences(language = AppLanguage.EN, usePersianDigits = false),
                    strings = EnStrings,
                    notificationsEnabledOverride = true,
                    onSendTestAlert = {
                        attempts++
                        false
                    }
                )
            }
        }

        composeRule.onNodeWithTag("test_alert_button").performClick()
        composeRule.waitForIdle()

        // A notification that never reached the system must not be reported as sent.
        composeRule.onNodeWithTag("alert_test_failed_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("alert_test_sent_banner").assertDoesNotExist()
        composeRule.onNodeWithText(EnStrings.alertTestFailedNotice).assertIsDisplayed()
        assertEquals(1, attempts)
    }

    @Test
    fun `AssetDetailSheet exposes Set Alert action for TMN assets`() {
        var clicked = false
        composeRule.setContent {
            ChandTheme(language = AppLanguage.EN) {
                AssetDetailSheet(
                    asset = asset("USD", 115_000.0),
                    isPinned = false,
                    historyPoints = emptyList(),
                    historySourceLabel = "",
                    isHistoryLoading = false,
                    selectedRange = TimeRange.H24,
                    prefs = AppPreferences(language = AppLanguage.EN, usePersianDigits = false),
                    strings = EnStrings,
                    onSelectRange = {},
                    onRetryHistory = {},
                    onTogglePin = {},
                    onConvertClick = {},
                    onSetAlertClick = { clicked = true },
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag("detail_alert_button")
            .assertIsDisplayed()
            .performClick()
        assertTrue(clicked)
    }
}
