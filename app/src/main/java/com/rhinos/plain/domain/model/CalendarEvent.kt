package com.rhinos.plain.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 내비게이션에 사용할 수 있는 일정 장소입니다. 좌표는 반드시 한 쌍으로 존재해야 합니다. */
data class ScheduleLocation(
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * 사용자가 등록한 하나의 일정을 표현하는 도메인 모델입니다.
 *
 * Room 저장 형식과 분리되어 있으므로 화면과 비즈니스 로직은 데이터베이스 구현에
 * 의존하지 않습니다. 위도와 경도는 둘 다 있을 때만 유효한 목적지로 취급해야 합니다.
 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String = "",
    val startAt: Instant,
    val endAt: Instant,
    val timeZoneId: String,
    val isAllDay: Boolean = false,
    val location: ScheduleLocation? = null,
    val tagIds: List<String> = emptyList(),
    val createdAt: Instant,
    val modifiedAt: Instant,
    val revision: Long,
)

/** 서버 시간대를 적용한 일정 날짜를 반환합니다. */
fun CalendarEvent.localDate(): LocalDate = startAt.atZone(zoneId()).toLocalDate()

/** 서버 시간대를 적용한 일정 시작 시각을 반환합니다. */
fun CalendarEvent.localStartTime(): LocalTime = startAt.atZone(zoneId()).toLocalTime()

/** 잘못된 외부 시간대 값이 화면을 중단시키지 않도록 시스템 시간대로 대체합니다. */
private fun CalendarEvent.zoneId(): ZoneId =
    runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())
