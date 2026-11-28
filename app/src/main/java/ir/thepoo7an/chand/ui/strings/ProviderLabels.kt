package ir.thepoo7an.chand.ui.strings

/**
 * Short, deterministic labels for the provider/attribution strings that come out of the network
 * layer.
 *
 * `Calculated Median (Frankfurter)` is a correct description of how a derived FX rate was produced,
 * but as a row label it was truncated to `Calculated Median (Frank…` on a phone. Every long form is
 * therefore mapped to a short form that always fits, in both languages, with the *longest* match
 * winning so `Calculated Median (Frankfurter)` never degrades into a bare `Median`.
 *
 * Unknown labels are returned trimmed and untouched: inventing a name for a provider we do not know
 * would be worse than showing it as it came.
 */
object ProviderLabels {

    private data class Alias(val long: String, val en: String, val fa: String)

    /** Longest forms first; [short] walks the list in order and returns the first hit. */
    private val ALIASES: List<Alias> = listOf(
        Alias("Calculated Median (Frankfurter)", "Median (ECB)", "میانگین (ECB)"),
        Alias("Calculated Median (Currency-API)", "Median (FX)", "میانگین (FX)"),
        Alias("Calculated Median (Currency API)", "Median (FX)", "میانگین (FX)"),
        Alias("Calculated Median", "Median", "میانگین"),
        Alias("Frankfurter", "ECB", "ECB"),
        Alias("Currency-API", "CurrencyAPI", "CurrencyAPI"),
        Alias("Free Market (USDT 24/7)", "USDT 24/7", "تتر ۲۴/۷"),
        Alias("Global FX + Cached USD", "FX + cached USD", "ارز جهانی + دلار")
    )

    /**
     * Short label for [raw]; blank stays blank so callers can still test for "no source".
     *
     * Provider labels are built dynamically in the network layer
     * (`"Calculated Median ($names)"`, where `$names` is whatever providers answered), so an exact
     * alias table can never cover them all. Anything that is a calculated median is therefore
     * classified by rule first, which keeps the label fixed for a given class of source:
     * Frankfurter-based -> `Median (ECB)`, other FX providers -> `Median (FX)`, otherwise `Median`.
     */
    fun short(raw: String, isFa: Boolean): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        calculatedMedianShort(trimmed, isFa)?.let { return it }
        val match = ALIASES.firstOrNull { alias ->
            trimmed.equals(alias.long, ignoreCase = true) || trimmed.contains(alias.long, ignoreCase = true)
        }
        if (match != null) return if (isFa) match.fa else match.en
        return trimmed
    }

    /** True when [raw] is a fixed ECB-median style label, so a caller can assert on it cheaply. */
    fun isCalculatedMedian(raw: String): Boolean =
        raw.trim().startsWith("Calculated Median", ignoreCase = true)

    private fun calculatedMedianShort(raw: String, isFa: Boolean): String? {
        if (!isCalculatedMedian(raw)) return null
        return when {
            raw.contains("Frankfurter", ignoreCase = true) -> if (isFa) "میانگین (ECB)" else "Median (ECB)"
            raw.contains("Currency", ignoreCase = true) -> if (isFa) "میانگین (FX)" else "Median (FX)"
            else -> if (isFa) "میانگین" else "Median"
        }
    }

    /**
     * Shortens every provider segment of a `•`-separated summary (`Free Market (Tehran) • TGJO • …`)
     * while keeping the order and the separator intact.
     */
    fun shortSummary(summary: String, isFa: Boolean): String =
        summary.split("•")
            .map { segment -> short(segment, isFa) }
            .filter { it.isNotEmpty() }
            .joinToString(" • ")
}
