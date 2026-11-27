package ir.thepoo7an.chand.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ir.thepoo7an.chand.alerts.PriceAlertNotifier
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.PriceAlert
import ir.thepoo7an.chand.model.ThemeMode
import ir.thepoo7an.chand.ui.components.AssetBadge
import ir.thepoo7an.chand.ui.strings.ChandStrings
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.ui.theme.LightBg
import ir.thepoo7an.chand.ui.theme.LightBgBottom
import ir.thepoo7an.chand.ui.theme.LocalMarketColors
import kotlinx.coroutines.launch

/** Outcome of the manual "Test alert" action, surfaced honestly in the UI. */
private enum class AlertTestNotice { SENT, FAILED }

@Composable
fun AlertsScreen(
    alerts: List<PriceAlert>,
    availableAssets: List<MarketAsset>,
    prefs: AppPreferences,
    strings: ChandStrings,
    initialAssetIdForCreation: String? = null,
    notificationsEnabledOverride: Boolean? = null,
    onConsumeInitialAssetId: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onSaveAlert: (existingId: String?, assetId: String, upperToman: Double?, lowerToman: Double?, isEnabled: Boolean) -> Unit = { _, _, _, _, _ -> },
    onToggleAlertEnabled: (String) -> Unit = {},
    onRearmAlert: (String) -> Unit = {},
    onDeleteAlert: (String) -> Unit = {},
    onSendTestAlert: suspend () -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val marketColors = LocalMarketColors.current
    val isFa = prefs.language == AppLanguage.FA
    val tomanAssets = remember(availableAssets) {
        availableAssets.filter { !it.isUsdPerBarrel && it.priceToman > 0.0 }
    }
    val assetMap = remember(availableAssets) { availableAssets.associateBy { it.id } }

    var editingAlert by remember { mutableStateOf<PriceAlert?>(null) }
    var showEditorSheet by remember { mutableStateOf(false) }
    var preselectedAssetId by remember { mutableStateOf("USD") }
    var testNotice by remember { mutableStateOf<AlertTestNotice?>(null) }

    // Mirrors the real OS state so the permission dialog / settings round-trip is reflected
    // without recomposing the whole screen.
    var notificationsEnabled by remember(context, notificationsEnabledOverride) {
        mutableStateOf(
            notificationsEnabledOverride ?: PriceAlertNotifier.areNotificationsEnabled(context)
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (notificationsEnabledOverride == null) {
            notificationsEnabled = PriceAlertNotifier.areNotificationsEnabled(context)
        }
    }

    LaunchedEffect(initialAssetIdForCreation) {
        if (!initialAssetIdForCreation.isNullOrBlank()) {
            preselectedAssetId = initialAssetIdForCreation
            editingAlert = null
            showEditorSheet = true
            onConsumeInitialAssetId()
        }
    }

    val bgBrush = when (prefs.themeMode) {
        ThemeMode.LIGHT -> Brush.verticalGradient(listOf(LightBg, LightBgBottom))
        ThemeMode.DARK -> Brush.verticalGradient(listOf(Color(0xFF0E131B), Color(0xFF111824)))
        ThemeMode.AMOLED -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(bgBrush) }
            .testTag("alerts_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("alerts_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = strings.backAction,
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.alertsTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = strings.alertsSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action Strip: Test Alert + Add Alert
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val delivered = onSendTestAlert()
                            notificationsEnabled = notificationsEnabledOverride
                                ?: PriceAlertNotifier.areNotificationsEnabled(context)
                            testNotice = if (delivered) {
                                AlertTestNotice.SENT
                            } else {
                                AlertTestNotice.FAILED
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("test_alert_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.alertTestButton,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = {
                        editingAlert = null
                        preselectedAssetId = tomanAssets.firstOrNull()?.id ?: "USD"
                        showEditorSheet = true
                        // Ask for POST_NOTIFICATIONS up-front so a freshly created alert can
                        // actually be delivered instead of failing silently in the background.
                        if (!notificationsEnabled) {
                            PriceAlertNotifier.ensureNotificationsAllowed(context)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("add_alert_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.addAlertButton,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            testNotice?.let { notice ->
                val delivered = notice == AlertTestNotice.SENT
                val accent = if (delivered) marketColors.gain else marketColors.loss
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (delivered) marketColors.gainContainer else marketColors.lossContainer,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag(
                            if (delivered) "alert_test_sent_banner" else "alert_test_failed_banner"
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (delivered) {
                                Icons.Outlined.CheckCircle
                            } else {
                                Icons.Outlined.NotificationsOff
                            },
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (delivered) {
                                strings.alertTestSentNotice
                            } else {
                                strings.alertTestFailedNotice
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (!notificationsEnabled) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = marketColors.gold.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, marketColors.gold.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { PriceAlertNotifier.ensureNotificationsAllowed(context) }
                        .testTag("alerts_notifications_disabled_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsOff,
                            contentDescription = null,
                            tint = marketColors.gold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = strings.alertNotificationsDisabledBanner,
                            style = MaterialTheme.typography.labelSmall,
                            color = marketColors.gold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = strings.alertEnableNotificationsAction,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = marketColors.gold,
                            modifier = Modifier.testTag("alert_enable_notifications_action")
                        )
                    }
                }
            }

            if (alerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("alerts_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = strings.alertsEmptyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.alertsEmptySubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(alerts, key = { it.id }) { alert ->
                        PriceAlertCard(
                            alert = alert,
                            asset = assetMap[alert.assetId],
                            isFa = isFa,
                            usePersianDigits = prefs.usePersianDigits,
                            strings = strings,
                            onToggleEnabled = { onToggleAlertEnabled(alert.id) },
                            onRearm = { onRearmAlert(alert.id) },
                            onEdit = {
                                editingAlert = alert
                                preselectedAssetId = alert.assetId
                                showEditorSheet = true
                            },
                            onDelete = { onDeleteAlert(alert.id) }
                        )
                    }
                }
            }
        }
    }

    if (showEditorSheet) {
        PriceAlertEditorSheet(
            existingAlert = editingAlert,
            defaultAssetId = preselectedAssetId,
            assets = tomanAssets.ifEmpty { availableAssets.filter { !it.isUsdPerBarrel } },
            isFa = isFa,
            usePersianDigits = prefs.usePersianDigits,
            strings = strings,
            onSave = { assetId, upper, lower ->
                onSaveAlert(
                    editingAlert?.id,
                    assetId,
                    upper,
                    lower,
                    editingAlert?.isEnabled ?: true
                )
                showEditorSheet = false
            },
            onDismiss = { showEditorSheet = false }
        )
    }
}

@Composable
private fun PriceAlertCard(
    alert: PriceAlert,
    asset: MarketAsset?,
    isFa: Boolean,
    usePersianDigits: Boolean,
    strings: ChandStrings,
    onToggleEnabled: () -> Unit,
    onRearm: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val marketColors = LocalMarketColors.current
    val statusBg = when {
        !alert.isEnabled -> MaterialTheme.colorScheme.surfaceVariant
        alert.isTriggered -> marketColors.gold.copy(alpha = 0.18f)
        else -> marketColors.gainContainer
    }
    val statusFg = when {
        !alert.isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        alert.isTriggered -> marketColors.gold
        else -> marketColors.gain
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (alert.isTriggered) marketColors.gold.copy(alpha = 0.45f) else marketColors.hairline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alert_card_${alert.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (asset != null) {
                    AssetBadge(
                        assetId = asset.id,
                        category = asset.category,
                        fallbackText = asset.badgeText,
                        accentHex = asset.accentHex,
                        size = 38.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = asset?.let { if (isFa) it.nameFa else it.nameEn } ?: alert.assetId,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (asset != null && asset.priceToman > 0.0) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "${asset.symbol} · ${NumberFormatters.formatToman(asset.priceToman, usePersianDigits)} ${strings.tomanUnit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg
                ) {
                    Text(
                        text = if (alert.isTriggered) strings.alertTriggeredStatus else strings.alertArmedStatus,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusFg,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("alert_status_${alert.id}")
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = alert.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.testTag("alert_toggle_${alert.id}")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                alert.upperBoundToman?.let { upper ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.alertUpperShort,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = NumberFormatters.formatToman(upper, usePersianDigits),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                alert.lowerBoundToman?.let { lower ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.alertLowerShort,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = NumberFormatters.formatToman(lower, usePersianDigits),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (alert.isTriggered) {
                    TextButton(
                        onClick = onRearm,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("alert_rearm_${alert.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.alertRearmButton)
                    }
                }
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("alert_edit_${alert.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.editAlertTitle.substringBefore(" "))
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("alert_delete_${alert.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = marketColors.loss,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.alertDeleteButton, color = marketColors.loss)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceAlertEditorSheet(
    existingAlert: PriceAlert?,
    defaultAssetId: String,
    assets: List<MarketAsset>,
    isFa: Boolean,
    usePersianDigits: Boolean,
    strings: ChandStrings,
    onSave: (assetId: String, upperToman: Double?, lowerToman: Double?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val marketColors = LocalMarketColors.current
    var selectedAssetId by remember(existingAlert, defaultAssetId) {
        mutableStateOf(existingAlert?.assetId ?: defaultAssetId)
    }
    var upperText by remember(existingAlert) {
        mutableStateOf(
            existingAlert?.upperBoundToman?.let {
                if (it >= 1.0 && it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var lowerText by remember(existingAlert) {
        mutableStateOf(
            existingAlert?.lowerBoundToman?.let {
                if (it >= 1.0 && it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var showError by remember { mutableStateOf(false) }

    val selectedAsset = assets.firstOrNull { it.id == selectedAssetId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag("alert_editor_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (existingAlert == null) strings.createAlertTitle else strings.editAlertTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(assets, key = { it.id }) { asset ->
                    val selected = asset.id == selectedAssetId
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else marketColors.hairline
                        ),
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedAssetId = asset.id }
                            .testTag("alert_asset_${asset.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = asset.badgeText)
                            Text(
                                text = asset.symbol,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (selectedAsset != null && selectedAsset.priceToman > 0.0) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = "${selectedAsset.symbol} = ${NumberFormatters.formatToman(selectedAsset.priceToman, usePersianDigits)} ${strings.tomanUnit}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            OutlinedTextField(
                value = upperText,
                onValueChange = {
                    upperText = it
                    showError = false
                },
                label = { Text(strings.alertUpperBoundLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("alert_upper_input")
            )

            OutlinedTextField(
                value = lowerText,
                onValueChange = {
                    lowerText = it
                    showError = false
                },
                label = { Text(strings.alertLowerBoundLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("alert_lower_input")
            )

            Text(
                text = if (showError) strings.alertInvalidBoundsError else strings.alertBoundHint,
                style = MaterialTheme.typography.labelSmall,
                color = if (showError) marketColors.loss else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (showError) Modifier.testTag("alert_bounds_error") else Modifier
            )

            Button(
                onClick = {
                    val parsedUpper = parseBoundToman(upperText)
                    val parsedLower = parseBoundToman(lowerText)
                    val validUpperText = upperText.isBlank() || parsedUpper != null
                    val validLowerText = lowerText.isBlank() || parsedLower != null
                    val candidate = PriceAlert(
                        id = existingAlert?.id ?: "draft",
                        assetId = selectedAssetId,
                        upperBoundToman = parsedUpper,
                        lowerBoundToman = parsedLower
                    )
                    if (!validUpperText || !validLowerText || !candidate.hasValidBounds) {
                        showError = true
                    } else {
                        onSave(selectedAssetId, parsedUpper, parsedLower)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("alert_save_button")
            ) {
                Text(text = strings.alertSaveButton)
            }
        }
    }
}

private fun parseBoundToman(raw: String): Double? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val normalized = buildString(trimmed.length) {
        for (ch in trimmed) {
            when (ch) {
                '۰' -> append('0')
                '۱' -> append('1')
                '۲' -> append('2')
                '۳' -> append('3')
                '۴' -> append('4')
                '۵' -> append('5')
                '۶' -> append('6')
                '۷' -> append('7')
                '۸' -> append('8')
                '۹' -> append('9')
                ',', '٬', ' ' -> Unit
                '٫' -> append('.')
                else -> append(ch)
            }
        }
    }
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
}
