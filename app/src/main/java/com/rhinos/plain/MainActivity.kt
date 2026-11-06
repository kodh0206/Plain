package com.rhinos.plain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rhinos.plain.ui.calendar.CalendarScreen
import com.rhinos.plain.ui.calendar.CalendarViewModel
import com.rhinos.plain.ui.theme.PlainTheme

/** Plain의 단일 Compose 화면을 호스팅하는 진입 Activity입니다. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as PlainApplication).calendarRepository

        setContent {
            PlainTheme {
                val calendarViewModel: CalendarViewModel = viewModel(
                    factory = CalendarViewModel.factory(repository),
                )
                val uiState by calendarViewModel.uiState.collectAsStateWithLifecycle()
                CalendarScreen(
                    uiState = uiState,
                    onPreviousMonth = calendarViewModel::showPreviousMonth,
                    onNextMonth = calendarViewModel::showNextMonth,
                    onDateSelected = calendarViewModel::selectDate,
                    onRetry = calendarViewModel::retryLoad,
                    onAddEvent = calendarViewModel::openCreateEditor,
                    onEventSelected = calendarViewModel::openEditEditor,
                    onEditorChanged = calendarViewModel::updateEditor,
                    onSaveEvent = calendarViewModel::saveEditor,
                    onDeleteEvent = calendarViewModel::deleteEditingEvent,
                    onCloseEditor = calendarViewModel::closeEditor,
                )
            }
        }
    }
}
