package com.example.plain.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.plain.domain.model.CalendarEvent
import com.example.plain.domain.model.ScheduleTag
import com.example.plain.domain.repository.CalendarRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** 달력 화면에 필요한 모든 불변 상태를 담습니다. */
data class CalendarUiState(
    val displayedMonth: YearMonth,
    val selectedDate: LocalDate,
    val events: List<CalendarEvent> = emptyList(),
    val tags: List<ScheduleTag> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * 월 이동, 날짜 선택, 선택 날짜의 일정 관찰을 담당합니다.
 *
 * 화면은 [uiState]만 관찰하므로 재구성이나 Activity 재생성 중에도 데이터 흐름이
 * 중복되지 않습니다. 날짜가 변경되면 이전 Room Flow 수집은 자동 취소됩니다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: CalendarRepository,
    initialDate: LocalDate = LocalDate.now(),
) : ViewModel() {
    private val displayedMonth = MutableStateFlow(YearMonth.from(initialDate))
    private val selectedDate = MutableStateFlow(initialDate)

    private val eventLoadState = selectedDate
        .flatMapLatest { date ->
            repository.observeEvents(date)
                .map<List<CalendarEvent>, EventLoadState> { EventLoadState.Loaded(it) }
                .onStart { emit(EventLoadState.Loading) }
                .catch { throwable ->
                    emit(EventLoadState.Failed(throwable.message ?: "일정을 불러오지 못했습니다."))
                }
        }

    private val tags = repository.observeTags().catch { emit(emptyList()) }

    /** 선택 날짜 또는 저장 데이터가 바뀔 때 갱신되는 화면 상태입니다. */
    val uiState = combine(displayedMonth, selectedDate, eventLoadState, tags) {
            month,
            date,
            eventState,
            currentTags,
        ->
        when (eventState) {
            EventLoadState.Loading -> CalendarUiState(
                displayedMonth = month,
                selectedDate = date,
                tags = currentTags,
            )

            is EventLoadState.Loaded -> CalendarUiState(
                displayedMonth = month,
                selectedDate = date,
                events = eventState.events,
                tags = currentTags,
                isLoading = false,
            )

            is EventLoadState.Failed -> CalendarUiState(
                displayedMonth = month,
                selectedDate = date,
                tags = currentTags,
                isLoading = false,
                errorMessage = eventState.message,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(
            displayedMonth = YearMonth.from(initialDate),
            selectedDate = initialDate,
        ),
    )

    /** 이전 달로 이동하고 기존 일자와 가장 가까운 유효 날짜를 선택합니다. */
    fun showPreviousMonth() = moveToMonth(displayedMonth.value.minusMonths(1))

    /** 다음 달로 이동하고 기존 일자와 가장 가까운 유효 날짜를 선택합니다. */
    fun showNextMonth() = moveToMonth(displayedMonth.value.plusMonths(1))

    /** 사용자가 누른 [date]를 선택하며, 앞뒤 달 날짜라면 표시 월도 함께 변경합니다. */
    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        displayedMonth.value = YearMonth.from(date)
    }

    private fun moveToMonth(month: YearMonth) {
        val day = selectedDate.value.dayOfMonth.coerceAtMost(month.lengthOfMonth())
        displayedMonth.value = month
        selectedDate.value = month.atDay(day)
    }

    private sealed interface EventLoadState {
        data object Loading : EventLoadState
        data class Loaded(val events: List<CalendarEvent>) : EventLoadState
        data class Failed(val message: String) : EventLoadState
    }

    companion object {
        /** 저장소 의존성을 주입하는 간단한 ViewModel 팩토리를 만듭니다. */
        fun factory(repository: CalendarRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CalendarViewModel(repository) }
        }
    }
}
