package com.example.plain.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [CalendarMonth]의 월 경계 계산을 검증합니다. */
class CalendarMonthTest {
    @Test
    fun dates_returnsSixCompleteWeeksStartingOnSunday() {
        val dates = CalendarMonth.dates(YearMonth.of(2026, 10))

        assertEquals(42, dates.size)
        assertEquals(DayOfWeek.SUNDAY, dates.first().dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, dates.last().dayOfWeek)
        assertTrue(dates.contains(LocalDate.of(2026, 10, 1)))
        assertTrue(dates.contains(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun dates_handlesYearBoundary() {
        val dates = CalendarMonth.dates(YearMonth.of(2027, 1))

        assertEquals(LocalDate.of(2026, 12, 27), dates.first())
        assertEquals(LocalDate.of(2027, 2, 6), dates.last())
    }
}
