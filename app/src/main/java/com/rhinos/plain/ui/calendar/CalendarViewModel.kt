package com.rhinos.plain.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rhinos.plain.domain.model.CalendarEvent
import com.rhinos.plain.domain.model.ScheduleLocation
import com.rhinos.plain.domain.model.ScheduleTag
import com.rhinos.plain.domain.model.localDate
import com.rhinos.plain.domain.model.localStartTime
import com.rhinos.plain.domain.repository.CalendarRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 일정 입력 패널의 생성·수정 모드를 구분합니다. */
enum class ScheduleEditorMode { Create, Edit }

/**
 * 일정 입력 필드와 저장 상태입니다.
 *
 * 문자열 상태를 그대로 보존해 사용자가 입력 중인 불완전한 날짜·시간·좌표도 Compose가
 * 안전하게 표시하도록 하고, 저장 직전에만 ViewModel에서 도메인 값으로 변환합니다.
 */
data class ScheduleEditorState(
    val mode: ScheduleEditorMode,
    val eventId: String,
    val title: String = "",
    val description: String = "",
    val dateText: String,
    val startTimeText: String = "09:00",
    val endDateText: String,
    val endTimeText: String = "10:00",
    val timeZoneId: String,
    val isAllDay: Boolean = false,
    val address: String = "",
    val latitudeText: String = "",
    val longitudeText: String = "",
    val selectedTagIds: Set<String> = emptySet(),
    val revision: Long = 0,
    val validationError: String? = null,
)

/** 달력 화면에 필요한 모든 불변 상태를 담습니다. */
data class CalendarUiState(
    val displayedMonth: YearMonth,
    val selectedDate: LocalDate,
    val events: List<CalendarEvent> = emptyList(),
    val eventDates: Set<LocalDate> = emptySet(),
    val tags: List<ScheduleTag> = emptyList(),
    val editor: ScheduleEditorState? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
)

private data class CalendarDataState(
    val month: YearMonth,
    val date: LocalDate,
    val events: List<CalendarEvent>,
    val tags: List<ScheduleTag>,
    val eventDates: Set<LocalDate>,
)

/**
 * 월 이동, 서버 일정 조회와 입력 패널의 CRUD 상태를 관리합니다.
 *
 * 저장 요청은 [isSaving]으로 중복 실행을 막고 서버가 반환한 일정만 Repository 캐시에
 * 반영합니다. 날짜 변경 시 이전 새로고침 작업을 취소해 늦은 실패가 새 화면을 덮지 않습니다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: CalendarRepository,
    initialDate: LocalDate = LocalDate.now(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val displayedMonth = MutableStateFlow(YearMonth.from(initialDate))
    private val selectedDate = MutableStateFlow(initialDate)
    private val editor = MutableStateFlow<ScheduleEditorState?>(null)
    private val isLoading = MutableStateFlow(true)
    private val isSaving = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val statusMessage = MutableStateFlow<String?>(null)
    private var editingEvent: CalendarEvent? = null
    private var refreshJob: Job? = null

    private val events = selectedDate
        .flatMapLatest(repository::observeEvents)
        .distinctUntilChanged()
    private val tags = repository.observeTags()
    private val eventDates = displayedMonth.flatMapLatest(repository::observeEventDates)

    private val calendarData = combine(displayedMonth, selectedDate, events, tags, eventDates) {
            month,
            date,
            currentEvents,
            currentTags,
            currentEventDates,
        ->
        CalendarDataState(month, date, currentEvents, currentTags, currentEventDates)
    }

    /** 선택 날짜, 서버 상태와 편집 상태를 하나의 화면 상태로 제공합니다. */
    val uiState = combine(
        calendarData,
        editor,
        isLoading,
        isSaving,
        combine(errorMessage, statusMessage) { error, status -> error to status },
    ) { data, currentEditor, loading, saving, messages ->
        CalendarUiState(
            displayedMonth = data.month,
            selectedDate = data.date,
            events = data.events,
            eventDates = data.eventDates,
            tags = data.tags,
            editor = currentEditor,
            isLoading = loading,
            isSaving = saving,
            errorMessage = messages.first,
            statusMessage = messages.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(YearMonth.from(initialDate), initialDate),
    )

    init {
        refreshDisplayedMonth()
    }

    /** 이전 달로 이동하고 기존 일자와 가장 가까운 유효 날짜를 선택합니다. */
    fun showPreviousMonth() = moveToMonth(displayedMonth.value.minusMonths(1))

    /** 다음 달로 이동하고 기존 일자와 가장 가까운 유효 날짜를 선택합니다. */
    fun showNextMonth() = moveToMonth(displayedMonth.value.plusMonths(1))

    /** 사용자가 누른 날짜를 선택하고 서버 일정을 새로고침합니다. */
    fun selectDate(date: LocalDate) {
        val monthChanged = YearMonth.from(date) != displayedMonth.value
        selectedDate.value = date
        displayedMonth.value = YearMonth.from(date)
        closeEditor()
        if (monthChanged) refreshDisplayedMonth() else refreshSelectedDate()
    }

    /** 현재 선택 날짜의 서버 일정을 다시 요청합니다. */
    fun retryLoad() = refreshSelectedDate()

    /** 선택 날짜를 기준으로 중복 방지 ID를 가진 새 일정 편집기를 엽니다. */
    fun openCreateEditor() {
        editingEvent = null
        val date = selectedDate.value
        editor.value = ScheduleEditorState(
            mode = ScheduleEditorMode.Create,
            eventId = UUID.randomUUID().toString(),
            dateText = date.toString(),
            endDateText = date.toString(),
            timeZoneId = zoneId.id,
        )
        statusMessage.value = null
    }

    /** 기존 서버 일정의 현재 리비전과 값을 편집기에 채웁니다. */
    fun openEditEditor(event: CalendarEvent) {
        editingEvent = event
        val start = event.startAt.atZone(ZoneId.of(event.timeZoneId))
        val end = event.endAt.atZone(ZoneId.of(event.timeZoneId))
        editor.value = ScheduleEditorState(
            mode = ScheduleEditorMode.Edit,
            eventId = event.id,
            title = event.title,
            description = event.description,
            dateText = start.toLocalDate().toString(),
            startTimeText = start.toLocalTime().withSecond(0).withNano(0).toString(),
            endDateText = end.toLocalDate().toString(),
            endTimeText = end.toLocalTime().withSecond(0).withNano(0).toString(),
            timeZoneId = event.timeZoneId,
            isAllDay = event.isAllDay,
            address = event.location?.address.orEmpty(),
            latitudeText = event.location?.latitude?.toString().orEmpty(),
            longitudeText = event.location?.longitude?.toString().orEmpty(),
            selectedTagIds = event.tagIds.toSet(),
            revision = event.revision,
        )
        statusMessage.value = null
    }

    /** Compose 입력 변경을 저장하되 이전 검증 오류는 제거합니다. */
    fun updateEditor(updated: ScheduleEditorState) {
        editor.value = updated.copy(validationError = null)
    }

    /** 편집 내용을 검증하고 서버 생성 또는 수정 결과가 확정된 뒤 패널을 닫습니다. */
    fun saveEditor() {
        val current = editor.value ?: return
        if (isSaving.value) return
        val event = try {
            current.toCalendarEvent(editingEvent, zoneId)
        } catch (error: IllegalArgumentException) {
            editor.value = current.copy(validationError = error.message)
            return
        }

        isSaving.value = true
        viewModelScope.launch {
            statusMessage.value = null
            try {
                val saved = when (current.mode) {
                    ScheduleEditorMode.Create -> repository.createEvent(event)
                    ScheduleEditorMode.Edit -> repository.updateEvent(event)
                }
                selectedDate.value = saved.localDate()
                displayedMonth.value = YearMonth.from(saved.localDate())
                editor.value = null
                editingEvent = null
                statusMessage.value = if (current.mode == ScheduleEditorMode.Create) {
                    "일정을 저장했습니다."
                } else {
                    "일정을 수정했습니다."
                }
                refreshSelectedDate()
            } catch (error: Exception) {
                editor.value = current.copy(validationError = error.userMessage())
            } finally {
                isSaving.value = false
            }
        }
    }

    /** 편집 중인 서버 일정을 현재 리비전으로 삭제합니다. */
    fun deleteEditingEvent() {
        val event = editingEvent ?: return
        if (isSaving.value) return
        isSaving.value = true
        viewModelScope.launch {
            try {
                repository.deleteEvent(event)
                closeEditor()
                statusMessage.value = "일정을 삭제했습니다."
                refreshSelectedDate()
            } catch (error: Exception) {
                editor.value = editor.value?.copy(validationError = error.userMessage())
            } finally {
                isSaving.value = false
            }
        }
    }

    /** 입력 내용을 저장하지 않고 편집 패널을 닫습니다. */
    fun closeEditor() {
        editor.value = null
        editingEvent = null
    }

    private fun moveToMonth(month: YearMonth) {
        val day = selectedDate.value.dayOfMonth.coerceAtMost(month.lengthOfMonth())
        displayedMonth.value = month
        selectedDate.value = month.atDay(day)
        closeEditor()
        refreshDisplayedMonth()
    }

    private fun refreshSelectedDate() {
        val date = selectedDate.value
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                repository.refreshEvents(date)
            } catch (error: Exception) {
                errorMessage.value = error.userMessage()
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun refreshDisplayedMonth() {
        val month = displayedMonth.value
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                repository.refreshMonth(month)
            } catch (error: Exception) {
                errorMessage.value = error.userMessage()
            } finally {
                isLoading.value = false
            }
        }
    }

    companion object {
        /** 저장소 의존성을 주입하는 간단한 ViewModel 팩토리를 만듭니다. */
        fun factory(repository: CalendarRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CalendarViewModel(repository) }
        }
    }
}

private fun ScheduleEditorState.toCalendarEvent(
    original: CalendarEvent?,
    fallbackZoneId: ZoneId,
): CalendarEvent {
    val normalizedTitle = title.trim()
    require(normalizedTitle.isNotEmpty()) { "일정 제목을 입력해 주세요." }
    val date = parseDate(dateText, "시작 날짜")
    val endDate = parseDate(endDateText, "종료 날짜")
    val scheduleZone = runCatching { ZoneId.of(timeZoneId) }.getOrElse {
        throw IllegalArgumentException("시간대 형식이 올바르지 않습니다.")
    }
    val startAt = if (isAllDay) {
        date.atStartOfDay(scheduleZone).toInstant()
    } else {
        date.atTime(parseTime(startTimeText, "시작 시간")).atZone(scheduleZone).toInstant()
    }
    val endAt = if (isAllDay) {
        endDate.plusDays(1).atStartOfDay(scheduleZone).toInstant()
    } else {
        endDate.atTime(parseTime(endTimeText, "종료 시간")).atZone(scheduleZone).toInstant()
    }
    require(endAt > startAt) { "종료 시각은 시작 시각보다 늦어야 합니다." }

    val latitude = latitudeText.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()
    val longitude = longitudeText.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()
    require(latitudeText.isBlank() || latitude != null) { "위도는 숫자로 입력해 주세요." }
    require(longitudeText.isBlank() || longitude != null) { "경도는 숫자로 입력해 주세요." }
    require((latitude == null) == (longitude == null)) { "위도와 경도는 함께 입력해 주세요." }
    require(latitude == null || latitude in -90.0..90.0) { "위도 범위가 올바르지 않습니다." }
    require(longitude == null || longitude in -180.0..180.0) { "경도 범위가 올바르지 않습니다." }

    val now = Instant.now()
    return CalendarEvent(
        id = eventId,
        title = normalizedTitle,
        description = description.trim(),
        startAt = startAt,
        endAt = endAt,
        timeZoneId = scheduleZone.id.ifBlank { fallbackZoneId.id },
        isAllDay = isAllDay,
        location = if (address.isBlank() && latitude == null) null else {
            ScheduleLocation(address.trim(), latitude, longitude)
        },
        tagIds = selectedTagIds.sorted(),
        createdAt = original?.createdAt ?: now,
        modifiedAt = original?.modifiedAt ?: now,
        revision = original?.revision ?: 0,
    )
}

private fun parseDate(value: String, label: String): LocalDate = try {
    LocalDate.parse(value.trim())
} catch (_: DateTimeParseException) {
    throw IllegalArgumentException("$label 형식은 yyyy-MM-dd입니다.")
}

private fun parseTime(value: String, label: String): LocalTime = try {
    LocalTime.parse(value.trim())
} catch (_: DateTimeParseException) {
    throw IllegalArgumentException("$label 형식은 HH:mm입니다.")
}

private fun Throwable.userMessage(): String =
    message?.takeIf(String::isNotBlank) ?: "요청을 처리하지 못했습니다. 다시 시도해 주세요."
