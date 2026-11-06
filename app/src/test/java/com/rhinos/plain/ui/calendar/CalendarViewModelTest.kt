package com.rhinos.plain.ui.calendar

import com.rhinos.plain.domain.model.CalendarEvent
import com.rhinos.plain.domain.model.ScheduleTag
import com.rhinos.plain.domain.repository.CalendarRepository
import com.rhinos.plain.domain.repository.CalendarRepositoryException
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** 일정 입력 검증, 서버 확인 반영과 중복 저장 방지를 검증합니다. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveEditor_createsOnlyOnceWhenButtonIsTappedRepeatedly() = runTest(dispatcher) {
        val repository = FakeCalendarRepository()
        val viewModel = CalendarViewModel(
            repository = repository,
            initialDate = LocalDate.of(2026, 11, 7),
            zoneId = ZoneId.of("Asia/Seoul"),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.openCreateEditor()
        advanceUntilIdle()
        val editor = requireNotNull(viewModel.uiState.value.editor)
        viewModel.updateEditor(editor.copy(title = "대전 출장"))
        advanceUntilIdle()
        viewModel.saveEditor()
        viewModel.saveEditor()
        advanceUntilIdle()

        assertEquals(1, repository.createCount)
        assertNull(viewModel.uiState.value.editor)
        assertEquals("일정을 저장했습니다.", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun saveEditor_keepsEditorOpenWhenServerReportsConflict() = runTest(dispatcher) {
        val repository = FakeCalendarRepository(conflictOnUpdate = true)
        val date = LocalDate.of(2026, 11, 7)
        val original = repository.sampleEvent(date)
        val viewModel = CalendarViewModel(repository, date, ZoneId.of("Asia/Seoul"))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.openEditEditor(original)
        advanceUntilIdle()
        val editor = requireNotNull(viewModel.uiState.value.editor)
        viewModel.updateEditor(editor.copy(title = "변경된 제목"))
        advanceUntilIdle()
        viewModel.saveEditor()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.editor)
        assertEquals(
            "다른 곳에서 일정이 변경되었습니다. 새로고침 후 다시 시도해 주세요.",
            viewModel.uiState.value.editor?.validationError,
        )
    }
}

private class FakeCalendarRepository(
    private val conflictOnUpdate: Boolean = false,
) : CalendarRepository {
    private val events = MutableStateFlow<Map<LocalDate, List<CalendarEvent>>>(emptyMap())
    private val tags = MutableStateFlow(emptyList<ScheduleTag>())
    var createCount: Int = 0
        private set

    override fun observeEvents(date: LocalDate): Flow<List<CalendarEvent>> =
        events.map { it[date].orEmpty() }

    override fun observeEventDates(month: YearMonth): Flow<Set<LocalDate>> =
        events.map { map -> map.filterKeys { YearMonth.from(it) == month }.keys }

    override fun observeTags(): Flow<List<ScheduleTag>> = tags

    override suspend fun refreshEvents(date: LocalDate) = Unit

    override suspend fun refreshMonth(month: YearMonth) = Unit

    override suspend fun getEvent(eventId: String): CalendarEvent =
        events.value.values.flatten().first { it.id == eventId }

    override suspend fun createEvent(event: CalendarEvent): CalendarEvent {
        createCount += 1
        return event.copy(revision = 1).also(::cache)
    }

    override suspend fun updateEvent(event: CalendarEvent): CalendarEvent {
        if (conflictOnUpdate) throw CalendarRepositoryException.Conflict()
        return event.copy(revision = event.revision + 1).also(::cache)
    }

    override suspend fun deleteEvent(event: CalendarEvent) {
        events.value = events.value.mapValues { (_, values) -> values.filterNot { it.id == event.id } }
    }

    override suspend fun createTag(tag: ScheduleTag): String = "1"

    override suspend fun updateTag(tag: ScheduleTag) = Unit

    override suspend fun deleteTag(tag: ScheduleTag) = Unit

    fun sampleEvent(date: LocalDate): CalendarEvent {
        val zone = ZoneId.of("Asia/Seoul")
        val start = date.atTime(9, 0).atZone(zone).toInstant()
        return CalendarEvent(
            id = "schedule_12345678",
            title = "대전 출장",
            startAt = start,
            endAt = start.plusSeconds(3_600),
            timeZoneId = zone.id,
            createdAt = start,
            modifiedAt = start,
            revision = 1,
        )
    }

    private fun cache(event: CalendarEvent) {
        val date = event.startAt.atZone(ZoneId.of(event.timeZoneId)).toLocalDate()
        events.value = events.value + (date to listOf(event))
    }
}
