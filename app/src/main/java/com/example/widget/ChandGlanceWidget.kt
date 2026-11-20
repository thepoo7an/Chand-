package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.data.PreferencesRepository
import com.example.model.AppLanguage
import com.example.model.AppPreferences
import com.example.model.MarketAsset
import com.example.network.MarketRemoteDataSource
import com.example.ui.strings.NumberFormatters
import com.example.model.CachedMarketSnapshot

class ChandGlanceWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT_SIZE = DpSize(140.dp, 110.dp)
        private val WIDE_SIZE = DpSize(250.dp, 110.dp)
        private val TALL_SIZE = DpSize(250.dp, 200.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_SIZE, WIDE_SIZE, TALL_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefsRepo = PreferencesRepository(context)
        val prefs = prefsRepo.getPreferences()
        val snapshot = prefsRepo.loadCachedSnapshot()

        val allAssets = snapshot?.assets.orEmpty()
        val pinned = allAssets.filter { it.id in prefs.pinnedIds }
        val displayAssets = (if (pinned.isNotEmpty()) pinned else allAssets)
            .take(4)

        provideContent {
            WidgetContent(
                assets = displayAssets,
                updatedAtMillis = snapshot?.updatedAtMillis ?: 0L,
                prefs = prefs
            )
        }
    }

    @Composable
    private fun WidgetContent(
        assets: List<MarketAsset>,
        updatedAtMillis: Long,
        prefs: AppPreferences
    ) {
        val size = LocalSize.current
        val isCompact = size.width < 200.dp
        val maxRows = when {
            size.height >= 180.dp -> 4
            isCompact -> 2
            else -> 3
        }
        val visibleAssets = assets.take(maxRows)
        val isFa = prefs.language == AppLanguage.FA

        val bgColor = ColorProvider(
            day = Color(0xFFFFFFFF),
            night = Color(0xFF0A0D12)
        )
        val rowBgColor = ColorProvider(
            day = Color(0xFFF3F5F8),
            night = Color(0xFF141922)
        )
        val titleColor = ColorProvider(
            day = Color(0xFF0B1220),
            night = Color(0xFFFFFFFF)
        )
        val subColor = ColorProvider(
            day = Color(0xFF5B6577),
            night = Color(0xFF8E9BAE)
        )
        val accentColor = ColorProvider(
            day = Color(0xFF0284C7),
            night = Color(0xFF22D3EE)
        )
        val gainColor = ColorProvider(
            day = Color(0xFF059669),
            night = Color(0xFF10B981)
        )
        val lossColor = ColorProvider(
            day = Color(0xFFDC2626),
            night = Color(0xFFEF4444)
        )

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bgColor)
                .cornerRadius(20.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isFa) "چند؟" else "Chand?",
                    style = TextStyle(
                        color = titleColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                if (updatedAtMillis > 0L && !isCompact) {
                    Text(
                        text = NumberFormatters.formatTime(updatedAtMillis, prefs.usePersianDigits),
                        style = TextStyle(color = subColor, fontSize = 11.sp)
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }
                Box(
                    modifier = GlanceModifier
                        .background(rowBgColor)
                        .cornerRadius(10.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable(actionRunCallback<RefreshWidgetAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↻",
                        style = TextStyle(
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (visibleAssets.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isFa) "برای دریافت نرخ لمس کنید" else "Tap to load live rates",
                        style = TextStyle(color = subColor, fontSize = 12.sp)
                    )
                }
            } else {
                Column(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    visibleAssets.forEachIndexed { index, asset ->
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(rowBgColor)
                                .cornerRadius(12.dp)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${asset.badgeText} ${asset.symbol}",
                                style = TextStyle(
                                    color = titleColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = GlanceModifier.defaultWeight())
                            Column(horizontalAlignment = Alignment.End) {
                                val priceStr = NumberFormatters.formatToman(
                                    asset.priceToman,
                                    prefs.usePersianDigits
                                )
                                Text(
                                    text = if (isCompact) priceStr else "$priceStr ${if (isFa) "ت" else "TMN"}",
                                    style = TextStyle(
                                        color = titleColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                if (!isCompact) {
                                    val chStr = NumberFormatters.formatPercent(
                                        asset.change24hPercent,
                                        prefs.usePersianDigits
                                    )
                                    Text(
                                        text = chStr,
                                        style = TextStyle(
                                            color = if (asset.change24hPercent >= 0) gainColor else lossColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                        if (index < visibleAssets.lastIndex) {
                            Spacer(modifier = GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val remote = MarketRemoteDataSource()
        val prefsRepo = PreferencesRepository(context)
        val res = remote.fetchLiveMarket()
        if (res.isLive && res.assets.isNotEmpty()) {
            prefsRepo.saveCachedSnapshot(
                CachedMarketSnapshot(
                    assets = res.assets,
                    updatedAtMillis = res.fetchedAtMillis,
                    sourceSummary = res.sourceSummary
                )
            )
        }
        ChandGlanceWidget().updateAll(context)
    }
}
