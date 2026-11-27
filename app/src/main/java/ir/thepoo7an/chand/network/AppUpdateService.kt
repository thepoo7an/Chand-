package ir.thepoo7an.chand.network

import android.os.Build
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import ir.thepoo7an.chand.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

@JsonClass(generateAdapter = true)
data class GitHubReleaseAssetDto(
    val name: String = "",
    @Json(name = "browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseDto(
    @Json(name = "tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @Json(name = "html_url") val htmlUrl: String = "",
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    @Json(name = "published_at") val publishedAt: String = "",
    val assets: List<GitHubReleaseAssetDto> = emptyList()
)

interface GitHubReleaseApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubReleaseDto

    @GET("repos/{owner}/{repo}/releases")
    suspend fun listReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 5
    ): List<GitHubReleaseDto>
}

data class ChangelogSection(
    val title: String?,
    val items: List<String>
)

data class AppUpdateInfo(
    val currentVersionName: String,
    val currentVersionCode: Int,
    val latestVersionName: String,
    val latestVersionCode: Int?,
    val releaseTitle: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val downloadUrl: String,
    val matchedApkName: String,
    val matchedApkSizeBytes: Long,
    val publishedAtIso: String = "",
    val hasDirectApkAsset: Boolean = downloadUrl.endsWith(".apk", ignoreCase = true),
    val expectedPackageName: String = UpdateSecurityValidator.EXPECTED_PACKAGE_NAME,
    val expectedSha256: String = "",
    val hasVerifiedMetadata: Boolean = false
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object RepoNotConfigured : AppUpdateState
    data class UpToDate(
        val currentVersionName: String,
        val currentVersionCode: Int,
        val checkedAtMillis: Long
    ) : AppUpdateState

    data class UpdateAvailable(val info: AppUpdateInfo) : AppUpdateState
    data class Error(
        val message: String,
        val reason: UpdateErrorReason = UpdateErrorReason.fromCode(message)
    ) : AppUpdateState
}

/**
 * GitHub Releases update checker & hardened in-app APK downloader.
 * Enforces HTTPS-only transfers with redirect downgrade checks, fetches & binds trusted release
 * metadata (package name, versionCode, download URL, size, SHA-256), verifies APK ZIP structure,
 * and preflights the production release signing certificate prior to launching Android's installer.
 */
class AppUpdateService(
    private val client: OkHttpClient = NetworkConfig.sharedClient,
    private val trustedHosts: Set<String> = UpdateSecurityValidator.DEFAULT_TRUSTED_HOSTS,
    private val apkInspector: ApkPackageInspector? = null
) {
    private val moshi: Moshi = Moshi.Builder().build()
    private val metadataAdapter by lazy { moshi.adapter(UpdateReleaseMetadataDto::class.java) }

    private val api: GitHubReleaseApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(NetworkConfig.clientFor(NetworkConfig.RequestProfile.UPDATE_CHECK, client))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubReleaseApi::class.java)
    }

    private val downloadClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(15L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .writeTimeout(15L, TimeUnit.SECONDS)
            .callTimeout(0L, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private val checkDeduplicator = InFlightRequestDeduplicator<String, AppUpdateState>()

    suspend fun checkForUpdate(
        repoSlugInput: String,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS
    ): AppUpdateState = withContext(Dispatchers.IO) {
        val parsed = parseRepoSlug(repoSlugInput) ?: return@withContext AppUpdateState.RepoNotConfigured
        val (owner, repo) = parsed
        val dedupeKey = "${owner.lowercase()}/${repo.lowercase()}|$currentVersionCode|$currentVersionName|${supportedAbis.joinToString(",")}"
        val updateProfile = NetworkConfig.RequestProfile.UPDATE_CHECK

        checkDeduplicator.execute(dedupeKey) {
            var releaseListSucceeded = false
            var lastFailure: Throwable? = null
            val release = try {
                NetworkConfig.retrySuspend(
                    maxAttempts = updateProfile.defaultMaxAttempts,
                    baseDelayMillis = updateProfile.baseDelayMillis,
                    maxDelayMillis = updateProfile.maxDelayMillis
                ) {
                    api.getLatestRelease(owner, repo)
                }.takeUnless { it.draft || it.prerelease }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastFailure = t
                try {
                    val list = NetworkConfig.retrySuspend(
                        maxAttempts = updateProfile.defaultMaxAttempts,
                        baseDelayMillis = updateProfile.baseDelayMillis,
                        maxDelayMillis = updateProfile.maxDelayMillis
                    ) {
                        api.listReleases(owner, repo, perPage = 10)
                    }
                    releaseListSucceeded = true
                    selectLatestStableRelease(list)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (inner: Throwable) {
                    lastFailure = inner
                    null
                }
            }

            if (release == null) {
                return@execute if (releaseListSucceeded) {
                    AppUpdateState.UpToDate(
                        currentVersionName = currentVersionName,
                        currentVersionCode = currentVersionCode,
                        checkedAtMillis = System.currentTimeMillis()
                    )
                } else {
                    val reason = mapThrowableToUpdateError(lastFailure).reason
                    val code = if (reason == UpdateErrorReason.NETWORK_FAILURE) {
                        "release_fetch_failed"
                    } else {
                        reason.code
                    }
                    AppUpdateState.Error(message = code, reason = reason)
                }
            }

            val remoteBuild = extractBuildNumber(release)
            val remoteSemver = extractCleanVersionName(release)

            val newer = isRemoteNewer(
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                remoteVersionName = remoteSemver,
                remoteBuildCode = remoteBuild
            )

            if (!newer) {
                return@execute AppUpdateState.UpToDate(
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                    checkedAtMillis = System.currentTimeMillis()
                )
            }

            val safeReleasePageUrl = release.htmlUrl
                .takeIf { UpdateSecurityValidator.isSafeExternalHttpsUrl(it, trustedHosts) }
                .orEmpty()

            val apkAsset = pickBestApkAsset(release.assets, supportedAbis)
            val metadataAsset = findMetadataAsset(release.assets)

            if (metadataAsset != null) {
                if (apkAsset == null) {
                    return@execute AppUpdateState.Error(
                        message = UpdateErrorReason.METADATA_INVALID.code,
                        reason = UpdateErrorReason.METADATA_INVALID
                    )
                }
                return@execute try {
                    val rawMeta = fetchHttpsText(metadataAsset.browserDownloadUrl)
                    val metadataDto = runCatching { metadataAdapter.fromJson(rawMeta) }.getOrNull()
                        ?: throw AppUpdateException(
                            UpdateErrorReason.METADATA_INVALID,
                            "Malformed update metadata JSON"
                        )
                    val verifiedArtifact = UpdateSecurityValidator.validateAndBindArtifactMetadata(
                        metadata = metadataDto,
                        expectedApkName = apkAsset.name,
                        expectedDownloadUrl = apkAsset.browserDownloadUrl,
                        expectedAssetSizeBytes = apkAsset.size,
                        expectedReleaseBuildCode = remoteBuild,
                        currentVersionCode = currentVersionCode,
                        trustedHosts = trustedHosts
                    )
                    val info = AppUpdateInfo(
                        currentVersionName = currentVersionName,
                        currentVersionCode = currentVersionCode,
                        latestVersionName = metadataDto.versionName.ifBlank { remoteSemver },
                        latestVersionCode = verifiedArtifact.versionCode,
                        releaseTitle = release.name.ifBlank { release.tagName },
                        releaseNotes = release.body.trim(),
                        releasePageUrl = safeReleasePageUrl,
                        downloadUrl = verifiedArtifact.downloadUrl,
                        matchedApkName = apkAsset.name,
                        matchedApkSizeBytes = verifiedArtifact.sizeBytes,
                        publishedAtIso = release.publishedAt,
                        hasDirectApkAsset = true,
                        expectedPackageName = verifiedArtifact.packageName,
                        expectedSha256 = verifiedArtifact.sha256,
                        hasVerifiedMetadata = true
                    )
                    AppUpdateState.UpdateAvailable(info)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    val mapped = mapThrowableToUpdateError(t)
                    AppUpdateState.Error(message = mapped.reason.code, reason = mapped.reason)
                }
            }

            if (apkAsset != null && apkAsset.browserDownloadUrl.trim().startsWith("http://", ignoreCase = true)) {
                return@execute AppUpdateState.Error(
                    message = UpdateErrorReason.HTTPS_VALIDATION_FAILED.code,
                    reason = UpdateErrorReason.HTTPS_VALIDATION_FAILED
                )
            }

            val fallbackUrl = apkAsset?.browserDownloadUrl?.takeIf { it.isNotBlank() } ?: safeReleasePageUrl

            AppUpdateState.UpdateAvailable(
                AppUpdateInfo(
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                    latestVersionName = remoteSemver,
                    latestVersionCode = remoteBuild,
                    releaseTitle = release.name.ifBlank { release.tagName },
                    releaseNotes = release.body.trim(),
                    releasePageUrl = safeReleasePageUrl,
                    downloadUrl = fallbackUrl,
                    matchedApkName = apkAsset?.name ?: "Chand-release.apk",
                    matchedApkSizeBytes = apkAsset?.size ?: 0L,
                    publishedAtIso = release.publishedAt,
                    // No trusted `chand-update-metadata.json` in this release means there is no
                    // SHA-256 checksum to verify against, so the APK is deliberately
                    // browser-download only and never handed to the in-app installer.
                    hasDirectApkAsset = false,
                    expectedPackageName = UpdateSecurityValidator.EXPECTED_PACKAGE_NAME,
                    expectedSha256 = "",
                    hasVerifiedMetadata = false
                )
            )
        }
    }

    private suspend fun fetchHttpsText(url: String): String {
        val response = executeHttpsGetWithVerifiedRedirects(
            initialUrl = url,
            acceptHeader = "application/json, text/plain;q=0.9"
        )
        return response.use { resp ->
            if (!resp.isSuccessful) {
                throw AppUpdateException(
                    UpdateErrorReason.NETWORK_FAILURE,
                    "HTTP ${resp.code} fetching update metadata"
                )
            }
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) {
                throw AppUpdateException(
                    UpdateErrorReason.METADATA_INVALID,
                    "Empty update metadata payload"
                )
            }
            body
        }
    }

    private suspend fun executeHttpsGetWithVerifiedRedirects(
        initialUrl: String,
        acceptHeader: String
    ): Response {
        var currentUrl: HttpUrl = UpdateSecurityValidator.validateHttpsUrl(initialUrl, trustedHosts)
        var hops = 0
        while (hops <= UpdateSecurityValidator.MAX_REDIRECT_HOPS) {
            currentCoroutineContext().ensureActive()
            val request = Request.Builder()
                .url(currentUrl)
                .header("Accept", acceptHeader)
                .build()
            val call = downloadClient.newCall(request)
            val response = try {
                call.execute()
            } catch (ce: CancellationException) {
                call.cancel()
                throw ce
            } catch (t: Throwable) {
                throw mapThrowableToUpdateError(t)
            }

            if (response.code in REDIRECT_STATUS_CODES) {
                val location = response.header("Location")
                response.close()
                currentUrl = UpdateSecurityValidator.resolveRedirectUrl(
                    currentUrl = currentUrl,
                    locationHeader = location,
                    trustedHosts = trustedHosts
                )
                hops++
                continue
            }

            // Re-validate final request URL after any transport/interceptor execution
            return try {
                UpdateSecurityValidator.validateHttpsUrl(response.request.url.toString(), trustedHosts)
                response
            } catch (t: Throwable) {
                response.close()
                throw t
            }
        }
        throw AppUpdateException(
            UpdateErrorReason.NETWORK_FAILURE,
            "Exceeded maximum redirect hops (${UpdateSecurityValidator.MAX_REDIRECT_HOPS})"
        )
    }

    /**
     * Downloads an update APK strictly over HTTPS from a trusted host (rejecting plain HTTP,
     * HTTPS->HTTP redirect downgrades, and untrusted final URLs), verifies byte length,
     * SHA-256 checksum, full ZIP/APK structure, package name, versionCode upgrade monotonicity,
     * and production release signing certificate before atomically committing [destinationFile].
     */
    suspend fun downloadApk(
        downloadUrl: String,
        destinationFile: File,
        expectedSizeBytes: Long = 0L,
        expectedSha256: String = "",
        expectedPackageName: String = UpdateSecurityValidator.EXPECTED_PACKAGE_NAME,
        expectedVersionCode: Int? = null,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        expectedReleaseCertSha256: String = BuildConfig.EXPECTED_RELEASE_CERT_SHA256,
        apkInspector: ApkPackageInspector? = this.apkInspector,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        UpdateSecurityValidator.validateHttpsUrl(downloadUrl, trustedHosts)

        if (!UpdateSecurityValidator.isValidSha256Hex(expectedSha256)) {
            throw AppUpdateException(
                UpdateErrorReason.METADATA_INVALID,
                "Trusted SHA-256 checksum is required before downloading APK"
            )
        }
        if (expectedPackageName != UpdateSecurityValidator.EXPECTED_PACKAGE_NAME) {
            throw AppUpdateException(
                UpdateErrorReason.WRONG_PACKAGE_NAME,
                "Expected package name $expectedPackageName is not ${UpdateSecurityValidator.EXPECTED_PACKAGE_NAME}"
            )
        }
        if (expectedVersionCode != null && expectedVersionCode <= currentVersionCode) {
            throw AppUpdateException(
                UpdateErrorReason.OLDER_OR_EQUAL_VERSION,
                "Update versionCode $expectedVersionCode is not newer than $currentVersionCode"
            )
        }
        if (expectedSizeBytes > UpdateSecurityValidator.MAX_APK_SIZE_BYTES ||
            (expectedSizeBytes in 1L until UpdateSecurityValidator.MIN_APK_SIZE_BYTES)
        ) {
            throw AppUpdateException(
                UpdateErrorReason.UNREASONABLE_FILE_SIZE,
                "Expected APK size out of bounds: $expectedSizeBytes"
            )
        }
        val inspector = apkInspector ?: throw AppUpdateException(
            UpdateErrorReason.CORRUPTED_APK,
            "APK package inspector is required for pre-install verification"
        )

        destinationFile.parentFile?.mkdirs()
        UpdateSecurityValidator.checkAvailableStorage(destinationFile.parentFile, expectedSizeBytes)

        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.part")
        if (tempFile.exists()) tempFile.delete()

        try {
            executeHttpsGetWithVerifiedRedirects(
                initialUrl = downloadUrl,
                acceptHeader = "application/vnd.android.package-archive, application/octet-stream"
            ).use { response ->
                if (!response.isSuccessful) {
                    throw AppUpdateException(
                        UpdateErrorReason.NETWORK_FAILURE,
                        "HTTP ${response.code} downloading update"
                    )
                }
                val body = response.body ?: throw AppUpdateException(
                    UpdateErrorReason.INCOMPLETE_DOWNLOAD,
                    "Empty response body"
                )
                val rawContentLength = body.contentLength()
                if (rawContentLength == 0L) {
                    throw AppUpdateException(
                        UpdateErrorReason.INCOMPLETE_DOWNLOAD,
                        "Server returned 0-byte APK response"
                    )
                }
                if (rawContentLength > UpdateSecurityValidator.MAX_APK_SIZE_BYTES) {
                    throw AppUpdateException(
                        UpdateErrorReason.UNREASONABLE_FILE_SIZE,
                        "Reported Content-Length ($rawContentLength) exceeds maximum allowed APK size"
                    )
                }
                if (rawContentLength in 1L until UpdateSecurityValidator.MIN_APK_SIZE_BYTES) {
                    throw AppUpdateException(
                        UpdateErrorReason.INCOMPLETE_DOWNLOAD,
                        "Reported Content-Length ($rawContentLength) is smaller than valid APK minimum"
                    )
                }
                val headerLen = rawContentLength.takeIf { it > 0L } ?: 0L
                val totalBytes = if (headerLen > 0L) headerLen else expectedSizeBytes.coerceAtLeast(0L)
                UpdateSecurityValidator.checkAvailableStorage(destinationFile.parentFile, totalBytes)

                var downloadedBytes = 0L
                var lastReportedBytes = 0L
                onProgress(0L, totalBytes)

                try {
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(DEFAULT_DOWNLOAD_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (read > 0) {
                                    downloadedBytes += read
                                    if (downloadedBytes > UpdateSecurityValidator.MAX_APK_SIZE_BYTES) {
                                        throw AppUpdateException(
                                            UpdateErrorReason.UNREASONABLE_FILE_SIZE,
                                            "Stream exceeded maximum allowed APK size"
                                        )
                                    }
                                    output.write(buffer, 0, read)
                                    if (downloadedBytes - lastReportedBytes >= PROGRESS_REPORT_STEP_BYTES) {
                                        lastReportedBytes = downloadedBytes
                                        onProgress(downloadedBytes, totalBytes)
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    throw mapThrowableToUpdateError(t)
                }

                if (downloadedBytes == 0L || downloadedBytes < UpdateSecurityValidator.MIN_APK_SIZE_BYTES) {
                    throw AppUpdateException(
                        UpdateErrorReason.INCOMPLETE_DOWNLOAD,
                        "Downloaded APK is empty or truncated ($downloadedBytes bytes)"
                    )
                }
                if (headerLen > 0L && downloadedBytes != headerLen) {
                    throw AppUpdateException(
                        UpdateErrorReason.INCOMPLETE_DOWNLOAD,
                        "Incomplete download ($downloadedBytes / $headerLen bytes)"
                    )
                }

                UpdateSecurityValidator.verifyDownloadedApkForInstall(
                    apkFile = tempFile,
                    expectedSha256 = expectedSha256,
                    expectedSizeBytes = expectedSizeBytes,
                    expectedPackageName = expectedPackageName,
                    expectedVersionCode = expectedVersionCode,
                    currentVersionCode = currentVersionCode,
                    expectedReleaseCertSha256 = expectedReleaseCertSha256,
                    apkInspector = inspector
                )

                onProgress(downloadedBytes, if (totalBytes > 0L) totalBytes else downloadedBytes)

                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                if (!tempFile.renameTo(destinationFile)) {
                    try {
                        tempFile.copyTo(destinationFile, overwrite = true)
                        tempFile.delete()
                    } catch (t: Throwable) {
                        destinationFile.delete()
                        throw mapThrowableToUpdateError(t)
                    }
                }
                destinationFile
            }
        } catch (ce: CancellationException) {
            tempFile.delete()
            throw ce
        } catch (t: Throwable) {
            tempFile.delete()
            throw mapThrowableToUpdateError(t)
        }
    }

    private fun findMetadataAsset(assets: List<GitHubReleaseAssetDto>): GitHubReleaseAssetDto? =
        assets.firstOrNull {
            it.name.trim().equals(UpdateSecurityValidator.METADATA_PRIMARY_FILENAME, ignoreCase = true)
        } ?: assets.firstOrNull {
            it.name.trim().equals(UpdateSecurityValidator.METADATA_FALLBACK_FILENAME, ignoreCase = true)
        }

    private fun mapThrowableToUpdateError(t: Throwable?): AppUpdateException {
        if (t is AppUpdateException) return t
        if (t == null) {
            return AppUpdateException(UpdateErrorReason.NETWORK_FAILURE, "Update network failure")
        }
        if (hasCause<SSLException>(t)) {
            return AppUpdateException(
                UpdateErrorReason.HTTPS_VALIDATION_FAILED,
                t.message ?: "TLS/HTTPS validation failed",
                t
            )
        }
        if (UpdateSecurityValidator.isInsufficientStorageCause(t)) {
            return AppUpdateException(
                UpdateErrorReason.INSUFFICIENT_STORAGE,
                t.message ?: "Insufficient storage space",
                t
            )
        }
        return AppUpdateException(
            UpdateErrorReason.NETWORK_FAILURE,
            t.message ?: "Network failure during update",
            t
        )
    }

    private inline fun <reified T : Throwable> hasCause(t: Throwable): Boolean {
        var cur: Throwable? = t
        while (cur != null) {
            if (cur is T) return true
            cur = cur.cause
        }
        return false
    }

    companion object {
        private const val DEFAULT_DOWNLOAD_BUFFER_SIZE = 16 * 1024
        private const val PROGRESS_REPORT_STEP_BYTES = 48 * 1024L
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

        fun hasApkZipMagicHeader(file: File): Boolean {
            if (!file.exists() || !file.isFile || file.length() < 4L) return false
            return runCatching {
                RandomAccessFile(file, "r").use { raf ->
                    val magic = ByteArray(4)
                    val read = raf.read(magic)
                    read == 4 &&
                        magic[0] == 0x50.toByte() &&
                        magic[1] == 0x4B.toByte() &&
                        magic[2] == 0x03.toByte() &&
                        magic[3] == 0x04.toByte()
                }
            }.getOrDefault(false)
        }

        /**
         * Parses GitHub Release markdown notes into structured changelog sections, filtering out
         * redundant APK file-list boilerplate and Full Changelog URL lines while keeping clean
         * bilingual release bullets.
         */
        fun parseReleaseNotes(rawBody: String): List<ChangelogSection> {
            val normalized = rawBody.replace("\r\n", "\n").trim()
            if (normalized.isBlank()) return emptyList()

            val lines = normalized.lines()
            val sections = mutableListOf<ChangelogSection>()
            var currentTitle: String? = null
            var currentItems = mutableListOf<String>()
            var skippingDownloadBoilerplate = false

            fun flushSection() {
                if (currentItems.isNotEmpty()) {
                    sections.add(ChangelogSection(title = currentTitle, items = currentItems.toList()))
                }
                currentItems = mutableListOf()
            }

            for (rawLine in lines) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#")) {
                    flushSection()
                    val headingText = trimmed.trimStart('#').trim()
                    val lower = headingText.lowercase()
                    skippingDownloadBoilerplate = "download" in lower &&
                        ("فایل نصب" in headingText || "📲" in headingText || headingText.equals("Download", ignoreCase = true))
                    currentTitle = headingText.takeIf { it.isNotBlank() && !skippingDownloadBoilerplate }
                    continue
                }

                if (skippingDownloadBoilerplate) {
                    continue
                }

                if (trimmed.startsWith("**Full Changelog**", ignoreCase = true) ||
                    trimmed.startsWith("Full Changelog:", ignoreCase = true)
                ) {
                    continue
                }

                val isBullet = trimmed.startsWith("- ") ||
                    trimmed.startsWith("* ") ||
                    trimmed.startsWith("• ") ||
                    Regex("""^\d+\.\s+""").containsMatchIn(trimmed)

                val content = if (isBullet) {
                    trimmed
                        .replaceFirst(Regex("""^(?:[-*•]|\d+\.)\s+"""), "")
                        .trim()
                } else {
                    trimmed
                }

                val cleaned = cleanMarkdownInline(content)
                if (cleaned.isNotBlank()) {
                    currentItems.add(cleaned)
                }
            }
            flushSection()
            return sections
        }

        private fun cleanMarkdownInline(text: String): String {
            var out = text
            out = out.replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
            out = out.replace(Regex("""\s+by\s+@\S+\s+in\s+https?://\S+$""", RegexOption.IGNORE_CASE), "")
            out = out.replace(Regex("""\s+in\s+https?://github\.com/\S+$""", RegexOption.IGNORE_CASE), "")
            out = out.replace("**", "").replace("__", "").replace("`", "")
            return out.trim()
        }

        fun selectLatestStableRelease(releases: List<GitHubReleaseDto>): GitHubReleaseDto? =
            releases.firstOrNull { !it.draft && !it.prerelease }

        fun parseRepoSlug(raw: String): Pair<String, String>? {
            val cleaned = raw.trim()
                .removePrefix("https://github.com/")
                .removePrefix("http://github.com/")
                .trim('/')
            val parts = cleaned.split('/').filter { it.isNotBlank() }
            if (parts.size < 2) return null
            return parts[0] to parts[1]
        }

        fun pickBestApkAsset(
            assets: List<GitHubReleaseAssetDto>,
            supportedAbis: Array<String>
        ): GitHubReleaseAssetDto? {
            val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            if (apkAssets.isEmpty()) return null

            for (abi in supportedAbis) {
                if (abi.isBlank()) continue
                val match = apkAssets.firstOrNull { it.name.contains(abi, ignoreCase = true) }
                if (match != null) return match
            }

            return apkAssets.firstOrNull { it.name.contains("universal", ignoreCase = true) }
                ?: apkAssets.first()
        }

        fun extractBuildNumber(release: GitHubReleaseDto): Int? {
            val tagMatch = Regex("""(?:build[-_]?|b)(\d+)""", RegexOption.IGNORE_CASE)
                .find(release.tagName)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (tagMatch != null) return tagMatch

            val titleMatch = Regex("""\(Build\s+(\d+)\)""", RegexOption.IGNORE_CASE)
                .find(release.name)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
            return titleMatch
        }

        fun extractCleanVersionName(release: GitHubReleaseDto): String {
            val tag = release.tagName.trim()
            if (tag.startsWith("v", ignoreCase = true)) {
                return tag.substring(1)
            }
            val titleSemver = Regex("""v(\d+(?:\.\d+)*)""", RegexOption.IGNORE_CASE)
                .find(release.name)
                ?.groupValues?.getOrNull(1)
            if (!titleSemver.isNullOrBlank()) return titleSemver
            return tag.ifBlank { release.name }
        }

        fun isRemoteNewer(
            currentVersionName: String,
            currentVersionCode: Int,
            remoteVersionName: String,
            remoteBuildCode: Int?
        ): Boolean {
            if (remoteBuildCode != null && remoteBuildCode > currentVersionCode) {
                return true
            }
            val semverCmp = compareVersionStrings(remoteVersionName, currentVersionName)
            if (semverCmp > 0) return true
            if (semverCmp < 0) return false
            return remoteBuildCode != null && remoteBuildCode > currentVersionCode
        }

        fun compareVersionStrings(a: String, b: String): Int {
            val aParts = a.replace(Regex("[^0-9.]"), "").split('.').mapNotNull { it.toIntOrNull() }
            val bParts = b.replace(Regex("[^0-9.]"), "").split('.').mapNotNull { it.toIntOrNull() }
            if (aParts.isEmpty() || bParts.isEmpty()) return 0
            val maxLen = maxOf(aParts.size, bParts.size)
            for (i in 0 until maxLen) {
                val av = aParts.getOrElse(i) { 0 }
                val bv = bParts.getOrElse(i) { 0 }
                if (av != bv) return av.compareTo(bv)
            }
            return 0
        }
    }
}
