package com.example.plain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plain.ui.calendar.CalendarScreen
import com.example.plain.ui.calendar.CalendarViewModel
import com.example.plain.ui.theme.PlainTheme

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
                )
            }
        }
    }
}
