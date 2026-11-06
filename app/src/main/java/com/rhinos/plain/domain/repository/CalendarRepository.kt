package com.rhinos.plain.domain.repository

import com.rhinos.plain.domain.model.CalendarEvent
import com.rhinos.plain.domain.model.ScheduleTag
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/**
 * 일정과 태그 데이터에 접근하는 애플리케이션 경계입니다.
 *
 * ViewModel은 이 인터페이스에만 의존하며 실제 저장 방식은 데이터 계층에서 결정합니다.
 */
interface CalendarRepository {
    /** 지정한 날짜의 일정을 시간순으로 지속 관찰합니다. */
    fun observeEvents(date: LocalDate): Flow<List<CalendarEvent>>

    /** 표시 월에서 일정이 한 개 이상 존재하는 날짜를 지속 관찰합니다. */
    fun observeEventDates(month: YearMonth): Flow<Set<LocalDate>>

    /** 모든 태그를 이름순으로 지속 관찰합니다. */
    fun observeTags(): Flow<List<ScheduleTag>>

    /** 서버에서 지정 날짜의 최신 일정을 읽어 관찰 상태를 갱신합니다. */
    suspend fun refreshEvents(date: LocalDate)

    /** 표시 월 전체를 서버에서 읽어 날짜별 캐시와 일정 표시 점을 갱신합니다. */
    suspend fun refreshMonth(month: YearMonth)

    /** 서버에서 단일 일정을 조회합니다. */
    suspend fun getEvent(eventId: String): CalendarEvent

    /** 일정을 추가하고 생성된 식별자를 반환합니다. */
    suspend fun createEvent(event: CalendarEvent): CalendarEvent

    /** 동일한 식별자의 일정을 갱신합니다. */
    suspend fun updateEvent(event: CalendarEvent): CalendarEvent

    /** 일정을 삭제합니다. 존재하지 않는 식별자는 무시됩니다. */
    suspend fun deleteEvent(event: CalendarEvent)

    /** 태그를 추가하고 생성된 식별자를 반환합니다. */
    suspend fun createTag(tag: ScheduleTag): String

    /** 동일한 식별자의 태그를 갱신합니다. */
    suspend fun updateTag(tag: ScheduleTag)

    /** 사용되지 않는 태그를 삭제합니다. */
    suspend fun deleteTag(tag: ScheduleTag)
}
