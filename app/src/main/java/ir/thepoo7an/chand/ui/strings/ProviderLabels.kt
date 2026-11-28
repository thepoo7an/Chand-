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

    /** Short label for [raw]; blank stays blank so callers can still test for "no source". */
    fun short(raw: String, isFa: Boolean): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val match = ALIASES.firstOrNull { alias ->
            trimmed.equals(alias.long, ignoreCase = true) || trimmed.contains(alias.long, ignoreCase = true)
        }
        if (match != null) return if (isFa) match.fa else match.en
        return trimmed
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
