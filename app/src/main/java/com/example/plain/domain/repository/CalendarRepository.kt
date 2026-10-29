package com.example.plain.domain.repository

import com.example.plain.domain.model.CalendarEvent
import com.example.plain.domain.model.ScheduleTag
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * 일정과 태그 데이터에 접근하는 애플리케이션 경계입니다.
 *
 * ViewModel은 이 인터페이스에만 의존하며 실제 저장 방식은 데이터 계층에서 결정합니다.
 */
interface CalendarRepository {
    /** 지정한 날짜의 일정을 시간순으로 지속 관찰합니다. */
    fun observeEvents(date: LocalDate): Flow<List<CalendarEvent>>

    /** 모든 태그를 이름순으로 지속 관찰합니다. */
    fun observeTags(): Flow<List<ScheduleTag>>

    /** 일정을 추가하고 생성된 식별자를 반환합니다. */
    suspend fun createEvent(event: CalendarEvent): Long

    /** 동일한 식별자의 일정을 갱신합니다. */
    suspend fun updateEvent(event: CalendarEvent)

    /** 일정을 삭제합니다. 존재하지 않는 식별자는 무시됩니다. */
    suspend fun deleteEvent(event: CalendarEvent)

    /** 태그를 추가하고 생성된 식별자를 반환합니다. */
    suspend fun createTag(tag: ScheduleTag): Long

    /** 동일한 식별자의 태그를 갱신합니다. */
    suspend fun updateTag(tag: ScheduleTag)

    /** 사용되지 않는 태그를 삭제합니다. */
    suspend fun deleteTag(tag: ScheduleTag)
}
