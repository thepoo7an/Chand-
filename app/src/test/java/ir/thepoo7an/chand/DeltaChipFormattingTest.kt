package ir.thepoo7an.chand

import ir.thepoo7an.chand.ui.strings.NumberFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A delta chip must show both the absolute move and its percentage: `↑322.3K` alone is invisible
 * next to a 25.8M price and says nothing about the size of the move.
 */
class DeltaChipFormattingTest {

    @Test
    fun `a Toman delta carries the absolute magnitude and the signed percentage`() {
        assertEquals(
            "↑322.3K • +1.2%",
            NumberFormatters.formatDeltaWithArrow(322_300.0, 1.2, usePersianDigits = false)
        )
        assertEquals(
            "↓1.89K • -0.7%",
            NumberFormatters.formatDeltaWithArrow(-1_890.0, -0.65, usePersianDigits = false)
        )
        assertEquals(
            "↑1.89K • +0.6%",
            NumberFormatters.formatDeltaWithArrow(1_890.0, 0.64, usePersianDigits = false)
        )
    }

    @Test
    fun `a flat delta reads as a signed zero percent instead of a bare zero`() {
        val label = NumberFormatters.formatDeltaWithArrow(0.0, 0.0, usePersianDigits = false)

        assertEquals("• 0.0%", label)
        assertTrue("the flat chip must not pretend there is a move", label.startsWith("•"))
    }

    @Test
    fun `the percentage scales with the delta instead of being dropped`() {
        val small = NumberFormatters.formatDeltaWithArrow(50.0, 0.01, usePersianDigits = false)
        val large = NumberFormatters.formatDeltaWithArrow(25_797_800.0, 12.5, usePersianDigits = false)

        assertEquals("↑50 • +0.0%", small)
        assertEquals("↑25.8M • +12.5%", large)
    }

    @Test
    fun `oil deltas stay in dollars and still carry the percentage`() {
        assertEquals(
            "↑3.50 • +2.4%",
            NumberFormatters.formatUsdDeltaWithArrow(3.5, 2.4, usePersianDigits = false)
        )
        assertEquals(
            "• 0.0%",
            NumberFormatters.formatUsdDeltaWithArrow(null, 0.0, usePersianDigits = false)
        )
    }

    @Test
    fun `Persian digits apply to the numbers but keep the separator`() {
        assertEquals(
            "↑۳۲۲.۳K • +۱.۲%",
            NumberFormatters.formatDeltaWithArrow(322_300.0, 1.2, usePersianDigits = true)
        )
    }

    @Test
    fun `formatPercent keeps its two decimal contract through the shared helper`() {
        assertEquals("+1.25%", NumberFormatters.formatPercent(1.25, usePersianDigits = false))
        assertEquals("-0.80%", NumberFormatters.formatPercent(-0.8, usePersianDigits = false))
        assertEquals("0.00%", NumberFormatters.formatPercent(0.0, usePersianDigits = false))
    }
}
