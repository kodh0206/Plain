package com.example.plain.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 월간 달력 화면에서 보여 줄 6주(42일) 범위를 계산합니다. */
object CalendarMonth {
    /**
     * [month]를 포함하는 일요일 시작 6주 날짜 목록을 반환합니다.
     * 앞뒤 달의 날짜도 포함하여 화면 높이가 월마다 바뀌지 않게 합니다.
     */
    fun dates(month: YearMonth): List<LocalDate> {
        val firstDay = month.atDay(1)
        val gridStart = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        return List(42) { offset -> gridStart.plusDays(offset.toLong()) }
    }
}
