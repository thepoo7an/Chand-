package ir.thepoo7an.chand.network

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import androidx.core.content.pm.PackageInfoCompat
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import ir.thepoo7an.chand.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

enum class UpdateErrorReason(val code: String) {
    NETWORK_FAILURE("network_failure"),
    INVALID_URL("invalid_url"),
    HTTPS_VALIDATION_FAILED("https_validation_failed"),
    CHECKSUM_MISMATCH("checksum_mismatch"),
    METADATA_INVALID("metadata_invalid"),
    CORRUPTED_APK("corrupted_apk"),
    WRONG_PACKAGE_NAME("wrong_package_name"),
    OLDER_OR_EQUAL_VERSION("older_or_equal_version"),
    UNEXPECTED_SIGNER("unexpected_signer"),
    INSUFFICIENT_STORAGE("insufficient_storage"),
    UNREASONABLE_FILE_SIZE("unreasonable_file_size"),
    INCOMPLETE_DOWNLOAD("incomplete_download"),
    INSTALL_FAILED("install_failed");

    companion object {
        fun fromCode(code: String?): UpdateErrorReason =
            entries.firstOrNull { it.code == code } ?: NETWORK_FAILURE
    }
}

class AppUpdateException(
    val reason: UpdateErrorReason,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

@JsonClass(generateAdapter = true)
data class UpdateArtifactMetadataDto(
    val name: String = "",
    @Json(name = "package_name") val packageName: String = "",
    @Json(name = "version_code") val versionCode: Int = 0,
    @Json(name = "version_name") val versionName: String = "",
    @Json(name = "download_url") val downloadUrl: String = "",
    val sha256: String = "",
    @Json(name = "size_bytes") val sizeBytes: Long = 0L
)

@JsonClass(generateAdapter = true)
data class UpdateReleaseMetadataDto(
    @Json(name = "schema_version") val schemaVersion: Int = 1,
    @Json(name = "package_name") val packageName: String = "",
    @Json(name = "version_code") val versionCode: Int = 0,
    @Json(name = "version_name") val versionName: String = "",
    val artifacts: List<UpdateArtifactMetadataDto> = emptyList()
)

data class VerifiedApkDetails(
    val packageName: String,
    val versionCode: Long,
    val signerSha256Digests: Set<String>
)

fun interface ApkPackageInspector {
    fun inspect(apkFile: File): VerifiedApkDetails?
}

class AndroidApkPackageInspector(
    private val context: Context
) : ApkPackageInspector {
    override fun inspect(apkFile: File): VerifiedApkDetails? {
        if (!apkFile.exists() || !apkFile.isFile) return null
        val pm = context.packageManager
        val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_SIGNATURES
            )
        } ?: return null

        val pkgName = info.packageName.orEmpty().trim()
        if (pkgName.isBlank()) return null
        val versionCode = PackageInfoCompat.getLongVersionCode(info)

        val digests = extractSignerDigests(info)
        return VerifiedApkDetails(
            packageName = pkgName,
            versionCode = versionCode,
            signerSha256Digests = digests
        )
    }

    private fun extractSignerDigests(info: PackageInfo): Set<String> {
        val rawCerts: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            if (signingInfo != null) {
                val signatures = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                signatures?.map { it.toByteArray() }.orEmpty()
            } else {
                emptyList()
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.map { it.toByteArray() }.orEmpty()
        }

        return rawCerts
            .map { UpdateSecurityValidator.sha256Hex(it) }
            .filter { UpdateSecurityValidator.isValidSha256Hex(it) }
            .toSet()
    }
}

object UpdateSecurityValidator {
    const val EXPECTED_PACKAGE_NAME = "ir.thepoo7an.chand"
    const val ANDROID_DEBUG_CERT_SHA256 =
        "5c2230c64edf2eb08efffce72578f2e008e25447ebc649c924180058bbdcb38b"
    const val METADATA_PRIMARY_FILENAME = "chand-update-metadata.json"
    const val METADATA_FALLBACK_FILENAME = "update-metadata.json"
    const val MIN_APK_SIZE_BYTES = 512L
    const val MAX_APK_SIZE_BYTES = 200L * 1024L * 1024L
    const val MAX_REDIRECT_HOPS = 10
    private const val STORAGE_SAFETY_MARGIN_BYTES = 1024L * 1024L

    val DEFAULT_TRUSTED_HOSTS: Set<String> = setOf(
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
        "raw.githubusercontent.com",
        "cdn.jsdelivr.net"
    )

    private val SHA256_HEX_REGEX = Regex("^[0-9a-f]{64}$")
    private val APK_SIG_BLOCK_MAGIC = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)

    fun normalizeSha256(raw: String): String =
        raw.trim().lowercase(Locale.US).replace(":", "").replace(" ", "")

    fun isValidSha256Hex(raw: String): Boolean =
        SHA256_HEX_REGEX.matches(normalizeSha256(raw))

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(Locale.US, it) }

    fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buf)
                if (read < 0) break
                if (read > 0) digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
    }

    fun isHostTrusted(
        host: String,
        trustedHosts: Set<String> = DEFAULT_TRUSTED_HOSTS
    ): Boolean {
        val clean = host.trim().lowercase(Locale.US)
        if (clean.isBlank()) return false
        return clean in trustedHosts
    }

    fun isSafeExternalHttpsUrl(
        rawUrl: String,
        trustedHosts: Set<String> = DEFAULT_TRUSTED_HOSTS
    ): Boolean = runCatching {
        validateHttpsUrl(rawUrl, trustedHosts)
    }.isSuccess

    fun validateHttpsUrl(
        rawUrl: String,
        trustedHosts: Set<String> = DEFAULT_TRUSTED_HOSTS
    ): HttpUrl {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            throw AppUpdateException(UpdateErrorReason.INVALID_URL, "Blank update URL")
        }
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            throw AppUpdateException(
                UpdateErrorReason.HTTPS_VALIDATION_FAILED,
                "Insecure HTTP update URL rejected"
            )
        }
        val parsed = trimmed.toHttpUrlOrNull()
            ?: throw AppUpdateException(UpdateErrorReason.INVALID_URL, "Malformed update URL")

        if (!parsed.scheme.equals("https", ignoreCase = true)) {
            throw AppUpdateException(UpdateErrorReason.INVALID_URL, "Unsupported update URL scheme")
        }
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            throw AppUpdateException(UpdateErrorReason.INVALID_URL, "Credentials in URL rejected")
        }
        val host = parsed.host.trim().lowercase(Locale.US)
        if (host.isBlank() || !isHostTrusted(host, trustedHosts)) {
            throw AppUpdateException(
                UpdateErrorReason.INVALID_URL,
                "Untrusted update host: $host"
            )
        }
        return parsed
    }

    fun resolveRedirectUrl(
        currentUrl: HttpUrl,
        locationHeader: String?,
        trustedHosts: Set<String> = DEFAULT_TRUSTED_HOSTS
    ): HttpUrl {
        val location = locationHeader?.trim().orEmpty()
        if (location.isBlank()) {
            throw AppUpdateException(UpdateErrorReason.INVALID_URL, "Redirect missing Location header")
        }
        if (location.startsWith("http://", ignoreCase = true)) {
            throw AppUpdateException(
                UpdateErrorReason.HTTPS_VALIDATION_FAILED,
                "HTTPS-to-HTTP redirect downgrade rejected"
            )
        }
        val resolved = currentUrl.resolve(location)
            ?: throw AppUpdateException(UpdateErrorReason.INVALID_URL, "Invalid redirect Location URL")

        if (!resolved.isHttps) {
            val reason = if (resolved.scheme.equals("http", ignoreCase = true)) {
                UpdateErrorReason.HTTPS_VALIDATION_FAILED
            } else {
                UpdateErrorReason.INVALID_URL
            }
            throw AppUpdateException(reason, "Non-HTTPS redirect target rejected")
        }
        if (resolved.username.isNotEmpty() || resolved.password.isNotEmpty()) {
            throw AppUpdateException(UpdateErrorReason.INVALID_URL, "Redirect URL credentials rejected")
        }
        if (!isHostTrusted(resolved.host, trustedHosts)) {
            throw AppUpdateException(
                UpdateErrorReason.INVALID_URL,
                "Untrusted redirect host: ${resolved.host}"
            )
        }
        return resolved
    }

    fun isInsufficientStorageCause(t: Throwable): Boolean {
        var cur: Throwable? = t
        while (cur != null) {
            val msg = cur.message?.lowercase(Locale.US).orEmpty()
            if ("no space" in msg || "enospc" in msg || "disk full" in msg) {
                return true
            }
            cur = cur.cause
        }
        return false
    }

    fun checkAvailableStorage(targetDir: File?, requiredBytes: Long) {
        if (requiredBytes <= 0L || targetDir == null) return
        val probeDir = if (targetDir.exists()) targetDir else targetDir.parentFile ?: return
        val availableBytes = runCatching {
            val stat = StatFs(probeDir.absolutePath)
            stat.availableBytes
        }.getOrElse {
            probeDir.usableSpace
        }
        if (availableBytes > 0L && availableBytes < requiredBytes + STORAGE_SAFETY_MARGIN_BYTES) {
            throw AppUpdateException(
                UpdateErrorReason.INSUFFICIENT_STORAGE,
                "Insufficient storage ($availableBytes available, need $requiredBytes)"
            )
        }
    }

    fun validateAndBindArtifactMetadata(
        metadata: UpdateReleaseMetadataDto,
        expectedApkName: String,
        expectedDownloadUrl: String,
        expectedAssetSizeBytes: Long,
        expectedReleaseBuildCode: Int?,
        currentVersionCode: Int,
        trustedHosts: Set<String> = DEFAULT_TRUSTED_HOSTS
    ): UpdateArtifactMetadataDto {
        if (metadata.schemaVersion != 1) {
            throw AppUpdateException(
                UpdateErrorReason.METADATA_INVALID,
                "Unsupported update metadata schema version: ${metadata.schemaVersion}"
            )
        }
        if (metadata.packageName.trim() != EXPECTED_PACKAGE_NAME) {
            throw AppUpdateException(
                UpdateErrorReason.METADATA_INVALID,
                "Metadata package mismatch: ${metadata.packageName}"
            )
        }
        if (metadata.versionCode <= 0) {
            throw AppUpdateException(
                UpdateErrorReason.METADATA_INVALID,
                "Metadata missing positive version_code"
            )
        }
        if (expectedReleaseBuildCode != null &&
            expectedReleaseBuildCode > 0 &&
            metadata.versionCode != expectedReleaseBuildCode
        ) {
            throw AppUpdateException(
                UpdateErrorReason.METADATA_INVALID,
                "Metadata version_code ${metadata.versionCode} mismatches release build $expectedReleaseBuildCode"
            )
        }
        if (metadata.versionCode <= currentVersionCode) {
            throw AppUpdateException(
                UpdateErrorReason.OLDER_OR_EQUAL_VERSION,
                "Update version ${metadata.versionCode} is not newer than $currentVersionCode"
            )
        }

        val artifact = metadata.artifacts.firstOrNull {
            it.name.trim().equals(expectedApkName.trim(), ignoreCase = true)
        } ?: throw AppUpdateException(
            UpdateErrorReason.METADATA_INVALID,
            "Metadata missing artifact entry for $expectedApkName"
        )

        if (artifact.packageName.trim() != EXPECTED_PACKAGE_NAME) {
            throw AppUpdateException(
                UpdateErrorReason.METADATA_INVALID,
                "Artifact package mismatch: ${artifact.packageName}"
            )
        }
        if (artifact.versionCode != metadata.versionCode) {
            throw AppUpdateException(
                UpdateErrorReason.METADATA_INVALID,
                "Artifact version_code ${artifact.versionCode} mismatches release ${metadata.versionCode}"
            )
        }

        val artifactUrl = validateHttpsUrl(artifact.downloadUrl, trustedHosts).toString()
        val assetUrl = validateHttpsUrl(expectedDownloadUrl, trustedHosts).toString()
        if (artifactUrl != assetUrl) {
            throw AppUpdateException(
                UpdateErrorReason.METADATA_INVALID,
                "Artifact download URL does not match release asset URL"
            )
        }

        val normalizedSha = normalizeSha256(artifact.sha256)
        if (!isValidSha256Hex(normalizedSha)) {
            throw AppUpdateException(
                UpdateErrorReason.METADATA_INVALID,
                "Artifact sha256 is missing or malformed"
            )
        }
        if (artifact.sizeBytes < MIN_APK_SIZE_BYTES || artifact.sizeBytes > MAX_APK_SIZE_BYTES) {
            throw AppUpdateException(
                UpdateErrorReason.UNREASONABLE_FILE_SIZE,
                "Artifact size out of bounds: ${artifact.sizeBytes}"
            )
        }
        if (expectedAssetSizeBytes > 0L && artifact.sizeBytes != expectedAssetSizeBytes) {
            throw AppUpdateException(
                UpdateErrorReason.METADATA_INVALID,
                "Artifact size ${artifact.sizeBytes} mismatches GitHub asset size $expectedAssetSizeBytes"
            )
        }

        return artifact.copy(
            downloadUrl = artifactUrl,
            sha256 = normalizedSha
        )
    }

    /**
     * Validates real ZIP central directory, entry path safety, required Android APK entries
     * (`AndroidManifest.xml` and `classes*.dex`), and readable compressed payloads.
     */
    fun verifyApkZipStructure(file: File) {
        if (!file.exists() || !file.isFile || file.length() < MIN_APK_SIZE_BYTES) {
            throw AppUpdateException(UpdateErrorReason.CORRUPTED_APK, "APK file missing or too small")
        }
        if (file.length() > MAX_APK_SIZE_BYTES) {
            throw AppUpdateException(UpdateErrorReason.UNREASONABLE_FILE_SIZE, "APK exceeds max size")
        }

        var hasManifest = false
        var hasDex = false
        var hasV1Signature = false
        var hasV1Manifest = false

        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                var entryCount = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount++
                    val name = entry.name
                    if (name.startsWith("/") || name.contains("../") || name == "..") {
                        throw AppUpdateException(
                            UpdateErrorReason.CORRUPTED_APK,
                            "Unsafe ZIP entry path: $name"
                        )
                    }
                    when {
                        name == "AndroidManifest.xml" && !entry.isDirectory -> {
                            hasManifest = true
                            // Read entry bytes to verify ZIP stream & CRC integrity
                            zip.getInputStream(entry).use { stream ->
                                val buf = ByteArray(1024)
                                var total = 0
                                while (true) {
                                    val r = stream.read(buf)
                                    if (r < 0) break
                                    total += r
                                }
                                if (total <= 0) {
                                    throw AppUpdateException(
                                        UpdateErrorReason.CORRUPTED_APK,
                                        "Empty AndroidManifest.xml"
                                    )
                                }
                            }
                        }
                        Regex("""^classes\d*\.dex$""").matches(name) && !entry.isDirectory -> {
                            zip.getInputStream(entry).use { stream ->
                                val firstByte = stream.read()
                                if (firstByte < 0) {
                                    throw AppUpdateException(
                                        UpdateErrorReason.CORRUPTED_APK,
                                        "Empty DEX entry: $name"
                                    )
                                }
                            }
                            hasDex = true
                        }
                        name == "META-INF/MANIFEST.MF" && !entry.isDirectory -> {
                            hasV1Manifest = true
                        }
                        name.startsWith("META-INF/") &&
                            (name.endsWith(".RSA", ignoreCase = true) ||
                                name.endsWith(".DSA", ignoreCase = true) ||
                                name.endsWith(".EC", ignoreCase = true)) -> {
                            hasV1Signature = true
                        }
                    }
                }
                if (entryCount == 0 || !hasManifest || !hasDex) {
                    throw AppUpdateException(
                        UpdateErrorReason.CORRUPTED_APK,
                        "Archive is missing required AndroidManifest.xml or classes.dex"
                    )
                }
            }
        } catch (e: AppUpdateException) {
            throw e
        } catch (t: Throwable) {
            throw AppUpdateException(
                UpdateErrorReason.CORRUPTED_APK,
                "Invalid or corrupted APK archive",
                t
            )
        }

        val hasV2OrV3SigBlock = hasApkSigningBlockMagic(file)
        if (!hasV2OrV3SigBlock && !(hasV1Manifest && hasV1Signature)) {
            throw AppUpdateException(
                UpdateErrorReason.UNEXPECTED_SIGNER,
                "APK has no valid v1 or v2/v3 signing block"
            )
        }
    }

    /**
     * Checks whether the APK contains the standard Android APK Signing Block v2/v3 magic
     * (`"APK Sig Block 42"`) immediately preceding the ZIP Central Directory.
     */
    fun hasApkSigningBlockMagic(file: File): Boolean {
        val len = file.length()
        if (len < 64L) return false
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                // Locate End of Central Directory (EOCD: 0x06054b50) near tail
                val searchLen = minOf(len, 65_557L).toInt()
                val tailOffset = len - searchLen
                raf.seek(tailOffset)
                val tail = ByteArray(searchLen)
                raf.readFully(tail)

                var eocdPos = -1
                for (i in searchLen - 22 downTo 0) {
                    if (tail[i] == 0x50.toByte() &&
                        tail[i + 1] == 0x4B.toByte() &&
                        tail[i + 2] == 0x05.toByte() &&
                        tail[i + 3] == 0x06.toByte()
                    ) {
                        val commentLen = ((tail[i + 20].toInt() and 0xFF) or
                            ((tail[i + 21].toInt() and 0xFF) shl 8))
                        if (i + 22 + commentLen == searchLen) {
                            eocdPos = i
                            break
                        }
                    }
                }
                if (eocdPos < 0) return@use false

                val cdOffset = ByteBuffer.wrap(tail, eocdPos + 16, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int
                    .toLong() and 0xFFFFFFFFL
                if (cdOffset < 32L || cdOffset > tailOffset + eocdPos) return@use false

                raf.seek(cdOffset - 16L)
                val magic = ByteArray(16)
                raf.readFully(magic)
                magic.contentEquals(APK_SIG_BLOCK_MAGIC)
            }
        }.getOrDefault(false)
    }

    fun verifyDownloadedApkForInstall(
        apkFile: File,
        expectedSha256: String,
        expectedSizeBytes: Long,
        expectedPackageName: String = EXPECTED_PACKAGE_NAME,
        expectedVersionCode: Int? = null,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        expectedReleaseCertSha256: String = BuildConfig.EXPECTED_RELEASE_CERT_SHA256,
        apkInspector: ApkPackageInspector
    ): VerifiedApkDetails {
        if (!apkFile.exists() || !apkFile.isFile) {
            throw AppUpdateException(UpdateErrorReason.CORRUPTED_APK, "APK file missing")
        }
        val actualSize = apkFile.length()
        if (actualSize < MIN_APK_SIZE_BYTES) {
            throw AppUpdateException(UpdateErrorReason.INCOMPLETE_DOWNLOAD, "Downloaded APK is empty or truncated")
        }
        if (actualSize > MAX_APK_SIZE_BYTES) {
            throw AppUpdateException(UpdateErrorReason.UNREASONABLE_FILE_SIZE, "Downloaded APK exceeds max size")
        }
        if (expectedSizeBytes > 0L && actualSize != expectedSizeBytes) {
            throw AppUpdateException(
                UpdateErrorReason.INCOMPLETE_DOWNLOAD,
                "Downloaded APK size ($actualSize) mismatches expected ($expectedSizeBytes)"
            )
        }

        val normalizedExpectedSha = normalizeSha256(expectedSha256)
        if (!isValidSha256Hex(normalizedExpectedSha)) {
            throw AppUpdateException(
                UpdateErrorReason.METADATA_INVALID,
                "Trusted SHA-256 checksum missing for update verification"
            )
        }
        val actualSha = computeFileSha256(apkFile)
        if (actualSha != normalizedExpectedSha) {
            throw AppUpdateException(
                UpdateErrorReason.CHECKSUM_MISMATCH,
                "APK SHA-256 mismatch (expected $normalizedExpectedSha, got $actualSha)"
            )
        }

        verifyApkZipStructure(apkFile)

        val details = apkInspector.inspect(apkFile)
            ?: throw AppUpdateException(
                UpdateErrorReason.CORRUPTED_APK,
                "Could not parse AndroidManifest or package info from APK"
            )

        if (expectedPackageName != EXPECTED_PACKAGE_NAME || details.packageName != EXPECTED_PACKAGE_NAME) {
            throw AppUpdateException(
                UpdateErrorReason.WRONG_PACKAGE_NAME,
                "Unexpected APK package name: ${details.packageName}"
            )
        }

        if (details.versionCode <= currentVersionCode.toLong()) {
            throw AppUpdateException(
                UpdateErrorReason.OLDER_OR_EQUAL_VERSION,
                "APK versionCode ${details.versionCode} is not newer than installed $currentVersionCode"
            )
        }
        if (expectedVersionCode != null &&
            expectedVersionCode > 0 &&
            details.versionCode != expectedVersionCode.toLong()
        ) {
            throw AppUpdateException(
                UpdateErrorReason.METADATA_INVALID,
                "APK versionCode ${details.versionCode} mismatches metadata $expectedVersionCode"
            )
        }

        val expectedCert = normalizeSha256(expectedReleaseCertSha256)
        if (!isValidSha256Hex(expectedCert) || expectedCert == ANDROID_DEBUG_CERT_SHA256) {
            throw AppUpdateException(
                UpdateErrorReason.UNEXPECTED_SIGNER,
                "Trusted production release certificate is not configured"
            )
        }

        val digests = details.signerSha256Digests.map(::normalizeSha256).toSet()
        if (digests.isEmpty() ||
            ANDROID_DEBUG_CERT_SHA256 in digests ||
            digests.any { it != expectedCert }
        ) {
            throw AppUpdateException(
                UpdateErrorReason.UNEXPECTED_SIGNER,
                "APK signing certificate does not match expected production release certificate"
            )
        }

        return details
    }
}
