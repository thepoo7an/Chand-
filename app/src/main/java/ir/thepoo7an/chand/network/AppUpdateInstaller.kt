package ir.thepoo7an.chand.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

sealed interface AppUpdateDownloadState {
    data object Idle : AppUpdateDownloadState

    data class Downloading(
        val info: AppUpdateInfo,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppUpdateDownloadState {
        val progressFraction: Float?
            get() = if (totalBytes > 0L) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }

        val progressPercent: Int?
            get() = progressFraction?.let { (it * 100f).toInt().coerceIn(0, 100) }
    }

    data class ReadyToInstall(
        val info: AppUpdateInfo,
        val apkFilePath: String,
        val fileSizeBytes: Long
    ) : AppUpdateDownloadState

    data class Error(
        val info: AppUpdateInfo,
        val errorCode: String
    ) : AppUpdateDownloadState
}

enum class InstallLaunchResult {
    LAUNCHED_INSTALLER,
    NEEDS_UNKNOWN_SOURCES_PERMISSION,
    FILE_MISSING,
    FAILED
}

/**
 * Manages cached APK update storage under `cacheDir/updates/` and safely launches the
 * system Android package installer via [FileProvider].
 */
object AppUpdateInstaller {

    private const val UPDATES_DIR_NAME = "updates"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    fun updatesDir(context: Context): File =
        File(context.cacheDir, UPDATES_DIR_NAME).apply { mkdirs() }

    fun targetApkFile(context: Context, info: AppUpdateInfo): File {
        val baseName = File(info.matchedApkName).name
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "Chand-update.apk" }
        val versionSuffix = info.latestVersionCode?.let { "b$it" }
            ?: info.latestVersionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = "${versionSuffix}_$baseName"
        return File(updatesDir(context), fileName)
    }

    fun findAlreadyDownloadedApk(context: Context, info: AppUpdateInfo): File? {
        val file = targetApkFile(context, info)
        if (!AppUpdateService.hasApkZipMagicHeader(file)) return null
        if (info.matchedApkSizeBytes > 0L && file.length() != info.matchedApkSizeBytes) {
            return null
        }
        return file
    }

    fun cleanupStaleApks(context: Context, keepFile: File? = null) {
        val dir = File(context.cacheDir, UPDATES_DIR_NAME)
        if (!dir.exists() || !dir.isDirectory) return
        val keepPath = keepFile?.absolutePath
        dir.listFiles()?.forEach { child ->
            if (child.absolutePath != keepPath) {
                runCatching { child.delete() }
            }
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun createUnknownSourcesSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun createInstallApkIntent(context: Context, apkFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun launchInstallOrRequestPermission(
        context: Context,
        apkFile: File
    ): InstallLaunchResult {
        if (!AppUpdateService.hasApkZipMagicHeader(apkFile)) {
            return InstallLaunchResult.FILE_MISSING
        }
        if (!canRequestPackageInstalls(context)) {
            return InstallLaunchResult.NEEDS_UNKNOWN_SOURCES_PERMISSION
        }
        return runCatching {
            context.startActivity(createInstallApkIntent(context, apkFile))
            InstallLaunchResult.LAUNCHED_INSTALLER
        }.getOrElse {
            InstallLaunchResult.FAILED
        }
    }
}
