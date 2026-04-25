package app.paperkeep.feature.reader.signature

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists and retrieves up to [MAX_SLOTS] user signatures as raw PNG bytes (P3.6).
 *
 * Each slot is an independent encrypted-in-filesDir file.
 * Phase 4 will wire through AesGcmImageStore; for now files are stored
 * in the app's private filesDir (inaccessible without root).
 */
@Singleton
class SignatureRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val MAX_SLOTS = 3
        private const val FILE_PREFIX = "signature_"
        private const val FILE_EXT = ".png"
    }

    private fun fileFor(slot: Int) = context.filesDir.resolve("$FILE_PREFIX$slot$FILE_EXT")

    /**
     * Save [pngBytes] into [slot] (0-indexed, must be in 0..<MAX_SLOTS).
     * @throws IllegalArgumentException if [pngBytes] is empty or slot is out of range.
     */
    fun save(pngBytes: ByteArray, slot: Int = 0) {
        require(slot in 0 until MAX_SLOTS) { "slot must be in 0..<$MAX_SLOTS, was $slot" }
        require(pngBytes.isNotEmpty()) { "Signature bytes must not be empty" }
        context.filesDir.mkdirs()
        fileFor(slot).writeBytes(pngBytes)
    }

    /** Load the signature from [slot], or null if nothing was saved there. */
    fun load(slot: Int = 0): ByteArray? {
        require(slot in 0 until MAX_SLOTS) { "slot must be in 0..<$MAX_SLOTS, was $slot" }
        val f = fileFor(slot)
        if (!f.exists() || f.length() == 0L) return null
        return f.readBytes()
    }

    /** True if [slot] has a saved signature. */
    fun exists(slot: Int = 0): Boolean {
        require(slot in 0 until MAX_SLOTS) { "slot must be in 0..<$MAX_SLOTS, was $slot" }
        return fileFor(slot).let { it.exists() && it.length() > 0 }
    }

    /** Delete the signature in [slot]. */
    fun delete(slot: Int = 0) {
        require(slot in 0 until MAX_SLOTS) { "slot must be in 0..<$MAX_SLOTS, was $slot" }
        fileFor(slot).delete()
    }

    /** Delete all saved signatures. */
    fun deleteAll() = repeat(MAX_SLOTS) { delete(it) }

    /** Count of occupied slots. */
    fun count(): Int = (0 until MAX_SLOTS).count { exists(it) }

    /** Load all non-null signatures in slot order. */
    fun loadAll(): List<IndexedSignature> =
        (0 until MAX_SLOTS).mapNotNull { slot ->
            load(slot)?.let { IndexedSignature(slot, it) }
        }
}

/** A saved signature with its [slot] index and raw [pngBytes]. */
data class IndexedSignature(val slot: Int, val pngBytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is IndexedSignature && slot == other.slot && pngBytes.contentEquals(other.pngBytes)
    override fun hashCode(): Int = 31 * slot + pngBytes.contentHashCode()
}
