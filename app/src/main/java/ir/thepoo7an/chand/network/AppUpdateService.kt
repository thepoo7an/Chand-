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
import okhttp3.OkHttpClient
import okhttp3.Request
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
    val hasDirectApkAsset: Boolean = downloadUrl.endsWith(".apk", ignoreCase = true)
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
    data class Error(val message: String) : AppUpdateState
}

/**
 * GitHub Releases update checker & streaming in-app APK downloader.
 * Automatically matches the device's CPU ABI (`arm64-v8a` / `armeabi-v7a` / `universal`),
 * parses release changelog markdown into clean structured sections, and streams verified APK
 * binaries into the app cache directory for direct installation.
 */
class AppUpdateService(
    private val client: OkHttpClient = NetworkConfig.sharedClient
) {
    private val moshi: Moshi = Moshi.Builder().build()

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
            .followRedirects(true)
            .followSslRedirects(true)
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
            } catch (_: Throwable) {
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
                } catch (_: Throwable) {
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
                    AppUpdateState.Error("release_fetch_failed")
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

            val apkAsset = pickBestApkAsset(release.assets, supportedAbis)
            val hasDirectApk = !apkAsset?.browserDownloadUrl.isNullOrBlank()
            val downloadUrl = apkAsset?.browserDownloadUrl?.takeIf { it.isNotBlank() }
                ?: release.htmlUrl

            AppUpdateState.UpdateAvailable(
                AppUpdateInfo(
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                    latestVersionName = remoteSemver,
                    latestVersionCode = remoteBuild,
                    releaseTitle = release.name.ifBlank { release.tagName },
                    releaseNotes = release.body.trim(),
                    releasePageUrl = release.htmlUrl,
                    downloadUrl = downloadUrl,
                    matchedApkName = apkAsset?.name ?: "Chand-release.apk",
                    matchedApkSizeBytes = apkAsset?.size ?: 0L,
                    publishedAtIso = release.publishedAt,
                    hasDirectApkAsset = hasDirectApk
                )
            )
        }
    }

    /**
     * Streams the APK file at [downloadUrl] into [destinationFile] via a temporary `.part` file,
     * reporting byte-level progress via [onProgress] and verifying ZIP/APK integrity before
     * committing to [destinationFile].
     */
    suspend fun downloadApk(
        downloadUrl: String,
        destinationFile: File,
        expectedSizeBytes: Long = 0L,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        require(
            downloadUrl.startsWith("https://", ignoreCase = true) ||
                downloadUrl.startsWith("http://", ignoreCase = true)
        ) {
            "Unsupported download URL scheme"
        }

        destinationFile.parentFile?.mkdirs()
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.part")
        if (tempFile.exists()) tempFile.delete()

        val request = Request.Builder()
            .url(downloadUrl)
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
            .build()

        val call = downloadClient.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} downloading update")
                }
                val body = response.body ?: throw IOException("Empty response body")
                val headerLen = body.contentLength().takeIf { it > 0L } ?: 0L
                val totalBytes = if (headerLen > 0L) headerLen else expectedSizeBytes.coerceAtLeast(0L)

                var downloadedBytes = 0L
                var lastReportedBytes = 0L
                onProgress(0L, totalBytes)

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(DEFAULT_DOWNLOAD_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read > 0) {
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                if (downloadedBytes - lastReportedBytes >= PROGRESS_REPORT_STEP_BYTES) {
                                    lastReportedBytes = downloadedBytes
                                    onProgress(downloadedBytes, totalBytes)
                                }
                            }
                        }
                        output.flush()
                    }
                }

                if (downloadedBytes <= 4L) {
                    throw IOException("Downloaded file is empty or truncated")
                }
                if (headerLen > 0L && downloadedBytes != headerLen) {
                    throw IOException("Incomplete download ($downloadedBytes / $headerLen bytes)")
                }
                if (!hasApkZipMagicHeader(tempFile)) {
                    throw IOException("Downloaded payload is not a valid APK archive")
                }

                onProgress(downloadedBytes, if (totalBytes > 0L) totalBytes else downloadedBytes)

                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                if (!tempFile.renameTo(destinationFile)) {
                    tempFile.copyTo(destinationFile, overwrite = true)
                    tempFile.delete()
                }
                destinationFile
            }
        } catch (ce: CancellationException) {
            call.cancel()
            tempFile.delete()
            throw ce
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }
    }

    companion object {
        private const val DEFAULT_DOWNLOAD_BUFFER_SIZE = 16 * 1024
        private const val PROGRESS_REPORT_STEP_BYTES = 48 * 1024L

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

                // Skip redundant GitHub auto-generated Full Changelog link lines
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
            // Convert markdown links [label](url) -> label
            out = out.replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
            // Strip PR/commit noise suffix generated by GitHub release notes: " by @user in https://..."
            out = out.replace(Regex("""\s+by\s+@\S+\s+in\s+https?://\S+$""", RegexOption.IGNORE_CASE), "")
            out = out.replace(Regex("""\s+in\s+https?://github\.com/\S+$""", RegexOption.IGNORE_CASE), "")
            // Strip inline bold/italic/code markers
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
