package com.example.plain.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room에 저장되는 일정 태그 레코드입니다. */
@Entity(
    tableName = "schedule_tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class ScheduleTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Long,
)
