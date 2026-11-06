package com.rhinos.plain.data.repository

import com.rhinos.plain.data.local.ScheduleTagDao
import com.rhinos.plain.data.local.ScheduleTagEntity
import com.rhinos.plain.data.remote.FirebaseCalendarRemoteDataSource
import com.rhinos.plain.domain.model.CalendarEvent
import com.rhinos.plain.domain.model.ScheduleTag
import com.rhinos.plain.domain.model.localDate
import com.rhinos.plain.domain.repository.CalendarRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Cloud Functions를 일정의 원본으로 사용하고 화면 관찰용 메모리 캐시를 관리합니다.
 *
 * 태그 정의는 네트워크와 무관하게 표시할 수 있도록 기존 Room에 유지합니다. 생성·수정·삭제는
 * 서버 확인 후에만 캐시를 변경하므로 UI가 실패한 저장을 성공으로 표시하지 않습니다.
 */
class FirebaseCalendarRepository(
    private val remoteDataSource: FirebaseCalendarRemoteDataSource,
    private val tagDao: ScheduleTagDao,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : CalendarRepository {
    private val eventsByDate = MutableStateFlow<Map<LocalDate, List<CalendarEvent>>>(emptyMap())

    override fun observeEvents(date: LocalDate): Flow<List<CalendarEvent>> =
        eventsByDate.map { it[date].orEmpty() }.distinctUntilChanged()

    override fun observeEventDates(month: YearMonth): Flow<Set<LocalDate>> =
        eventsByDate.map { events ->
            events.filter { (date, values) -> YearMonth.from(date) == month && values.isNotEmpty() }.keys
        }.distinctUntilChanged()

    override fun observeTags(): Flow<List<ScheduleTag>> =
        tagDao.observeAll().map { entities -> entities.map(ScheduleTagEntity::toDomain) }

    override suspend fun refreshEvents(date: LocalDate) {
        val startAt = date.atStartOfDay(zoneId).toInstant()
        val endAt = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val events = remoteDataSource.getSchedules(startAt, endAt)
        eventsByDate.update { current -> current + (date to events) }
    }

    override suspend fun refreshMonth(month: YearMonth) {
        val startDate = month.atDay(1)
        val endDate = month.plusMonths(1).atDay(1)
        val events = remoteDataSource.getSchedules(
            startDate.atStartOfDay(zoneId).toInstant(),
            endDate.atStartOfDay(zoneId).toInstant(),
        )
        val grouped = events.groupBy(CalendarEvent::localDate)
        eventsByDate.update { current ->
            current.filterKeys { YearMonth.from(it) != month } + grouped
        }
    }

    override suspend fun getEvent(eventId: String): CalendarEvent =
        remoteDataSource.getSchedule(eventId).also(::cacheEvent)

    override suspend fun createEvent(event: CalendarEvent): CalendarEvent =
        remoteDataSource.createSchedule(event).also(::cacheEvent)

    override suspend fun updateEvent(event: CalendarEvent): CalendarEvent =
        remoteDataSource.updateSchedule(event).also(::cacheEvent)

    override suspend fun deleteEvent(event: CalendarEvent) {
        remoteDataSource.deleteSchedule(event)
        eventsByDate.update { current ->
            current.mapValues { (_, events) -> events.filterNot { it.id == event.id } }
        }
    }

    override suspend fun createTag(tag: ScheduleTag): String =
        tagDao.insert(tag.toEntity()).toString()

    override suspend fun updateTag(tag: ScheduleTag) {
        tagDao.update(tag.toEntity())
    }

    override suspend fun deleteTag(tag: ScheduleTag) {
        tagDao.delete(tag.toEntity())
    }

    private fun cacheEvent(event: CalendarEvent) {
        eventsByDate.update { current ->
            val withoutEvent = current.mapValues { (_, events) -> events.filterNot { it.id == event.id } }
            val date = event.localDate()
            withoutEvent + (date to (withoutEvent[date].orEmpty() + event).sortedBy(CalendarEvent::startAt))
        }
    }
}

private fun ScheduleTagEntity.toDomain() = ScheduleTag(id = id.toString(), name = name, colorArgb = colorArgb)

private fun ScheduleTag.toEntity() = ScheduleTagEntity(
    id = id.toLongOrNull() ?: 0,
    name = name,
    colorArgb = colorArgb,
)
