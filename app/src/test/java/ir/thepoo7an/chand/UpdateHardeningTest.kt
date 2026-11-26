package ir.thepoo7an.chand

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.thepoo7an.chand.network.ApkPackageInspector
import ir.thepoo7an.chand.network.AppUpdateException
import ir.thepoo7an.chand.network.AppUpdateInfo
import ir.thepoo7an.chand.network.AppUpdateInstaller
import ir.thepoo7an.chand.network.AppUpdateService
import ir.thepoo7an.chand.network.AppUpdateState
import ir.thepoo7an.chand.network.InstallLaunchResult
import ir.thepoo7an.chand.network.UpdateArtifactMetadataDto
import ir.thepoo7an.chand.network.UpdateErrorReason
import ir.thepoo7an.chand.network.UpdateReleaseMetadataDto
import ir.thepoo7an.chand.network.UpdateSecurityValidator
import ir.thepoo7an.chand.network.VerifiedApkDetails
import ir.thepoo7an.chand.ui.strings.EnStrings
import ir.thepoo7an.chand.ui.strings.FaStrings
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.net.ssl.SSLHandshakeException

internal object UpdateTestFixtures {
    fun buildMinimalSignedApkBytes(
        includeManifest: Boolean = true,
        includeDex: Boolean = true,
        includeV1Sig: Boolean = true,
        paddingSize: Int = 512
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            if (includeManifest) {
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write("manifest-placeholder".toByteArray() + ByteArray(paddingSize) { 0x41 })
                zos.closeEntry()
            }
            if (includeDex) {
                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00))
                zos.closeEntry()
            }
            if (includeV1Sig) {
                zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                zos.write("Manifest-Version: 1.0\r\n".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
                zos.write(byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00))
                zos.closeEntry()
            }
            zos.putNextEntry(ZipEntry("resources.arsc"))
            val resSize = maxOf(paddingSize, 1024)
            zos.write(ByteArray(resSize) { i -> ((i * 73 + 19) xor (i ushr 3) xor (i * 13)).toByte() })
            zos.closeEntry()
        }
        return baos.toByteArray()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class)
class UpdateHardeningTest {

    private fun interceptorClient(handler: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()

    private fun validInspector(
        packageName: String = UpdateSecurityValidator.EXPECTED_PACKAGE_NAME,
        versionCode: Long = 200L,
        signerDigests: Set<String> = setOf(BuildConfig.EXPECTED_RELEASE_CERT_SHA256)
    ): ApkPackageInspector = ApkPackageInspector {
        VerifiedApkDetails(
            packageName = packageName,
            versionCode = versionCode,
            signerSha256Digests = signerDigests
        )
    }

    private fun bytesResponse(
        request: Request,
        bytes: ByteArray,
        contentType: String = "application/vnd.android.package-archive",
        declaredContentLength: Long = bytes.size.toLong()
    ): Response {
        val body = object : ResponseBody() {
            override fun contentType() = contentType.toMediaType()
            override fun contentLength() = declaredContentLength
            override fun source(): BufferedSource = Buffer().write(bytes)
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }

    private fun redirectResponse(request: Request, code: Int = 302, location: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Redirect")
            .header("Location", location)
            .body(ByteArray(0).toResponseBody(null))
            .build()

    private fun jsonResponse(request: Request, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    @Test
    fun `https url validation enforces https and rejects plain http and untrusted hosts`() {
        val valid = UpdateSecurityValidator.validateHttpsUrl(
            "https://github.com/thepoo7an/chand-iran-currency-gold-crypto/releases/download/v2.0/app.apk"
        )
        assertTrue(valid.isHttps)

        try {
            UpdateSecurityValidator.validateHttpsUrl(
                "http://github.com/thepoo7an/chand-iran-currency-gold-crypto/releases/download/v2.0/app.apk"
            )
            fail("Expected HTTP URL to be rejected")
        } catch (e: AppUpdateException) {
            assertEquals(UpdateErrorReason.HTTPS_VALIDATION_FAILED, e.reason)
        }

        try {
            UpdateSecurityValidator.validateHttpsUrl("https://evil.attacker.invalid/Chand-release.apk")
            fail("Expected untrusted host to be rejected")
        } catch (e: AppUpdateException) {
            assertEquals(UpdateErrorReason.INVALID_URL, e.reason)
        }

        try {
            UpdateSecurityValidator.validateHttpsUrl("https://user:pass@github.com/a.apk")
            fail("Expected embedded credentials in URL to be rejected")
        } catch (e: AppUpdateException) {
            assertEquals(UpdateErrorReason.INVALID_URL, e.reason)
        }
    }

    @Test
    fun `redirect downgrade from https to http and invalid final host are rejected`() = runBlocking {
        val tempDir = Files.createTempDirectory("chand-redirect-test").toFile()
        try {
            val apkBytes = UpdateTestFixtures.buildMinimalSignedApkBytes()
            val sha = UpdateSecurityValidator.sha256Hex(apkBytes)

            val downgradeClient = interceptorClient { req ->
                redirectResponse(
                    req,
                    302,
                    "http://objects.githubusercontent.com/chand/Chand-arm64-v8a-release.apk"
                )
            }
            val downgradeService = AppUpdateService(downgradeClient, apkInspector = validInspector())
            try {
                downgradeService.downloadApk(
                    downloadUrl = "https://github.com/thepoo7an/chand-iran-currency-gold-crypto/releases/download/v2.0/Chand-arm64-v8a-release.apk",
                    destinationFile = File(tempDir, "downgrade.apk"),
                    expectedSizeBytes = apkBytes.size.toLong(),
                    expectedSha256 = sha,
                    expectedVersionCode = 200,
                    currentVersionCode = 100
                )
                fail("Expected HTTPS-to-HTTP redirect downgrade to be rejected")
            } catch (e: AppUpdateException) {
                assertEquals(UpdateErrorReason.HTTPS_VALIDATION_FAILED, e.reason)
            }

            val untrustedFinalClient = interceptorClient { req ->
                redirectResponse(req, 302, "https://phishing.untrusted.example/Chand-arm64-v8a-release.apk")
            }
            val untrustedService = AppUpdateService(untrustedFinalClient, apkInspector = validInspector())
            try {
                untrustedService.downloadApk(
                    downloadUrl = "https://github.com/thepoo7an/chand-iran-currency-gold-crypto/releases/download/v2.0/Chand-arm64-v8a-release.apk",
                    destinationFile = File(tempDir, "untrusted-final.apk"),
                    expectedSizeBytes = apkBytes.size.toLong(),
                    expectedSha256 = sha,
                    expectedVersionCode = 200,
                    currentVersionCode = 100
                )
                fail("Expected untrusted final redirect host to be rejected")
            } catch (e: AppUpdateException) {
                assertEquals(UpdateErrorReason.INVALID_URL, e.reason)
            }

            // Trusted HTTPS -> HTTPS redirect (GitHub Release -> objects.githubusercontent.com) succeeds
            val trustedRedirectClient = interceptorClient { req ->
                if (req.url.host == "github.com") {
                    redirectResponse(
                        req,
                        302,
                        "https://objects.githubusercontent.com/github-production-release-asset/Chand-arm64-v8a-release.apk"
                    )
                } else {
                    bytesResponse(req, apkBytes)
                }
            }
            val okFile = AppUpdateService(trustedRedirectClient, apkInspector = validInspector()).downloadApk(
                downloadUrl = "https://github.com/thepoo7an/chand-iran-currency-gold-crypto/releases/download/v2.0/Chand-arm64-v8a-release.apk",
                destinationFile = File(tempDir, "trusted-redirect.apk"),
                expectedSizeBytes = apkBytes.size.toLong(),
                expectedSha256 = sha,
                expectedVersionCode = 200,
                currentVersionCode = 100
            )
            assertTrue(okFile.exists())
            assertEquals(sha, UpdateSecurityValidator.computeFileSha256(okFile))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `downloadApk maps SSL handshake failure and IO failure to distinct reasons`() = runBlocking {
        val tempDir = Files.createTempDirectory("chand-net-err-test").toFile()
        try {
            val apkBytes = UpdateTestFixtures.buildMinimalSignedApkBytes()
            val sha = UpdateSecurityValidator.sha256Hex(apkBytes)

            val sslClient = interceptorClient { throw SSLHandshakeException("Untrusted cert") }
            try {
                AppUpdateService(sslClient, apkInspector = validInspector()).downloadApk(
                    downloadUrl = "https://objects.githubusercontent.com/chand/Chand.apk",
                    destinationFile = File(tempDir, "ssl.apk"),
                    expectedSizeBytes = apkBytes.size.toLong(),
                    expectedSha256 = sha,
                    expectedVersionCode = 200,
                    currentVersionCode = 100
                )
                fail("Expected SSLException to map to HTTPS_VALIDATION_FAILED")
            } catch (e: AppUpdateException) {
                assertEquals(UpdateErrorReason.HTTPS_VALIDATION_FAILED, e.reason)
            }

            val enospcClient = interceptorClient { throw IOException("write failed: ENOSPC (No space left on device)") }
            try {
                AppUpdateService(enospcClient, apkInspector = validInspector()).downloadApk(
                    downloadUrl = "https://objects.githubusercontent.com/chand/Chand.apk",
                    destinationFile = File(tempDir, "enospc.apk"),
                    expectedSizeBytes = apkBytes.size.toLong(),
                    expectedSha256 = sha,
                    expectedVersionCode = 200,
                    currentVersionCode = 100
                )
                fail("Expected ENOSPC to map to INSUFFICIENT_STORAGE")
            } catch (e: AppUpdateException) {
                assertEquals(UpdateErrorReason.INSUFFICIENT_STORAGE, e.reason)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `downloadApk rejects checksum mismatch and incomplete or unreasonable sizes`() = runBlocking {
        val tempDir = Files.createTempDirectory("chand-integrity-test").toFile()
        try {
            val apkBytes = UpdateTestFixtures.buildMinimalSignedApkBytes()
            val realSha = UpdateSecurityValidator.sha256Hex(apkBytes)
            val wrongSha = "0".repeat(64)

            val okClient = interceptorClient { req -> bytesResponse(req, apkBytes) }
            val service = AppUpdateService(okClient, apkInspector = validInspector())

            // Checksum mismatch
            val mismatchDest = File(tempDir, "mismatch.apk")
            try {
                service.downloadApk(
                    downloadUrl = "https://objects.githubusercontent.com/chand/a.apk",
                    destinationFile = mismatchDest,
                    expectedSizeBytes = apkBytes.size.toLong(),
                    expectedSha256 = wrongSha,
                    expectedVersionCode = 200,
                    currentVersionCode = 100
                )
                fail("Expected checksum mismatch error")
            } catch (e: AppUpdateException) {
                assertEquals(UpdateErrorReason.CHECKSUM_MISMATCH, e.reason)
            }
            assertFalse(mismatchDest.exists())

            // Incomplete download (Content-Length promises more bytes than body provides)
            val truncatedClient = interceptorClient { req ->
                bytesResponse(req, apkBytes, declaredContentLength = apkBytes.size.toLong() + 128L)
            }
            try {
                AppUpdateService(truncatedClient, apkInspector = validInspector()).downloadApk(
                    downloadUrl = "https://objects.githubusercontent.com/chand/truncated.apk",
                    destinationFile = File(tempDir, "truncated.apk"),
                    expectedSizeBytes = apkBytes.size.toLong() + 128L,
                    expectedSha256 = realSha,
                    expectedVersionCode = 200,
                    currentVersionCode = 100
                )
                fail("Expected incomplete download rejection")
            } catch (e: AppUpdateException) {
                assertEquals(UpdateErrorReason.INCOMPLETE_DOWNLOAD, e.reason)
            }

            // Empty 0-byte download
            val emptyClient = interceptorClient { req ->
                bytesResponse(req, ByteArray(0), declaredContentLength = 0L)
            }
            try {
                AppUpdateService(emptyClient, apkInspector = validInspector()).downloadApk(
                    downloadUrl = "https://objects.githubusercontent.com/chand/empty.apk",
                    destinationFile = File(tempDir, "empty.apk"),
                    expectedSizeBytes = apkBytes.size.toLong(),
                    expectedSha256 = realSha,
                    expectedVersionCode = 200,
                    currentVersionCode = 100
                )
                fail("Expected empty download rejection")
            } catch (e: AppUpdateException) {
                assertEquals(UpdateErrorReason.INCOMPLETE_DOWNLOAD, e.reason)
            }

            // Unreasonable file size (> MAX_APK_SIZE_BYTES)
            try {
                service.downloadApk(
                    downloadUrl = "https://objects.githubusercontent.com/chand/huge.apk",
                    destinationFile = File(tempDir, "huge.apk"),
                    expectedSizeBytes = UpdateSecurityValidator.MAX_APK_SIZE_BYTES + 1L,
                    expectedSha256 = realSha,
                    expectedVersionCode = 200,
                    currentVersionCode = 100
                )
                fail("Expected oversized expectation rejection")
            } catch (e: AppUpdateException) {
                assertEquals(UpdateErrorReason.UNREASONABLE_FILE_SIZE, e.reason)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `apk structural validation rejects zip without AndroidManifest or classes dex or signature`() = runBlocking {
        val tempDir = Files.createTempDirectory("chand-apk-struct-test").toFile()
        try {
            val missingDexBytes = UpdateTestFixtures.buildMinimalSignedApkBytes(includeDex = false)
            val missingManifestBytes = UpdateTestFixtures.buildMinimalSignedApkBytes(includeManifest = false)
            val unsignedBytes = UpdateTestFixtures.buildMinimalSignedApkBytes(includeV1Sig = false)
            val zipMagicGarbage = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(600) { 0x33 }

            for (badBytes in listOf(missingDexBytes, missingManifestBytes, zipMagicGarbage)) {
                val sha = UpdateSecurityValidator.sha256Hex(badBytes)
                val client = interceptorClient { req -> bytesResponse(req, badBytes) }
                try {
                    AppUpdateService(client, apkInspector = validInspector()).downloadApk(
                        downloadUrl = "https://objects.githubusercontent.com/chand/corrupt.apk",
                        destinationFile = File(tempDir, "corrupt.apk"),
                        expectedSizeBytes = badBytes.size.toLong(),
                        expectedSha256 = sha,
                        expectedVersionCode = 200,
                        currentVersionCode = 100
                    )
                    fail("Expected corrupted APK rejection")
                } catch (e: AppUpdateException) {
                    assertEquals(UpdateErrorReason.CORRUPTED_APK, e.reason)
                }
            }

            // Unsigned ZIP fails signer block check even if manifest & dex exist
            val unsignedSha = UpdateSecurityValidator.sha256Hex(unsignedBytes)
            val unsignedClient = interceptorClient { req -> bytesResponse(req, unsignedBytes) }
            try {
                AppUpdateService(unsignedClient, apkInspector = validInspector()).downloadApk(
                    downloadUrl = "https://objects.githubusercontent.com/chand/unsigned.apk",
                    destinationFile = File(tempDir, "unsigned.apk"),
                    expectedSizeBytes = unsignedBytes.size.toLong(),
                    expectedSha256 = unsignedSha,
                    expectedVersionCode = 200,
                    currentVersionCode = 100
                )
                fail("Expected unsigned APK rejection")
            } catch (e: AppUpdateException) {
                assertEquals(UpdateErrorReason.UNEXPECTED_SIGNER, e.reason)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `downloadApk and installer enforce exact package name, newer versionCode, and production signer`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = Files.createTempDirectory("chand-pkg-signer-test").toFile()
        try {
            val apkBytes = UpdateTestFixtures.buildMinimalSignedApkBytes()
            val sha = UpdateSecurityValidator.sha256Hex(apkBytes)
            val client = interceptorClient { req -> bytesResponse(req, apkBytes) }

            // 1. Wrong package name
            try {
                AppUpdateService(
                    client,
                    apkInspector = validInspector(packageName = "com.evil.imposter")
                ).downloadApk(
                    downloadUrl = "https://objects.githubusercontent.com/chand/wrongpkg.apk",
                    destinationFile = File(tempDir, "wrongpkg.apk"),
                    expectedSizeBytes = apkBytes.size.toLong(),
                    expectedSha256 = sha,
                    expectedVersionCode = 200,
                    currentVersionCode = 100
                )
                fail("Expected wrong package name rejection")
            } catch (e: AppUpdateException) {
                assertEquals(UpdateErrorReason.WRONG_PACKAGE_NAME, e.reason)
            }

            // 2. Older or equal versionCode
            for (vCode in listOf(99L, 100L)) {
                try {
                    AppUpdateService(
                        client,
                        apkInspector = validInspector(versionCode = vCode)
                    ).downloadApk(
                        downloadUrl = "https://objects.githubusercontent.com/chand/old.apk",
                        destinationFile = File(tempDir, "old.apk"),
                        expectedSizeBytes = apkBytes.size.toLong(),
                        expectedSha256 = sha,
                        expectedVersionCode = null,
                        currentVersionCode = 100
                    )
                    fail("Expected older/equal versionCode ($vCode) rejection")
                } catch (e: AppUpdateException) {
                    assertEquals(UpdateErrorReason.OLDER_OR_EQUAL_VERSION, e.reason)
                }
            }

            // 3. Debug-signed or unexpected signer certificate rejected
            for (badCert in listOf(
                UpdateSecurityValidator.ANDROID_DEBUG_CERT_SHA256,
                "a".repeat(64)
            )) {
                try {
                    AppUpdateService(
                        client,
                        apkInspector = validInspector(signerDigests = setOf(badCert))
                    ).downloadApk(
                        downloadUrl = "https://objects.githubusercontent.com/chand/badsig.apk",
                        destinationFile = File(tempDir, "badsig.apk"),
                        expectedSizeBytes = apkBytes.size.toLong(),
                        expectedSha256 = sha,
                        expectedVersionCode = 200,
                        currentVersionCode = 100
                    )
                    fail("Expected unexpected/debug signer ($badCert) rejection")
                } catch (e: AppUpdateException) {
                    assertEquals(UpdateErrorReason.UNEXPECTED_SIGNER, e.reason)
                }
            }

            // 4. Pre-install gate in AppUpdateInstaller deletes tampered cached file & returns CHECKSUM_MISMATCH
            val info = AppUpdateInfo(
                currentVersionName = "1.0",
                currentVersionCode = 100,
                latestVersionName = "2.0",
                latestVersionCode = 200,
                releaseTitle = "Chand?! v2.0",
                releaseNotes = "- Hardened updates",
                releasePageUrl = "https://github.com/thepoo7an/chand-iran-currency-gold-crypto/releases/tag/v2.0",
                downloadUrl = "https://github.com/thepoo7an/chand-iran-currency-gold-crypto/releases/download/v2.0/Chand-arm64-v8a-release.apk",
                matchedApkName = "Chand-arm64-v8a-release.apk",
                matchedApkSizeBytes = apkBytes.size.toLong(),
                hasDirectApkAsset = true,
                expectedPackageName = UpdateSecurityValidator.EXPECTED_PACKAGE_NAME,
                expectedSha256 = sha,
                hasVerifiedMetadata = true
            )
            val cachedApk = AppUpdateInstaller.targetApkFile(context, info)
            cachedApk.parentFile?.mkdirs()
            // Tamper 1 byte in cached APK
            val tamperedBytes = apkBytes.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x01).toByte() }
            cachedApk.writeBytes(tamperedBytes)

            val launchResult = AppUpdateInstaller.launchInstallOrRequestPermission(
                context = context,
                apkFile = cachedApk,
                info = info,
                apkInspector = validInspector(),
                currentVersionCode = 100
            )
            assertEquals(InstallLaunchResult.CHECKSUM_MISMATCH, launchResult)
            assertFalse("Tampered cached APK should be deleted", cachedApk.exists())
            assertNull(AppUpdateInstaller.findAlreadyDownloadedApk(context, info, validInspector(), 100))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `checkForUpdate fetches metadata over HTTPS and rejects mismatched or forged metadata`() = runBlocking {
        val apkBytes = UpdateTestFixtures.buildMinimalSignedApkBytes()
        val sha = UpdateSecurityValidator.sha256Hex(apkBytes)
        val apkUrl = "https://github.com/thepoo7an/chand-iran-currency-gold-crypto/releases/download/build-200/Chand-arm64-v8a-release.apk"
        val metaUrl = "https://github.com/thepoo7an/chand-iran-currency-gold-crypto/releases/download/build-200/chand-update-metadata.json"

        fun releaseJson(metadataAssetUrl: String = metaUrl, apkAssetUrl: String = apkUrl): String = """
            {
              "tag_name": "build-200",
              "name": "Chand?! v2.0.0 (Build 200)",
              "body": "## What's Changed\n* Security hardening",
              "html_url": "https://github.com/thepoo7an/chand-iran-currency-gold-crypto/releases/tag/build-200",
              "prerelease": false,
              "draft": false,
              "published_at": "2026-11-25T10:00:00Z",
              "assets": [
                {
                  "name": "Chand-arm64-v8a-release.apk",
                  "browser_download_url": "$apkAssetUrl",
                  "size": ${apkBytes.size}
                },
                {
                  "name": "chand-update-metadata.json",
                  "browser_download_url": "$metadataAssetUrl",
                  "size": 512
                }
              ]
            }
        """.trimIndent()

        // Valid bound metadata -> returns verified UpdateAvailable even if extra forged signer fields appear
        val validMetaJson = """
            {
              "schema_version": 1,
              "package_name": "ir.thepoo7an.chand",
              "version_code": 200,
              "version_name": "2.0.0",
              "signer_sha256": "${"f".repeat(64)}",
              "artifacts": [
                {
                  "name": "Chand-arm64-v8a-release.apk",
                  "package_name": "ir.thepoo7an.chand",
                  "version_code": 200,
                  "version_name": "2.0.0",
                  "download_url": "$apkUrl",
                  "sha256": "$sha",
                  "size_bytes": ${apkBytes.size}
                }
              ]
            }
        """.trimIndent()

        val validClient = interceptorClient { req ->
            if (req.url.host == "api.github.com") jsonResponse(req, releaseJson())
            else jsonResponse(req, validMetaJson)
        }
        val state = AppUpdateService(validClient, apkInspector = validInspector()).checkForUpdate(
            repoSlugInput = "thepoo7an/chand-iran-currency-gold-crypto",
            currentVersionName = "1.0.0",
            currentVersionCode = 100,
            supportedAbis = arrayOf("arm64-v8a")
        )
        assertTrue(state is AppUpdateState.UpdateAvailable)
        val info = (state as AppUpdateState.UpdateAvailable).info
        assertTrue(info.hasVerifiedMetadata)
        assertEquals(sha, info.expectedSha256)
        assertEquals(UpdateSecurityValidator.EXPECTED_PACKAGE_NAME, info.expectedPackageName)
        assertEquals(200, info.latestVersionCode)

        // Plain HTTP metadata asset URL is rejected
        val httpMetaClient = interceptorClient { req ->
            jsonResponse(
                req,
                releaseJson(metadataAssetUrl = "http://github.com/thepoo7an/chand-iran-currency-gold-crypto/releases/download/build-200/chand-update-metadata.json")
            )
        }
        val httpMetaState = AppUpdateService(httpMetaClient).checkForUpdate(
            repoSlugInput = "thepoo7an/chand-iran-currency-gold-crypto",
            currentVersionName = "1.0.0",
            currentVersionCode = 100,
            supportedAbis = arrayOf("arm64-v8a")
        )
        assertTrue(httpMetaState is AppUpdateState.Error)
        assertEquals(UpdateErrorReason.HTTPS_VALIDATION_FAILED, (httpMetaState as AppUpdateState.Error).reason)

        // Metadata with mismatched artifact download_url is rejected
        val mismatchedUrlDto = UpdateReleaseMetadataDto(
            schemaVersion = 1,
            packageName = "ir.thepoo7an.chand",
            versionCode = 200,
            versionName = "2.0.0",
            artifacts = listOf(
                UpdateArtifactMetadataDto(
                    name = "Chand-arm64-v8a-release.apk",
                    packageName = "ir.thepoo7an.chand",
                    versionCode = 200,
                    versionName = "2.0.0",
                    downloadUrl = "https://github.com/other/repo/releases/download/v2/Chand-arm64-v8a-release.apk",
                    sha256 = sha,
                    sizeBytes = apkBytes.size.toLong()
                )
            )
        )
        try {
            UpdateSecurityValidator.validateAndBindArtifactMetadata(
                metadata = mismatchedUrlDto,
                expectedApkName = "Chand-arm64-v8a-release.apk",
                expectedDownloadUrl = apkUrl,
                expectedAssetSizeBytes = apkBytes.size.toLong(),
                expectedReleaseBuildCode = 200,
                currentVersionCode = 100
            )
            fail("Expected mismatched download URL in metadata to fail")
        } catch (e: AppUpdateException) {
            assertEquals(UpdateErrorReason.METADATA_INVALID, e.reason)
        }

        // Metadata bound to wrong package name is rejected
        val wrongPkgDto = mismatchedUrlDto.copy(packageName = "com.fake.app")
        try {
            UpdateSecurityValidator.validateAndBindArtifactMetadata(
                metadata = wrongPkgDto,
                expectedApkName = "Chand-arm64-v8a-release.apk",
                expectedDownloadUrl = apkUrl,
                expectedAssetSizeBytes = apkBytes.size.toLong(),
                expectedReleaseBuildCode = 200,
                currentVersionCode = 100
            )
            fail("Expected wrong package name in metadata to fail")
        } catch (e: AppUpdateException) {
            assertEquals(UpdateErrorReason.METADATA_INVALID, e.reason)
        }
    }

    @Test
    fun `every UpdateErrorReason maps to a distinct localized message in FA and EN`() {
        val faMessages = UpdateErrorReason.entries.map { FaStrings.updateErrorMessage(it) }
        val enMessages = UpdateErrorReason.entries.map { EnStrings.updateErrorMessage(it) }
        assertTrue(faMessages.all { it.isNotBlank() })
        assertTrue(enMessages.all { it.isNotBlank() })
        assertEquals(UpdateErrorReason.entries.size, faMessages.toSet().size)
        assertEquals(UpdateErrorReason.entries.size, enMessages.toSet().size)
        assertNotNull(BuildConfig.EXPECTED_RELEASE_CERT_SHA256)
        assertTrue(UpdateSecurityValidator.isValidSha256Hex(BuildConfig.EXPECTED_RELEASE_CERT_SHA256))
    }
}
