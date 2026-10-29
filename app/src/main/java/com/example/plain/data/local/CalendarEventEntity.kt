package com.example.plain.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Room에 저장되는 일정 레코드입니다. 도메인 모델과의 변환은 저장소에서 수행합니다. */
@Entity(
    tableName = "calendar_events",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("date"), Index("tagId")],
)
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val time: LocalTime,
    val locationName: String,
    val latitude: Double?,
    val longitude: Double?,
    val tagId: Long?,
    val createdAt: Instant,
    val modifiedAt: Instant,
)
