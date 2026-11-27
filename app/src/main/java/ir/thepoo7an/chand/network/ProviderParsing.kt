package ir.thepoo7an.chand.network

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * One home for the payload helpers every provider needs, so an extracted provider service never
 * has to depend back on the aggregator it was split out of.
 *
 * These three helpers were lifted verbatim from `MarketRemoteDataSource` while it is being split
 * one provider per change; the aggregator keeps thin delegating shims so the remaining providers
 * keep compiling until their turn comes.
 */
internal object ProviderParsing {

    /**
     * Parses a numeric field that providers are generous about: HTML fragments and thousands
     * separators are tolerated, Persian/Arabic digits and typographic minus signs are normalised,
     * and non-numbers come back as `null` instead of `0.0` (a zero price is never invented).
     */
    fun parseNumeric(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val noHtml = raw.replace(Regex("<[^>]*>"), " ")
        val ascii = StringBuilder(noHtml.length)
        for (ch in noHtml) {
            ascii.append(
                when (ch) {
                    in '۰'..'۹' -> ('0' + (ch - '۰'))
                    in '٠'..'٩' -> ('0' + (ch - '٠'))
                    '−', '–' -> '-'
                    ',', '٬', ' ', '\u00A0' -> continue
                    else -> ch
                }
            )
        }
        val match = Regex("-?\\d+(?:\\.\\d+)?").find(ascii.toString()) ?: return null
        return match.value.toDoubleOrNull()
    }

    /** Parses the Tehran-local timestamps TGJU publishes (`yyyy-MM-dd HH:mm[:ss]`). */
    fun parseTgjuTimestampMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val tz = TimeZone.getTimeZone("Asia/Tehran")
        val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss")
        for (pattern in patterns) {
            val sdf = SimpleDateFormat(pattern, Locale.US).apply { timeZone = tz }
            val parsed = runCatching { sdf.parse(trimmed)?.time }.getOrNull()
            if (parsed != null && parsed > 0L) return parsed
        }
        return null
    }

    /**
     * Source timestamp of a quote: epoch seconds or millis, an ISO-8601 string, or a TGJU-style
     * local timestamp, looked up through the keys providers actually use. Returns `0L` — "unknown"
     * — rather than pretending the quote is freshly fetched.
     */
    fun parseQuoteTimestampMillis(vararg objects: JSONObject?): Long {
        val candidateKeys =
            listOf("updated_at", "updatedAt", "lastUpdate", "last_update", "timestamp", "ts", "time")
        for (obj in objects) {
            if (obj == null) continue
            for (key in candidateKeys) {
                if (!obj.has(key) || obj.isNull(key)) continue
                val rawVal = obj.opt(key) ?: continue
                when (rawVal) {
                    is Number -> {
                        val num = rawVal.toLong()
                        if (num > 0L) {
                            return if (num < 10_000_000_000L) num * 1000L else num
                        }
                    }
                    is String -> {
                        val trimmed = rawVal.trim()
                        if (trimmed.isEmpty()) continue
                        val numeric = trimmed.toLongOrNull()
                        if (numeric != null && numeric > 0L) {
                            return if (numeric < 10_000_000_000L) numeric * 1000L else numeric
                        }
                        FinancialApiService.parseIsoTimestampMillis(trimmed)?.let { return it }
                        parseTgjuTimestampMillis(trimmed)?.let { return it }
                    }
                }
            }
        }
        return 0L
    }
}
