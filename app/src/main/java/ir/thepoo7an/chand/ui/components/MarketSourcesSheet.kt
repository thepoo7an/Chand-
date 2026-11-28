package ir.thepoo7an.chand.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.ui.strings.ChandStrings
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.ui.strings.ProviderLabels

/**
 * One provider that contributed to the feed.
 *
 * @param key stable tag suffix (the raw provider label slug)
 * @param assetCount how many visible assets this provider feeds
 * @param assetsLabel the first few asset names, so the sheet answers "which assets?" at a glance
 * @param lastFetchedMillis newest fetch timestamp seen for this provider
 */
data class ProviderSourceGroup(
    val key: String,
    val label: String,
    val assetCount: Int,
    val assetsLabel: String,
    val lastFetchedMillis: Long
)

/**
 * Turns the flat asset list into the provider list the "ℹ️" sheet shows: each provider once, with
 * when it was last fetched and which assets it feeds.
 */
object MarketSourcesSummary {

    private const val MAX_ASSETS_PER_ROW = 4

    fun build(
        assets: List<MarketAsset>,
        isFa: Boolean,
        strings: ChandStrings
    ): List<ProviderSourceGroup> = assets
        .filter { it.sourceLabel.isNotBlank() && it.priceToman > 0.0 }
        .groupBy { it.sourceLabel.trim() }
        .map { (rawLabel, fedAssets) ->
            val names = fedAssets
                .map { if (isFa) it.nameFa else it.nameEn }
                .distinct()
                .sorted()
            ProviderSourceGroup(
                key = ProviderLabels.short(rawLabel, isFa = false)
                    .lowercase()
                    .replace(Regex("[^a-z0-9]+"), "_")
                    .trim('_')
                    .ifEmpty { "provider" },
                label = ProviderLabels.short(rawLabel, isFa),
                assetCount = fedAssets.size,
                assetsLabel = if (names.size <= MAX_ASSETS_PER_ROW) {
                    names.joinToString(strings.sourcesListSeparator)
                } else {
                    names.take(MAX_ASSETS_PER_ROW).joinToString(strings.sourcesListSeparator) +
                        " " + strings.sourcesMoreCount(names.size - MAX_ASSETS_PER_ROW)
                },
                lastFetchedMillis = fedAssets.maxOf { asset ->
                    asset.fetchedAtMillis.takeIf { it > 0L } ?: asset.updatedAtMillis
                }
            )
        }
        .sortedWith(compareByDescending<ProviderSourceGroup> { it.assetCount }.thenBy { it.label })
        // Two spellings of the same provider can slug to the same key; LazyColumn keys must be unique.
        .let { groups ->
            val seen = mutableSetOf<String>()
            groups.map { group ->
                if (seen.add(group.key)) {
                    group
                } else {
                    var suffixed = "${group.key}_2"
                    var index = 2
                    while (!seen.add(suffixed)) {
                        index += 1
                        suffixed = "${group.key}_$index"
                    }
                    group.copy(key = suffixed)
                }
            }
        }

    /** Number of distinct providers behind the current feed, for the compact "Sources: N" chip. */
    fun providerCount(assets: List<MarketAsset>): Int =
        assets.map { it.sourceLabel.trim() }.filter { it.isNotEmpty() }.distinct().size
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketSourcesSheet(
    groups: List<ProviderSourceGroup>,
    prefs: AppPreferences,
    strings: ChandStrings,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isFa = prefs.language == AppLanguage.FA

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        MarketSourcesContent(
            groups = groups,
            isFa = isFa,
            usePersianDigits = prefs.usePersianDigits,
            strings = strings
        )
    }
}

@Composable
internal fun MarketSourcesContent(
    groups: List<ProviderSourceGroup>,
    isFa: Boolean,
    usePersianDigits: Boolean,
    strings: ChandStrings,
    modifier: Modifier = Modifier
) {
    val textAlign = if (isFa) TextAlign.End else TextAlign.Start
    val fetchedAt = remember(groups, usePersianDigits) {
        groups.associate { group ->
            group.key to NumberFormatters.formatTime(group.lastFetchedMillis, usePersianDigits)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("sources_sheet"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.sourcesSheetTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = textAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sources_sheet_title")
                )
                Text(
                    text = strings.sourcesSheetSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (groups.isEmpty()) {
            Text(
                text = strings.sourcesSheetEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .testTag("sources_sheet_empty")
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 860.dp),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(groups, key = { it.key }) { group ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("provider_row_${group.key}")
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            // Short deterministic label: never truncated, in either language.
                            text = group.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("provider_label_${group.key}")
                        )
                        Text(
                            text = "${strings.sourcesAssetsLabel}: ${group.assetsLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = textAlign,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = fetchedAt[group.key].orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.testTag("provider_fetched_${group.key}")
                    )
                }
            }
        }
    }
}
