package com.example.plain.domain.model

/**
 * 일정의 분류와 표시 색상을 정의하는 도메인 모델입니다.
 *
 * [colorArgb]는 플랫폼 UI 타입에 의존하지 않도록 ARGB 정수로 보관합니다.
 */
data class ScheduleTag(
    val id: Long = 0,
    val name: String,
    val colorArgb: Long,
)
