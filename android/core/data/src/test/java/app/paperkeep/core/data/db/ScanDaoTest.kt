package app.paperkeep.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for 1B.16: Room ScanEntity + ScanDao.
 * Uses an in-memory database — no real file I/O.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScanDaoTest {

    private lateinit var db: PaperkeepDatabase
    private lateinit var dao: ScanDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PaperkeepDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.scanDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── 1B.16: Insert → query → same data ─────────────────────────────────────

    @Test
    fun insert_andGetById_returnsSameEntity() = runTest {
        val entity = buildEntity(id = "abc-123", title = "My Receipt")

        dao.insert(entity)
        val result = dao.getById("abc-123")

        assertNotNull(result)
        assertEquals(entity, result)
    }

    @Test
    fun insert_andGetAll_containsInsertedEntity() = runTest {
        val entity = buildEntity(id = "x1", title = "Contract")

        dao.insert(entity)
        val all = dao.getAll()

        assertTrue(all.any { it.id == "x1" })
    }

    @Test
    fun observeAll_emitsInsertedEntities() = runTest {
        dao.insert(buildEntity("a", "Doc A"))
        dao.insert(buildEntity("b", "Doc B"))

        val list = dao.observeAll().first()

        assertEquals(2, list.size)
    }

    // ── 1B.16: Empty list when no scans ───────────────────────────────────────

    @Test
    fun getAll_whenEmpty_returnsEmptyList() = runTest {
        val result = dao.getAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun observeAll_whenEmpty_emitsEmptyList() = runTest {
        val result = dao.observeAll().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun count_whenEmpty_returnsZero() = runTest {
        assertEquals(0, dao.count())
    }

    // ── 1B.16: Delete removes entity ──────────────────────────────────────────

    @Test
    fun delete_removesEntity() = runTest {
        val entity = buildEntity("del-1", "To Delete")
        dao.insert(entity)
        assertEquals(1, dao.count())

        dao.delete(entity)

        assertEquals(0, dao.count())
        assertNull(dao.getById("del-1"))
    }

    @Test
    fun deleteById_removesCorrectEntity() = runTest {
        dao.insert(buildEntity("keep", "Keep"))
        dao.insert(buildEntity("remove", "Remove"))

        dao.deleteById("remove")

        assertNull(dao.getById("remove"))
        assertNotNull(dao.getById("keep"))
    }

    // ── 1B.16: Unicode in title ───────────────────────────────────────────────

    @Test
    fun insert_unicodeTitle_roundTrips() = runTest {
        val unicodeTitle = "文書スキャン — ক্যামেরা — العربية — 🗒️"
        val entity = buildEntity("unicode-1", unicodeTitle)

        dao.insert(entity)
        val result = dao.getById("unicode-1")

        assertEquals(unicodeTitle, result?.title)
    }

    @Test
    fun insert_emojiTitle_roundTrips() = runTest {
        val emojiTitle = "Receipt 🧾 #42"
        val entity = buildEntity("emoji-1", emojiTitle)

        dao.insert(entity)
        val result = dao.getById("emoji-1")

        assertEquals(emojiTitle, result?.title)
    }

    // ── 1B.16: Insert multiple / observeRecent ────────────────────────────────

    @Test
    fun observeRecent_withLimit_returnsCorrectCount() = runTest {
        repeat(5) { i -> dao.insert(buildEntity("id-$i", "Scan $i", createdAt = i.toLong())) }

        val recent = dao.observeRecent(3).first()

        assertEquals(3, recent.size)
    }

    @Test
    fun observeAll_orderedByCreatedAtDesc() = runTest {
        dao.insert(buildEntity("old", "Old", createdAt = 1000L))
        dao.insert(buildEntity("new", "New", createdAt = 9000L))
        dao.insert(buildEntity("mid", "Mid", createdAt = 5000L))

        val list = dao.observeAll().first()

        assertEquals("new", list[0].id)
        assertEquals("mid", list[1].id)
        assertEquals("old", list[2].id)
    }

    // ── 1B.16: Update ─────────────────────────────────────────────────────────

    @Test
    fun update_changesTitle() = runTest {
        val entity = buildEntity("upd-1", "Original")
        dao.insert(entity)

        dao.update(entity.copy(title = "Updated"))

        val result = dao.getById("upd-1")
        assertEquals("Updated", result?.title)
    }

    // ── 1B.16: Replace on conflict ────────────────────────────────────────────

    @Test
    fun insertAll_replaceOnConflict() = runTest {
        dao.insert(buildEntity("dup", "First"))
        dao.insertAll(listOf(buildEntity("dup", "Second")))

        val result = dao.getById("dup")
        assertEquals("Second", result?.title)
        assertEquals(1, dao.count())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildEntity(
        id: String,
        title: String,
        pageCount: Int = 1,
        createdAt: Long = System.currentTimeMillis(),
    ) = ScanEntity(
        id = id,
        title = title,
        pageCount = pageCount,
        createdAt = createdAt,
        updatedAt = createdAt,
        originalPath = "/data/data/app.paperkeep/$id.enc",
        thumbnailPath = "/data/data/app.paperkeep/$id.thumb.enc",
        widthPx = 2560,
        heightPx = 1920,
    )
}
