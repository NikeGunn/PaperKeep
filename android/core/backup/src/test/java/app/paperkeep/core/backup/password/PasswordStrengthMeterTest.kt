package app.paperkeep.core.backup.password

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordStrengthMeterTest {

    @Test
    fun emptyInput_isVeryWeak() {
        val r = PasswordStrengthMeter.estimate(charArrayOf())
        assertEquals(PasswordStrengthMeter.Score.VERY_WEAK, r.score)
        assertFalse(r.meetsMinimumLength)
        assertEquals(0.0, r.bitsOfEntropy, 0.0)
    }

    @Test
    fun shortPassword_failsMinimumLength() {
        val r = PasswordStrengthMeter.estimate("abc12".toCharArray())
        assertFalse(r.meetsMinimumLength)
        assertTrue(r.warnings.any { it.contains(PasswordStrengthMeter.MIN_LENGTH.toString()) })
    }

    @Test
    fun commonPassword_isHeavilyPenalised() {
        val r = PasswordStrengthMeter.estimate("password".toCharArray())
        assertTrue(r.score == PasswordStrengthMeter.Score.VERY_WEAK || r.score == PasswordStrengthMeter.Score.WEAK)
        assertTrue(r.warnings.any { it.contains("common", ignoreCase = true) })
    }

    @Test
    fun mixedClasses_at12Chars_isFairOrBetter() {
        val r = PasswordStrengthMeter.estimate("Tr0pic@lFire".toCharArray())
        assertTrue(r.meetsMinimumLength)
        assertTrue(
            "expected FAIR/STRONG/VERY_STRONG, got ${r.score}",
            r.score.ordinal >= PasswordStrengthMeter.Score.FAIR.ordinal,
        )
    }

    @Test
    fun longRandom_isVeryStrong() {
        val r = PasswordStrengthMeter.estimate("X9!hQp7L#m2nWv8FsBz3T6kY".toCharArray())
        assertEquals(PasswordStrengthMeter.Score.VERY_STRONG, r.score)
        assertTrue(r.bitsOfEntropy >= 80.0)
    }

    @Test
    fun keyboardRun_isPenalised() {
        val r = PasswordStrengthMeter.estimate("qwerty123".toCharArray())
        assertTrue(r.warnings.any { it.contains("pattern", ignoreCase = true) || it.contains("keyboard", ignoreCase = true) })
    }

    @Test
    fun repeatedChars_arePenalised() {
        val r = PasswordStrengthMeter.estimate("aaaaaaaaaaa".toCharArray())
        assertTrue(r.warnings.any { it.contains("repeat", ignoreCase = true) })
    }

    @Test
    fun bitsOfEntropy_isNeverNegative() {
        val r = PasswordStrengthMeter.estimate("a".toCharArray())
        assertTrue(r.bitsOfEntropy >= 0.0)
    }

    @Test
    fun minLengthGate_at10_passes() {
        val r = PasswordStrengthMeter.estimate("abcdefghij".toCharArray())
        assertTrue(r.meetsMinimumLength)
    }

    @Test
    fun classDiversity_warning_whenAllLowercase() {
        val r = PasswordStrengthMeter.estimate("abcdefghijklmn".toCharArray())
        assertTrue(r.warnings.any { it.contains("Mix", ignoreCase = true) })
    }
}
