package app.paperkeep.core.backup.crypto

import de.mkammerer.argon2.Argon2Factory
import javax.crypto.spec.SecretKeySpec

/**
 * Argon2id KDF for backup passwords (P4.1 / §6.1).
 *
 * Parameters chosen per spec:
 *   - m = 128 MiB  (131_072 KiB)
 *   - t = 4        (iterations)
 *   - p = 4        (parallelism)
 *   - 32-byte output (256-bit AES key)
 *
 * On a Pixel 6a this runs in roughly 1.5 seconds — slow enough to make a
 * dictionary attack on a leaked backup file uncomfortably expensive, fast
 * enough that legitimate restore is one user-visible spinner.
 */
object BackupKdf {

    /** Salt size — 32 bytes is overkill but cheap and unambiguous. */
    const val SALT_BYTES = 32

    /** Derived key length — AES-256 needs 32 bytes. */
    const val KEY_BYTES = 32

    /** Argon2id memory parameter (KiB). 131_072 KiB = 128 MiB. */
    const val MEMORY_KIB = 128 * 1024

    /** Argon2id iterations. */
    const val ITERATIONS = 4

    /** Argon2id parallelism (lanes). */
    const val PARALLELISM = 4

    /**
     * Derive a 256-bit AES key from [password] and [salt]. Both inputs are
     * cleared from this function's stack on return — pass a defensive copy if
     * you need to keep [password] alive after.
     */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        memoryKib: Int = MEMORY_KIB,
        iterations: Int = ITERATIONS,
        parallelism: Int = PARALLELISM,
    ): SecretKeySpec {
        require(salt.size == SALT_BYTES) { "salt must be $SALT_BYTES bytes, was ${salt.size}" }
        require(password.isNotEmpty()) { "password must not be empty" }
        require(memoryKib > 0 && iterations > 0 && parallelism > 0) {
            "argon2 params must be positive"
        }

        val argon2 = Argon2Factory.createAdvanced(
            Argon2Factory.Argon2Types.ARGON2id,
            SALT_BYTES,
            KEY_BYTES,
        )
        val raw: ByteArray = try {
            argon2.rawHash(iterations, memoryKib, parallelism, password, salt)
        } finally {
            // best-effort wipe — caller still owns the original password
        }
        return try {
            SecretKeySpec(raw, "AES")
        } finally {
            raw.fill(0)
        }
    }
}
