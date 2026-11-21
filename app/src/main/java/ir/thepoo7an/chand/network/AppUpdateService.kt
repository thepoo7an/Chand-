package ir.thepoo7an.chand.network

import android.os.Build
import ir.thepoo7an.chand.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

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
    val matchedApkSizeBytes: Long
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
 * Zero-permission GitHub Releases update checker (Google Play Creator Program compliant).
 * Automatically matches the device's CPU ABI (arm64-v8a / armeabi-v7a / universal) and exposes
 * direct release download URLs to open cleanly in the system browser via Intent.ACTION_VIEW.
 */
class AppUpdateService(
    client: OkHttpClient = NetworkConfig.sharedClient
) {
    private val moshi: Moshi = Moshi.Builder().build()

    private val api: GitHubReleaseApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubReleaseApi::class.java)
    }

    suspend fun checkForUpdate(
        repoSlugInput: String,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS
    ): AppUpdateState = withContext(Dispatchers.IO) {
        val parsed = parseRepoSlug(repoSlugInput) ?: return@withContext AppUpdateState.RepoNotConfigured
        val (owner, repo) = parsed

        var releaseListSucceeded = false
        val release = try {
            api.getLatestRelease(owner, repo).takeUnless { it.draft || it.prerelease }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Throwable) {
            try {
                val list = api.listReleases(owner, repo, perPage = 10)
                releaseListSucceeded = true
                selectLatestStableRelease(list)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Throwable) {
                null
            }
        }

        if (release == null) {
            return@withContext if (releaseListSucceeded) {
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
            return@withContext AppUpdateState.UpToDate(
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                checkedAtMillis = System.currentTimeMillis()
            )
        }

        val apkAsset = pickBestApkAsset(release.assets, supportedAbis)
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
                matchedApkSizeBytes = apkAsset?.size ?: 0L
            )
        )
    }

    companion object {
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
