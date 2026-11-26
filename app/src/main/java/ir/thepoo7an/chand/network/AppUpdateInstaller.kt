package ir.thepoo7an.chand.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import ir.thepoo7an.chand.BuildConfig
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
        val errorCode: String,
        val reason: UpdateErrorReason = UpdateErrorReason.fromCode(errorCode)
    ) : AppUpdateDownloadState
}

enum class InstallLaunchResult {
    LAUNCHED_INSTALLER,
    NEEDS_UNKNOWN_SOURCES_PERMISSION,
    FILE_MISSING,
    CHECKSUM_MISMATCH,
    CORRUPTED_APK,
    WRONG_PACKAGE_NAME,
    OLDER_OR_EQUAL_VERSION,
    UNEXPECTED_SIGNER,
    FAILED;

    fun toUpdateErrorReason(): UpdateErrorReason? = when (this) {
        LAUNCHED_INSTALLER, NEEDS_UNKNOWN_SOURCES_PERMISSION -> null
        FILE_MISSING -> UpdateErrorReason.INCOMPLETE_DOWNLOAD
        CHECKSUM_MISMATCH -> UpdateErrorReason.CHECKSUM_MISMATCH
        CORRUPTED_APK -> UpdateErrorReason.CORRUPTED_APK
        WRONG_PACKAGE_NAME -> UpdateErrorReason.WRONG_PACKAGE_NAME
        OLDER_OR_EQUAL_VERSION -> UpdateErrorReason.OLDER_OR_EQUAL_VERSION
        UNEXPECTED_SIGNER -> UpdateErrorReason.UNEXPECTED_SIGNER
        FAILED -> UpdateErrorReason.INSTALL_FAILED
    }

    companion object {
        fun fromErrorReason(reason: UpdateErrorReason): InstallLaunchResult = when (reason) {
            UpdateErrorReason.CHECKSUM_MISMATCH -> CHECKSUM_MISMATCH
            UpdateErrorReason.CORRUPTED_APK,
            UpdateErrorReason.METADATA_INVALID,
            UpdateErrorReason.UNREASONABLE_FILE_SIZE -> CORRUPTED_APK
            UpdateErrorReason.INCOMPLETE_DOWNLOAD -> FILE_MISSING
            UpdateErrorReason.WRONG_PACKAGE_NAME -> WRONG_PACKAGE_NAME
            UpdateErrorReason.OLDER_OR_EQUAL_VERSION -> OLDER_OR_EQUAL_VERSION
            UpdateErrorReason.UNEXPECTED_SIGNER -> UNEXPECTED_SIGNER
            else -> FAILED
        }
    }
}

/**
 * Manages cached APK update storage under `cacheDir/updates/`, runs full pre-install
 * integrity, package, version, and signer verification, and safely launches Android's
 * system package installer via [FileProvider].
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

    fun findAlreadyDownloadedApk(
        context: Context,
        info: AppUpdateInfo,
        apkInspector: ApkPackageInspector = AndroidApkPackageInspector(context),
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        expectedReleaseCertSha256: String = BuildConfig.EXPECTED_RELEASE_CERT_SHA256
    ): File? {
        if (!info.hasVerifiedMetadata || !UpdateSecurityValidator.isValidSha256Hex(info.expectedSha256)) {
            return null
        }
        val file = targetApkFile(context, info)
        if (!file.exists()) return null
        return runCatching {
            UpdateSecurityValidator.verifyDownloadedApkForInstall(
                apkFile = file,
                expectedSha256 = info.expectedSha256,
                expectedSizeBytes = info.matchedApkSizeBytes,
                expectedPackageName = info.expectedPackageName,
                expectedVersionCode = info.latestVersionCode,
                currentVersionCode = currentVersionCode,
                expectedReleaseCertSha256 = expectedReleaseCertSha256,
                apkInspector = apkInspector
            )
            file
        }.getOrElse {
            runCatching { file.delete() }
            null
        }
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
        apkFile: File,
        info: AppUpdateInfo,
        apkInspector: ApkPackageInspector = AndroidApkPackageInspector(context),
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        expectedReleaseCertSha256: String = BuildConfig.EXPECTED_RELEASE_CERT_SHA256
    ): InstallLaunchResult {
        if (!apkFile.exists() || !apkFile.isFile) {
            return InstallLaunchResult.FILE_MISSING
        }
        if (!info.hasVerifiedMetadata || !UpdateSecurityValidator.isValidSha256Hex(info.expectedSha256)) {
            runCatching { apkFile.delete() }
            return InstallLaunchResult.CORRUPTED_APK
        }
        try {
            UpdateSecurityValidator.verifyDownloadedApkForInstall(
                apkFile = apkFile,
                expectedSha256 = info.expectedSha256,
                expectedSizeBytes = info.matchedApkSizeBytes,
                expectedPackageName = info.expectedPackageName,
                expectedVersionCode = info.latestVersionCode,
                currentVersionCode = currentVersionCode,
                expectedReleaseCertSha256 = expectedReleaseCertSha256,
                apkInspector = apkInspector
            )
        } catch (e: AppUpdateException) {
            runCatching { apkFile.delete() }
            return InstallLaunchResult.fromErrorReason(e.reason)
        } catch (_: Throwable) {
            runCatching { apkFile.delete() }
            return InstallLaunchResult.CORRUPTED_APK
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
