package com.example.plain.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * 사용자가 등록한 하나의 일정을 표현하는 도메인 모델입니다.
 *
 * Room 저장 형식과 분리되어 있으므로 화면과 비즈니스 로직은 데이터베이스 구현에
 * 의존하지 않습니다. 위도와 경도는 둘 다 있을 때만 유효한 목적지로 취급해야 합니다.
 */
data class CalendarEvent(
    val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val time: LocalTime,
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val tagId: Long? = null,
    val createdAt: Instant,
    val modifiedAt: Instant,
)
