package ir.thepoo7an.chand.network

import android.util.Base64
import ir.thepoo7an.chand.util.AppLog
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Outcome of checking the detached signature embedded in `chand-live-config.json`. */
sealed class LiveConfigSignatureCheck {
    /** Signature is present and verified against [keyId]. */
    data class Valid(val keyId: String) : LiveConfigSignatureCheck()

    /** No signature/keyId pair in the payload (legacy or fork config). */
    object Unsigned : LiveConfigSignatureCheck()

    /** Signature present but the config must NOT be trusted. */
    data class Invalid(val reason: String) : LiveConfigSignatureCheck()
}

/**
 * Verifies the signed live configuration (ECDSA P-256 / SHA-256, DER signature, base64 encoded).
 *
 * The signed payload is a canonical rendering of the JSON document (everything except the
 * top-level `signature`/`keyId` members) so that reformatting the file does not invalidate the
 * signature while any semantic change does:
 *
 * 1. object keys sorted ascending (ASCII keys), no whitespace;
 * 2. strings escaped minimally (`"`, `\` and C0 controls only), everything else as raw UTF-8;
 * 3. numbers keep their literal source text (`4.0` never becomes `4`).
 *
 * `scripts/live_config_canonical.py` implements the exact same rules for the signing tooling, and
 * `LiveConfigSignatureTest` cross-checks this implementation against the committed config file.
 */
object LiveConfigSignature {

    const val SIGNATURE_FIELD = "signature"
    const val KEY_ID_FIELD = "keyId"

    private const val ALGORITHM = "SHA256withECDSA"
    private const val KEY_ALGORITHM = "EC"

    private val json = Json { isLenient = true }

    /**
     * Parses `keyId:base64,keyId2:base64` (the `LIVE_CONFIG_PUBLIC_KEYS` build constant).
     * Malformed entries are dropped instead of failing the whole trust store.
     */
    fun parseTrustedKeys(encoded: String): Map<String, String> {
        if (encoded.isBlank()) return emptyMap()
        val keys = LinkedHashMap<String, String>()
        encoded.split(',').forEach { chunk ->
            val trimmed = chunk.trim()
            if (trimmed.isEmpty()) return@forEach
            val separator = trimmed.indexOf(':')
            if (separator <= 0 || separator == trimmed.lastIndex) return@forEach
            val keyId = trimmed.substring(0, separator).trim()
            val value = trimmed.substring(separator + 1).trim()
            if (keyId.isNotEmpty() && value.isNotEmpty()) keys[keyId] = value
        }
        return keys
    }

    /** Canonical UTF-8 payload covered by the signature, or `null` when [rawJson] is unusable. */
    fun canonicalPayload(rawJson: String): String? {
        val root = runCatching { json.parseToJsonElement(rawJson.trim()) }.getOrNull() as? JsonObject
            ?: return null
        val stripped = JsonObject(root.filterKeys { it != SIGNATURE_FIELD && it != KEY_ID_FIELD })
        return StringBuilder(stripped.entries.sumOf { it.key.length + 8 })
            .also { builder -> appendCanonical(builder, stripped) }
            .toString()
    }

    fun verify(
        rawJson: String,
        trustedKeys: Map<String, String>
    ): LiveConfigSignatureCheck {
        val root = runCatching { json.parseToJsonElement(rawJson.trim()) }.getOrNull() as? JsonObject
            ?: return LiveConfigSignatureCheck.Invalid("invalid_json")

        val signatureB64 = root.stringMember(SIGNATURE_FIELD)
        val keyId = root.stringMember(KEY_ID_FIELD)
        if (signatureB64.isNullOrBlank() || keyId.isNullOrBlank()) {
            return LiveConfigSignatureCheck.Unsigned
        }
        // No configured trust store means the build opted out of signature enforcement.
        if (trustedKeys.isEmpty()) return LiveConfigSignatureCheck.Unsigned

        val publicKeyB64 = trustedKeys[keyId]
            ?: return LiveConfigSignatureCheck.Invalid("unknown_key")

        val payload = canonicalPayload(rawJson)
            ?: return LiveConfigSignatureCheck.Invalid("invalid_json")

        return try {
            val publicKeyBytes = Base64.decode(publicKeyB64, Base64.DEFAULT)
            val publicKey = KeyFactory.getInstance(KEY_ALGORITHM)
                .generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val verifier = Signature.getInstance(ALGORITHM).apply {
                initVerify(publicKey)
                update(payload.toByteArray(Charsets.UTF_8))
            }
            val signatureBytes = Base64.decode(signatureB64, Base64.DEFAULT)
            if (verifier.verify(signatureBytes)) {
                LiveConfigSignatureCheck.Valid(keyId)
            } else {
                LiveConfigSignatureCheck.Invalid("signature_mismatch")
            }
        } catch (t: Throwable) {
            AppLog.w("Live-config signature verification failed to run", t)
            LiveConfigSignatureCheck.Invalid("verify_error")
        }
    }

    private fun JsonObject.stringMember(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()

    private fun appendCanonical(builder: StringBuilder, element: JsonElement) {
        when (element) {
            is JsonObject -> {
                builder.append('{')
                element.keys.sorted().forEachIndexed { index, key ->
                    if (index > 0) builder.append(',')
                    appendEscapedString(builder, key)
                    builder.append(':')
                    appendCanonical(builder, element.getValue(key))
                }
                builder.append('}')
            }
            is JsonArray -> {
                builder.append('[')
                element.forEachIndexed { index, item ->
                    if (index > 0) builder.append(',')
                    appendCanonical(builder, item)
                }
                builder.append(']')
            }
            is JsonNull -> builder.append("null")
            is JsonPrimitive -> {
                if (element.isString) {
                    appendEscapedString(builder, element.content)
                } else {
                    // Booleans and numbers keep their literal source text.
                    builder.append(element.content)
                }
            }
        }
    }

    private fun appendEscapedString(builder: StringBuilder, value: String) {
        builder.append('"')
        value.forEach { ch ->
            when {
                ch == '"' -> builder.append("\\\"")
                ch == '\\' -> builder.append("\\\\")
                ch == '\b' -> builder.append("\\b")
                ch == '\u000C' -> builder.append("\\f")
                ch == '\n' -> builder.append("\\n")
                ch == '\r' -> builder.append("\\r")
                ch == '\t' -> builder.append("\\t")
                ch.code < 0x20 -> builder
                    .append("\\u")
                    .append(ch.code.toString(16).padStart(4, '0'))
                else -> builder.append(ch)
            }
        }
        builder.append('"')
    }
}
