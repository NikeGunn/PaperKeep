package app.paperkeep.core.backup.password

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Local password-strength estimator (P4.3).
 *
 * We do NOT use zxcvbn-kotlin (it pulls in dictionary blobs and a network-aware
 * API surface — both banned by CLAUDE.md). Instead we score on:
 *
 *   1. Length (the dominant factor for a backup password — there is no online
 *      attacker, only an offline one against a 128 MiB Argon2id KDF).
 *   2. Character-class diversity.
 *   3. Penalties for keyboard runs, repeated characters, and a small
 *      hand-curated common-password blocklist.
 *
 * Output is a [Score] enum (5 buckets matching zxcvbn) plus [bitsOfEntropy] for
 * UI surfacing. The MIN_LENGTH gate is enforced separately at the call site —
 * scoring still works for short passwords so we can show a meter that grows.
 */
object PasswordStrengthMeter {

    /** Minimum length policy from spec (P4.3). */
    const val MIN_LENGTH: Int = 10

    enum class Score(val label: String) {
        VERY_WEAK("Very weak"),
        WEAK("Weak"),
        FAIR("Fair"),
        STRONG("Strong"),
        VERY_STRONG("Very strong"),
    }

    data class Estimate(
        val score: Score,
        val bitsOfEntropy: Double,
        val meetsMinimumLength: Boolean,
        val warnings: List<String>,
    )

    private val COMMON: Set<String> = setOf(
        "password", "passw0rd", "password1", "qwerty", "qwertyuiop",
        "letmein", "welcome", "admin", "iloveyou", "monkey", "dragon",
        "abc123", "111111", "123456", "1234567", "12345678", "123456789",
        "1234567890", "qwerty123", "password123", "paperkeep", "scanner",
    )

    /** A few keyboard runs we screen out. Rotate / extend as needed. */
    private val RUNS: List<String> = listOf(
        "qwertyuiop", "asdfghjkl", "zxcvbnm",
        "abcdefghijklmnopqrstuvwxyz",
        "0123456789",
    )

    fun estimate(password: CharArray): Estimate {
        if (password.isEmpty()) {
            return Estimate(Score.VERY_WEAK, 0.0, meetsMinimumLength = false, warnings = listOf("Enter a password"))
        }
        val len = password.size
        val warnings = mutableListOf<String>()

        // ── 1. character classes ──
        var lower = false; var upper = false; var digit = false; var symbol = false
        var lastCh: Char? = null
        var maxRepeat = 1
        var run = 1
        for (c in password) {
            when {
                c in 'a'..'z' -> lower = true
                c in 'A'..'Z' -> upper = true
                c in '0'..'9' -> digit = true
                else -> symbol = true
            }
            if (lastCh != null && c == lastCh) {
                run++
                if (run > maxRepeat) maxRepeat = run
            } else {
                run = 1
            }
            lastCh = c
        }
        val poolSize = (if (lower) 26 else 0) + (if (upper) 26 else 0) +
            (if (digit) 10 else 0) + (if (symbol) 32 else 0)
        val bits = if (poolSize == 0) 0.0 else len * (ln(poolSize.toDouble()) / ln(2.0))

        // ── 2. penalties ──
        val joined = String(password).lowercase()
        var bitsAdjusted = bits
        if (joined in COMMON) {
            bitsAdjusted = min(bitsAdjusted, 10.0)
            warnings += "Looks like a common password"
        }
        if (RUNS.any { run -> joined.contains(run.substring(0, min(run.length, 5))) && len <= 12 }) {
            bitsAdjusted -= 6
            warnings += "Avoid keyboard patterns like \"qwerty\" or \"abcde\""
        }
        if (maxRepeat >= 3) {
            bitsAdjusted -= 4
            warnings += "Repeated characters reduce strength"
        }
        if (len < MIN_LENGTH) {
            warnings += "Use at least $MIN_LENGTH characters"
        }
        if (!(lower && upper && digit)) {
            warnings += "Mix lower-case, upper-case, and digits for more strength"
        }

        bitsAdjusted = max(0.0, bitsAdjusted)

        val score = when {
            bitsAdjusted < 28 -> Score.VERY_WEAK
            bitsAdjusted < 40 -> Score.WEAK
            bitsAdjusted < 56 -> Score.FAIR
            bitsAdjusted < 80 -> Score.STRONG
            else -> Score.VERY_STRONG
        }

        return Estimate(
            score = score,
            bitsOfEntropy = bitsAdjusted,
            meetsMinimumLength = len >= MIN_LENGTH,
            warnings = warnings.toList(),
        )
    }
}
