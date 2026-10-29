package com.example.plain.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** java.time 값을 정렬과 검증이 쉬운 ISO-8601 문자열로 변환합니다. */
class DateTimeConverters {
    @TypeConverter
    fun localDateToString(value: LocalDate): String = value.toString()

    @TypeConverter
    fun stringToLocalDate(value: String): LocalDate = LocalDate.parse(value)

    @TypeConverter
    fun localTimeToString(value: LocalTime): String = value.toString()

    @TypeConverter
    fun stringToLocalTime(value: String): LocalTime = LocalTime.parse(value)

    @TypeConverter
    fun instantToString(value: Instant): String = value.toString()

    @TypeConverter
    fun stringToInstant(value: String): Instant = Instant.parse(value)
}
