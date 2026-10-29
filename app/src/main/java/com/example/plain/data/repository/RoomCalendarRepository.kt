package com.example.plain.data.repository

import com.example.plain.data.local.CalendarEventDao
import com.example.plain.data.local.CalendarEventEntity
import com.example.plain.data.local.ScheduleTagDao
import com.example.plain.data.local.ScheduleTagEntity
import com.example.plain.domain.model.CalendarEvent
import com.example.plain.domain.model.ScheduleTag
import com.example.plain.domain.repository.CalendarRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room DAO를 애플리케이션의 [CalendarRepository] 계약에 연결합니다. */
class RoomCalendarRepository(
    private val eventDao: CalendarEventDao,
    private val tagDao: ScheduleTagDao,
) : CalendarRepository {
    override fun observeEvents(date: LocalDate): Flow<List<CalendarEvent>> =
        eventDao.observeByDate(date).map { entities -> entities.map(CalendarEventEntity::toDomain) }

    override fun observeTags(): Flow<List<ScheduleTag>> =
        tagDao.observeAll().map { entities -> entities.map(ScheduleTagEntity::toDomain) }

    override suspend fun createEvent(event: CalendarEvent): Long = eventDao.insert(event.toEntity())

    override suspend fun updateEvent(event: CalendarEvent) = eventDao.update(event.toEntity())

    override suspend fun deleteEvent(event: CalendarEvent) = eventDao.delete(event.toEntity())

    override suspend fun createTag(tag: ScheduleTag): Long = tagDao.insert(tag.toEntity())

    override suspend fun updateTag(tag: ScheduleTag) = tagDao.update(tag.toEntity())

    override suspend fun deleteTag(tag: ScheduleTag) = tagDao.delete(tag.toEntity())
}

private fun CalendarEventEntity.toDomain() = CalendarEvent(
    id = id,
    title = title,
    date = date,
    time = time,
    locationName = locationName,
    latitude = latitude,
    longitude = longitude,
    tagId = tagId,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)

private fun CalendarEvent.toEntity() = CalendarEventEntity(
    id = id,
    title = title,
    date = date,
    time = time,
    locationName = locationName,
    latitude = latitude,
    longitude = longitude,
    tagId = tagId,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)

private fun ScheduleTagEntity.toDomain() = ScheduleTag(id = id, name = name, colorArgb = colorArgb)

private fun ScheduleTag.toEntity() = ScheduleTagEntity(id = id, name = name, colorArgb = colorArgb)
