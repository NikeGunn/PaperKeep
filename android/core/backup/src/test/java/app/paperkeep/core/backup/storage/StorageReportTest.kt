package app.paperkeep.core.backup.storage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StorageReportTest {

    @Test
    fun emptyDir_isZero() {
        val tmp = Files.createTempDirectory("pk-empty").toFile()
        try {
            assertEquals(0L, StorageReporter.sizeOfDir(tmp))
        } finally { tmp.deleteRecursively() }
    }

    @Test
    fun nonexistentDir_isZero() {
        val nope = File("/this/does/not/exist/paperkeep/test")
        assertEquals(0L, StorageReporter.sizeOfDir(nope))
    }

    @Test
    fun sumsRecursivelyAcrossSubdirs() {
        val tmp = Files.createTempDirectory("pk-sum").toFile()
        try {
            File(tmp, "a.bin").writeBytes(ByteArray(100))
            File(tmp, "sub").mkdirs()
            File(tmp, "sub/b.bin").writeBytes(ByteArray(250))
            File(tmp, "sub/deep").mkdirs()
            File(tmp, "sub/deep/c.bin").writeBytes(ByteArray(50))
            assertEquals(400L, StorageReporter.sizeOfDir(tmp))
        } finally { tmp.deleteRecursively() }
    }

    @Test
    fun storageReport_totalsAllBuckets() {
        val r = StorageReport(
            scansBytes = 1000,
            ocrBytes = 100,
            exportsBytes = 10,
            sharesBytes = 1,
            crashBytes = 50,
            otherBytes = 5,
        )
        assertEquals(1166L, r.totalBytes)
    }

    @Test
    fun fileAsArgument_returnsItsLength() {
        val tmp = Files.createTempFile("pk-file", ".bin").toFile()
        try {
            tmp.writeBytes(ByteArray(73))
            assertEquals(73L, StorageReporter.sizeOfDir(tmp))
        } finally { tmp.delete() }
    }
}
