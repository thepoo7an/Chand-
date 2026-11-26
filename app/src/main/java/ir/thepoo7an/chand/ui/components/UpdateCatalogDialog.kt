package ir.thepoo7an.chand.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.network.AppUpdateDownloadState
import ir.thepoo7an.chand.network.AppUpdateInfo
import ir.thepoo7an.chand.network.AppUpdateInstaller
import ir.thepoo7an.chand.network.AppUpdateService
import ir.thepoo7an.chand.network.UpdateSecurityValidator
import ir.thepoo7an.chand.ui.strings.ChandStrings
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.ui.theme.LocalMarketColors
import java.io.File
import java.util.Locale

@Composable
fun UpdateCatalogDialog(
    info: AppUpdateInfo,
    downloadState: AppUpdateDownloadState,
    prefs: AppPreferences,
    strings: ChandStrings,
    onStartDownload: (AppUpdateInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallApk: (File, AppUpdateInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val marketColors = LocalMarketColors.current
    val changelogSections = remember(info.releaseNotes) {
        AppUpdateService.parseReleaseNotes(info.releaseNotes)
    }
    val publishedDateLabel = remember(info.publishedAtIso, prefs.usePersianDigits) {
        val rawDate = info.publishedAtIso.takeIf { it.length >= 10 }?.substring(0, 10)
        rawDate?.let { NumberFormatters.applyDigits(it, prefs.usePersianDigits) }
    }
    val apkSizeLabel = remember(info.matchedApkSizeBytes, prefs.usePersianDigits) {
        formatApkBytes(info.matchedApkSizeBytes, prefs.usePersianDigits)
    }
    val canInstallApps = AppUpdateInstaller.canRequestPackageInstalls(context)

    val isDownloadingThis = downloadState is AppUpdateDownloadState.Downloading &&
        downloadState.info.latestVersionName == info.latestVersionName
    val readyThis = (downloadState as? AppUpdateDownloadState.ReadyToInstall)?.takeIf {
        it.info.latestVersionName == info.latestVersionName
    }
    val errorThis = (downloadState as? AppUpdateDownloadState.Error)?.takeIf {
        it.info.latestVersionName == info.latestVersionName
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = if (marketColors.isAmoled) 0.dp else 8.dp,
            border = BorderStroke(1.dp, marketColors.hairline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 520.dp)
                .testTag("update_catalog_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                            marketColors.gain.copy(alpha = 0.22f)
                                        )
                                    )
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = strings.updateCatalogTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = info.releaseTitle.ifBlank { "v${info.latestVersionName}" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("update_catalog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = strings.laterButton,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Version Comparison & Package Info Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, marketColors.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = strings.currentVersionLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = NumberFormatters.applyDigits(
                                        "v${info.currentVersionName}",
                                        prefs.usePersianDigits
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = marketColors.gain.copy(alpha = 0.16f)
                            ) {
                                val latestTag = buildString {
                                    append("v")
                                    append(info.latestVersionName)
                                    info.latestVersionCode?.let { append(" • #$it") }
                                }
                                Text(
                                    text = NumberFormatters.applyDigits(latestTag, prefs.usePersianDigits),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = marketColors.gain,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = marketColors.hairline)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = info.matchedApkName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val metaText = buildList {
                                if (apkSizeLabel.isNotBlank()) add(apkSizeLabel)
                                if (!publishedDateLabel.isNullOrBlank()) {
                                    add("${strings.publishedAtPrefix} $publishedDateLabel")
                                }
                            }.joinToString(" • ")
                            if (metaText.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = metaText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Scrollable Changelog Card
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.changelogTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                        border = BorderStroke(1.dp, marketColors.hairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp, max = 250.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp)
                                .testTag("update_changelog_list"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (changelogSections.isEmpty()) {
                                Text(
                                    text = strings.changelogEmptyFallback,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                changelogSections.forEach { section ->
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (!section.title.isNullOrBlank()) {
                                            Text(
                                                text = section.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        section.items.forEach { item ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(top = 6.dp)
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(marketColors.gain)
                                                )
                                                Text(
                                                    text = item,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    lineHeight = 19.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // In-App Download / Install Section
                when {
                    isDownloadingThis -> {
                        val downloading = downloadState as AppUpdateDownloadState.Downloading
                        val targetProgress = downloading.progressFraction
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = strings.downloadingUpdateLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    downloading.progressPercent?.let { pct ->
                                        Text(
                                            text = NumberFormatters.applyDigits("$pct%", prefs.usePersianDigits),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (targetProgress != null) {
                                    val animatedProgress by animateFloatAsState(
                                        targetValue = targetProgress,
                                        label = "apk_download_progress"
                                    )
                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(99.dp))
                                            .testTag("update_download_progress")
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(99.dp))
                                            .testTag("update_download_progress")
                                    )
                                }

                                val downloadedStr = formatApkBytes(downloading.downloadedBytes, prefs.usePersianDigits)
                                val totalStr = formatApkBytes(downloading.totalBytes, prefs.usePersianDigits)
                                val byteCaption = if (totalStr.isNotBlank()) {
                                    "$downloadedStr / $totalStr"
                                } else {
                                    downloadedStr
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = byteCaption,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(
                                        onClick = onCancelDownload,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("cancel_download_button")
                                    ) {
                                        Text(strings.cancelDownloadButton, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    readyThis != null -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = marketColors.gain.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, marketColors.gain.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = marketColors.gain,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = strings.downloadCompleteLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = marketColors.gain
                                    )
                                }
                                if (!canInstallApps) {
                                    Text(
                                        text = strings.installPermissionHint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { onInstallApk(File(readyThis.apkFilePath), info) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("install_update_button")
                                ) {
                                    Icon(
                                        imageVector = if (canInstallApps) Icons.Outlined.InstallMobile else Icons.Outlined.Security,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (canInstallApps) strings.installUpdateButton else strings.grantInstallPermissionButton,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    errorThis != null -> {
                        val fallbackUrl = info.releasePageUrl
                            .takeIf { UpdateSecurityValidator.isSafeExternalHttpsUrl(it) }
                            ?: info.downloadUrl.takeIf { UpdateSecurityValidator.isSafeExternalHttpsUrl(it) }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = marketColors.loss.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, marketColors.loss.copy(alpha = 0.30f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ErrorOutline,
                                        contentDescription = null,
                                        tint = marketColors.loss,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(18.dp)
                                    )
                                    Text(
                                        text = strings.updateErrorMessage(errorThis.reason),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = marketColors.loss,
                                        modifier = Modifier.testTag("update_error_message")
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (info.hasDirectApkAsset) {
                                        Button(
                                            onClick = { onStartDownload(info) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                                .testTag("retry_download_button")
                                        ) {
                                            Text(strings.retryDownloadButton, fontSize = 12.sp, maxLines = 1)
                                        }
                                    }
                                    if (!fallbackUrl.isNullOrBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl))
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                        ) {
                                            Text(strings.openInBrowserFallback, fontSize = 12.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        val safeBrowserFallback = info.releasePageUrl
                            .takeIf { UpdateSecurityValidator.isSafeExternalHttpsUrl(it) }
                            ?: info.downloadUrl.takeIf { UpdateSecurityValidator.isSafeExternalHttpsUrl(it) }
                        if (info.hasDirectApkAsset) {
                            Button(
                                onClick = { onStartDownload(info) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("download_in_app_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.downloadUpdateButton,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (!safeBrowserFallback.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(safeBrowserFallback))
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("download_in_app_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.openInBrowserFallback,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Footer Secondary Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val safeReleasePage = info.releasePageUrl.takeIf {
                        UpdateSecurityValidator.isSafeExternalHttpsUrl(it)
                    }
                    if (!safeReleasePage.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(safeReleasePage))
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(strings.viewReleaseButton, fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("update_later_button")
                    ) {
                        Text(strings.laterButton)
                    }
                }
            }
        }
    }
}

internal fun formatApkBytes(bytes: Long, usePersianDigits: Boolean): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val raw = when {
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
    return NumberFormatters.applyDigits(raw, usePersianDigits)
}
