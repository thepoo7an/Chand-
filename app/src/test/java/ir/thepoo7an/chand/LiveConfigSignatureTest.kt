package ir.thepoo7an.chand

import ir.thepoo7an.chand.network.LiveConfigSignature
import ir.thepoo7an.chand.network.LiveConfigSignatureCheck
import ir.thepoo7an.chand.network.RemoteConfigService
import ir.thepoo7an.chand.network.RemoteConfigSyncResult
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the live-config trust boundary. The fixtures below were produced with
 * `scripts/sign-live-config.py` and a throw-away key pair whose public half is [TEST_PUBLIC_KEY].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class)
class LiveConfigSignatureTest {

    private val trustedTestKeys = mapOf("test-key" to TEST_PUBLIC_KEY)

    @Test
    fun `canonical payload ignores formatting but keeps semantic content`() {
        val pretty = requireNotNull(LiveConfigSignature.canonicalPayload(SIGNED_FIXTURE))
        val compact = requireNotNull(LiveConfigSignature.canonicalPayload(COMPACT_FIXTURE))
        assertEquals(pretty, compact)
        assertTrue("canonical payload must be single-line", '\n' !in pretty)
        assertTrue(
            "top-level keys must be sorted and signature fields stripped",
            pretty.startsWith("{\"configVersion\":12,\"disabledAssetIds\":[\"SHIB\"]")
        )
        // Number literals are preserved verbatim, so `4.0` can never be signed as `4`.
        assertTrue(pretty.contains("\"cryptoDisagreementThresholdPct\":4.0"))
        // Non-ASCII text stays raw UTF-8 rather than being escaped.
        assertTrue(pretty.contains("پیکربندی آزمون"))
    }

    @Test
    fun `canonical payload rejects non-object documents`() {
        assertNull(LiveConfigSignature.canonicalPayload("[]"))
        assertNull(LiveConfigSignature.canonicalPayload("not json at all"))
    }

    @Test
    fun `valid signature is accepted for the matching key id`() {
        val result = LiveConfigSignature.verify(SIGNED_FIXTURE, trustedTestKeys)
        assertTrue("expected Valid but was $result", result is LiveConfigSignatureCheck.Valid)
        assertEquals("test-key", (result as LiveConfigSignatureCheck.Valid).keyId)
    }

    @Test
    fun `reformatting the same config keeps the signature valid`() {
        val result = LiveConfigSignature.verify(COMPACT_FIXTURE, trustedTestKeys)
        assertTrue("expected Valid but was $result", result is LiveConfigSignatureCheck.Valid)
    }

    @Test
    fun `tampered endpoint override is rejected`() {
        val tampered = SIGNED_FIXTURE.replace(
            "https://mirror.example.com/tgju",
            "https://attacker.example.net/tgju"
        )
        assertEquals(
            LiveConfigSignatureCheck.Invalid("signature_mismatch"),
            LiveConfigSignature.verify(tampered, trustedTestKeys)
        )
    }

    @Test
    fun `tampering with a numeric threshold is rejected`() {
        val tampered = SIGNED_FIXTURE.replace("4.0", "40.0")
        assertEquals(
            LiveConfigSignatureCheck.Invalid("signature_mismatch"),
            LiveConfigSignature.verify(tampered, trustedTestKeys)
        )
    }

    @Test
    fun `unknown key id is rejected even with a well-formed signature`() {
        assertEquals(
            LiveConfigSignatureCheck.Invalid("unknown_key"),
            LiveConfigSignature.verify(SIGNED_FIXTURE, mapOf("other" to TEST_PUBLIC_KEY))
        )
    }

    @Test
    fun `unsigned config is reported as unsigned so the caller can decide`() {
        assertEquals(
            LiveConfigSignatureCheck.Unsigned,
            LiveConfigSignature.verify(UNSIGNED_FIXTURE, trustedTestKeys)
        )
        // ...and is still reported as unsigned when the build has no trust store at all.
        assertEquals(
            LiveConfigSignatureCheck.Unsigned,
            LiveConfigSignature.verify(SIGNED_FIXTURE, emptyMap())
        )
    }

    @Test
    fun `garbage signature material fails closed`() {
        val truncated = SIGNED_FIXTURE.replace(
            "MEUCIGln6kUZ4ULKME06hFCkKqifrsKEnHtxFIZ8uPaJTXgjAiEA5mYIB8/JRRivExfe9pkDzvtgV6FyFCul24UjjSdRFFc=",
            "MEUCIA=="
        )
        val truncatedResult = LiveConfigSignature.verify(truncated, trustedTestKeys)
        assertTrue(
            "truncated signature must be Invalid but was $truncatedResult",
            truncatedResult is LiveConfigSignatureCheck.Invalid
        )

        val notBase64 = SIGNED_FIXTURE.replace("\"keyId\": \"test-key\"", "\"keyId\": \"test-key\"")
            .replace("MEUCIGln6kUZ4ULKME06hFCkKqifrsKEnHtxFIZ8uPaJTXgjAiEA5mYIB8/JRRivExfe9pkDzvtgV6FyFCul24UjjSdRFFc=", "!!!not-base64!!!")
        val notBase64Result = LiveConfigSignature.verify(notBase64, trustedTestKeys)
        assertTrue(
            "unparseable signature must be Invalid but was $notBase64Result",
            notBase64Result is LiveConfigSignatureCheck.Invalid
        )
    }

    @Test
    fun `trust store parser accepts comma separated keys and drops malformed entries`() {
        val keys = LiveConfigSignature.parseTrustedKeys(
            " k1:AAAA ,, broken, k2:BBBB:k3:CCCC, :nokey, k4: "
        )
        assertEquals(setOf("k1", "k2"), keys.keys)
        assertEquals("AAAA", keys["k1"])
        // Everything after the first colon belongs to the base64 value.
        assertEquals("BBBB:k3:CCCC", keys["k2"])
        assertTrue(LiveConfigSignature.parseTrustedKeys("").isEmpty())
    }

    @Test
    fun `repository config verifies against the embedded production key`() {
        // Cross-implementation guard: the committed config is signed by the Python tooling, so
        // this test fails if the Kotlin canonicalization and `scripts/live_config_canonical.py`
        // ever disagree.
        val configFile = listOf(
            File("../chand-live-config.json"),
            File("chand-live-config.json")
        ).firstOrNull { it.exists() }
        assertTrue("chand-live-config.json missing", configFile != null)
        val trustedKeys = LiveConfigSignature.parseTrustedKeys(BuildConfig.LIVE_CONFIG_PUBLIC_KEYS)
        assertTrue("LIVE_CONFIG_PUBLIC_KEYS must not be empty", trustedKeys.isNotEmpty())
        val result = LiveConfigSignature.verify(configFile!!.readText(), trustedKeys)
        assertTrue("expected Valid for the committed config but was $result", result is LiveConfigSignatureCheck.Valid)
    }

    @Test
    fun `fetchLiveConfig applies a correctly signed document`() = runTest {
        val result = signedService(SIGNED_FIXTURE).fetchLiveConfig(
            repoSlug = "thepoo7an/chand-iran-currency-gold-crypto",
            appVersionCode = 100
        )
        assertTrue("expected Applied but was $result", result is RemoteConfigSyncResult.Applied)
        assertEquals(
            12,
            (result as RemoteConfigSyncResult.Applied).config.configVersion
        )
    }

    @Test
    fun `fetchLiveConfig rejects a tampered document end to end`() = runTest {
        val tampered = SIGNED_FIXTURE.replace(
            "https://mirror.example.com/tgju",
            "https://attacker.example.net/tgju"
        )
        val result = signedService(tampered).fetchLiveConfig(
            repoSlug = "thepoo7an/chand-iran-currency-gold-crypto",
            appVersionCode = 100
        )
        assertTrue("expected Error but was $result", result is RemoteConfigSyncResult.Error)
        assertEquals(
            "config_signature_signature_mismatch",
            (result as RemoteConfigSyncResult.Error).reason
        )
    }

    @Test
    fun `fetchLiveConfig rejects an unsigned document once a trust store is configured`() = runTest {
        val result = signedService(UNSIGNED_FIXTURE).fetchLiveConfig(
            repoSlug = "thepoo7an/chand-iran-currency-gold-crypto",
            appVersionCode = 100
        )
        assertTrue("expected Error but was $result", result is RemoteConfigSyncResult.Error)
        assertEquals("config_unsigned", (result as RemoteConfigSyncResult.Error).reason)
    }

    private fun signedService(body: String): RemoteConfigService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return RemoteConfigService(client, trustedConfigKeys = trustedTestKeys)
    }

    private companion object {
        /** Public half of the throw-away fixture key (base64 SubjectPublicKeyInfo, P-256). */
        const val TEST_PUBLIC_KEY =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtFAPy5JJ5/GnsHGitkihpdDFTLU7ZYApcep6K9fmdfAoaBsj" +
                "W/o3lij8IOMu4XVhljTod+mmsfXDp0J88DinSg=="

        val SIGNED_FIXTURE = """
            {
              "configVersion": 12,
              "minAppVersionCode": 1,
              "summaryEn": "Fixture config",
              "summaryFa": "پیکربندی آزمون",
              "endpoints": {
                "tgjuAjaxUrl": "https://mirror.example.com/tgju",
                "frankfurterLatestUrls": ["https://api.example.org/latest"]
              },
              "validation": {
                "cryptoDisagreementThresholdPct": 4.0,
                "fiatDisagreementThresholdPct": 3.5
              },
              "features": {
                "enableInAppApkUpdates": true,
                "minAutoRefreshSeconds": 10
              },
              "disabledAssetIds": ["SHIB"],
              "liveNotice": null,
              "signature": "MEUCIGln6kUZ4ULKME06hFCkKqifrsKEnHtxFIZ8uPaJTXgjAiEA5mYIB8/JRRivExfe9pkDzvtgV6FyFCul24UjjSdRFFc=",
              "keyId": "test-key"
            }
        """.trimIndent()

        val COMPACT_FIXTURE = """{"configVersion":12,"minAppVersionCode":1,"summaryEn":"Fixture config","summaryFa":"پیکربندی آزمون","endpoints":{"tgjuAjaxUrl":"https://mirror.example.com/tgju","frankfurterLatestUrls":["https://api.example.org/latest"]},"validation":{"cryptoDisagreementThresholdPct":4.0,"fiatDisagreementThresholdPct":3.5},"features":{"enableInAppApkUpdates":true,"minAutoRefreshSeconds":10},"disabledAssetIds":["SHIB"],"liveNotice":null,"signature":"MEUCIGln6kUZ4ULKME06hFCkKqifrsKEnHtxFIZ8uPaJTXgjAiEA5mYIB8/JRRivExfe9pkDzvtgV6FyFCul24UjjSdRFFc=","keyId":"test-key"}"""

        val UNSIGNED_FIXTURE = """
            {
              "configVersion": 12,
              "minAppVersionCode": 1,
              "summaryEn": "Fixture config",
              "endpoints": { "tgjuAjaxUrl": "https://mirror.example.com/tgju" }
            }
        """.trimIndent()
    }
}
