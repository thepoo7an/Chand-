package ir.thepoo7an.chand

import ir.thepoo7an.chand.ui.strings.ProviderLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Provider rows must never truncate: the long attribution forms get short, deterministic labels in
 * both languages, and unknown providers are never renamed.
 */
class ProviderLabelsTest {

    @Test
    fun `the calculated median attribution becomes a short label`() {
        assertEquals(
            "Median (ECB)",
            ProviderLabels.short("Calculated Median (Frankfurter)", isFa = false)
        )
        assertEquals(
            "میانگین (ECB)",
            ProviderLabels.short("Calculated Median (Frankfurter)", isFa = true)
        )
    }

    @Test
    fun `the longest alias wins so the ECB detail survives`() {
        assertEquals("Median", ProviderLabels.short("Calculated Median", isFa = false))
        assertEquals("Median (ECB)", ProviderLabels.short("Calculated Median (Frankfurter)", isFa = false))
        assertEquals("ECB", ProviderLabels.short("Frankfurter", isFa = false))
    }

    @Test
    fun `every short label fits a 320dp row`() {
        val long = listOf(
            "Calculated Median (Frankfurter)",
            "Calculated Median (Currency-API)",
            "Free Market (USDT 24/7)",
            "Global FX + Cached USD"
        )
        long.forEach { raw ->
            listOf(false, true).forEach { isFa ->
                val short = ProviderLabels.short(raw, isFa)
                assertTrue(
                    "\"$short\" (from \"$raw\") must stay short enough for a 320dp row",
                    short.length <= 20
                )
            }
        }
    }

    @Test
    fun `a provider we do not know is passed through trimmed`() {
        assertEquals("Wallex", ProviderLabels.short("  Wallex  ", isFa = false))
        assertEquals("", ProviderLabels.short("   ", isFa = false))
    }

    @Test
    fun `a long summary is shortened segment by segment and keeps its order`() {
        val summary = "Calculated Median (Frankfurter) • TGJU • Wallex"

        assertEquals(
            "Median (ECB) • TGJU • Wallex",
            ProviderLabels.shortSummary(summary, isFa = false)
        )
        assertEquals(
            "میانگین (ECB) • TGJU • Wallex",
            ProviderLabels.shortSummary(summary, isFa = true)
        )
    }
}
