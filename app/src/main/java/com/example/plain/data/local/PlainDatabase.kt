package com.example.plain.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Plain의 로컬 일정 데이터베이스입니다.
 *
 * 스키마 버전 변경 시 기존 데이터를 보존하는 명시적 Migration을 추가해야 하며,
 * 릴리스 코드에서는 파괴적 마이그레이션을 사용하지 않습니다.
 */
@Database(
    entities = [CalendarEventEntity::class, ScheduleTagEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DateTimeConverters::class)
abstract class PlainDatabase : RoomDatabase() {
    abstract fun calendarEventDao(): CalendarEventDao

    abstract fun scheduleTagDao(): ScheduleTagDao
}
