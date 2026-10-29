package com.example.plain.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 실제 in-memory Room 구현으로 일정과 태그 CRUD를 검증합니다. */
class PlainDatabaseTest {
    private lateinit var database: PlainDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PlainDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun eventAndTagDaos_supportCreateReadUpdateDelete() = runBlocking {
        val tagId = database.scheduleTagDao().insert(
            ScheduleTagEntity(name = "업무", colorArgb = 0xFF3B82F6L),
        )
        assertEquals(1, database.scheduleTagDao().observeAll().first().size)

        val original = CalendarEventEntity(
            title = "대전 출장",
            date = LocalDate.of(2026, 10, 28),
            time = LocalTime.of(8, 30),
            locationName = "대전역",
            latitude = 36.332,
            longitude = 127.434,
            tagId = tagId,
            createdAt = Instant.parse("2026-10-27T23:00:00Z"),
            modifiedAt = Instant.parse("2026-10-27T23:00:00Z"),
        )
        val eventId = database.calendarEventDao().insert(original)
        val saved = database.calendarEventDao().observeByDate(original.date).first().single()
        assertEquals("대전 출장", saved.title)

        database.calendarEventDao().update(saved.copy(title = "대전 고객 미팅"))
        val updated = database.calendarEventDao().observeByDate(original.date).first().single()
        assertEquals(eventId, updated.id)
        assertEquals("대전 고객 미팅", updated.title)

        database.calendarEventDao().delete(updated)
        assertTrue(database.calendarEventDao().observeByDate(original.date).first().isEmpty())
    }

    private fun assertTrue(value: Boolean) = org.junit.Assert.assertTrue(value)
}
