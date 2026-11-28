package ir.thepoo7an.chand

import ir.thepoo7an.chand.ui.strings.NumberFormatters
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One formatting policy for every price surface: below 1M the full integer, at or above 1M the
 * compact two-decimal form. `$292,890` and `255.56M` must never be able to appear side by side.
 */
class NumberFormattingPolicyTest {

    @Test
    fun `below one million keeps the full integer with separators`() {
        assertEquals("292,890", NumberFormatters.formatPrice(292_890.0, usePersianDigits = false))
        assertEquals("999,999", NumberFormatters.formatPrice(999_999.0, usePersianDigits = false))
        assertEquals("950", NumberFormatters.formatPrice(950.0, usePersianDigits = false))
    }

    @Test
    fun `one million and above is compact with two decimals at most`() {
        assertEquals("1M", NumberFormatters.formatPrice(1_000_000.0, usePersianDigits = false))
        assertEquals("12.9M", NumberFormatters.formatPrice(12_900_000.0, usePersianDigits = false))
        assertEquals("25.8M", NumberFormatters.formatPrice(25_797_800.0, usePersianDigits = false))
        assertEquals("255.56M", NumberFormatters.formatPrice(255_560_000.0, usePersianDigits = false))
        assertEquals("61.25B", NumberFormatters.formatPrice(61_250_000_000.0, usePersianDigits = false))
    }

    @Test
    fun `cards, axis labels and widgets all go through the same policy`() {
        val samples = listOf(292_890.0, 999_999.0, 1_000_000.0, 25_797_800.0, 255_560_000.0)
        samples.forEach { amount ->
            assertEquals(
                "formatCardPrice must not have a rule of its own for $amount",
                NumberFormatters.formatPrice(amount, usePersianDigits = false),
                NumberFormatters.formatCardPrice(amount, usePersianDigits = false)
            )
        }
    }

    @Test
    fun `the converter shares the policy above one million and keeps sub-unit precision below`() {
        assertEquals(
            NumberFormatters.formatPrice(2_550_000_000.0, usePersianDigits = false),
            NumberFormatters.formatConverterAmount(2_550_000_000.0, usePersianDigits = false)
        )
        assertEquals("2.55B", NumberFormatters.formatConverterAmount(2_550_000_000.0, usePersianDigits = false))
        assertEquals("292,890", NumberFormatters.formatConverterAmount(292_890.0, usePersianDigits = false))
        assertEquals("0.00001425", NumberFormatters.formatConverterAmount(0.00001425, usePersianDigits = false))
        assertEquals("0", NumberFormatters.formatConverterAmount(0.0, usePersianDigits = false))
    }

    @Test
    fun `Persian digits follow the same policy instead of a separate one`() {
        assertEquals("۲۹۲,۸۹۰", NumberFormatters.formatPrice(292_890.0, usePersianDigits = true))
        assertEquals("۲۵.۸M", NumberFormatters.formatPrice(25_797_800.0, usePersianDigits = true))
    }

    @Test
    fun `compact magnitudes agree with the price policy on scale`() {
        assertEquals("950", NumberFormatters.formatCompactMagnitude(950.0))
        assertEquals("322.3K", NumberFormatters.formatCompactMagnitude(322_300.0))
        assertEquals("12.9M", NumberFormatters.formatCompactMagnitude(12_900_000.0))
        assertEquals("255.56M", NumberFormatters.formatCompactMagnitude(255_560_000.0))
        assertEquals("0", NumberFormatters.formatCompactMagnitude(0.0))
    }

    @Test
    fun `non-finite input never leaks a NaN into the UI`() {
        assertEquals("0", NumberFormatters.formatPrice(Double.NaN, usePersianDigits = false))
        assertEquals("0", NumberFormatters.formatPrice(Double.POSITIVE_INFINITY, usePersianDigits = false))
        assertEquals("0", NumberFormatters.formatCompactMagnitude(Double.NaN))
    }
}
