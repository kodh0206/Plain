package com.example.plain.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 일정 태그 테이블의 생성, 조회, 수정, 삭제 연산을 제공합니다. */
@Dao
interface ScheduleTagDao {
    @Query("SELECT * FROM schedule_tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ScheduleTagEntity>>

    @Insert
    suspend fun insert(tag: ScheduleTagEntity): Long

    @Update
    suspend fun update(tag: ScheduleTagEntity)

    @Delete
    suspend fun delete(tag: ScheduleTagEntity)
}
