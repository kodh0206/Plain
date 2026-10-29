package com.example.plain.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** 일정 테이블의 생성, 조회, 수정, 삭제 연산을 제공합니다. */
@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events WHERE date = :date ORDER BY time ASC, id ASC")
    fun observeByDate(date: LocalDate): Flow<List<CalendarEventEntity>>

    @Insert
    suspend fun insert(event: CalendarEventEntity): Long

    @Update
    suspend fun update(event: CalendarEventEntity)

    @Delete
    suspend fun delete(event: CalendarEventEntity)
}
