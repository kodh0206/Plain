package com.rhinos.plain.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rhinos.plain.domain.calendar.CalendarMonth
import com.rhinos.plain.domain.model.CalendarEvent
import com.rhinos.plain.domain.model.ScheduleTag
import com.rhinos.plain.domain.model.localStartTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
private val weekdayFormatter = DateTimeFormatter.ofPattern("E", Locale.KOREAN)
private val compactDateFormatter = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
private val weekLabels = listOf("일", "월", "화", "수", "목", "금", "토")

/** 화면설계의 월간 달력, 인라인 일정 편집기와 선택 날짜 일정 목록을 표시합니다. */
@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onAddEvent: () -> Unit,
    onEventSelected: (CalendarEvent) -> Unit,
    onEditorChanged: (ScheduleEditorState) -> Unit,
    onSaveEvent: () -> Unit,
    onDeleteEvent: () -> Unit,
    onCloseEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
        ) {
            if (maxWidth >= 840.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    CalendarPanel(
                        uiState = uiState,
                        onPreviousMonth = onPreviousMonth,
                        onNextMonth = onNextMonth,
                        onDateSelected = onDateSelected,
                        onEditorChanged = onEditorChanged,
                        onSaveEvent = onSaveEvent,
                        onDeleteEvent = onDeleteEvent,
                        onCloseEditor = onCloseEditor,
                        modifier = Modifier.weight(1.08f).fillMaxHeight(),
                    )
                    SchedulePanel(
                        uiState = uiState,
                        onRetry = onRetry,
                        onAddEvent = onAddEvent,
                        onEventSelected = onEventSelected,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CalendarPanel(
                        uiState = uiState,
                        onPreviousMonth = onPreviousMonth,
                        onNextMonth = onNextMonth,
                        onDateSelected = onDateSelected,
                        onEditorChanged = onEditorChanged,
                        onSaveEvent = onSaveEvent,
                        onDeleteEvent = onDeleteEvent,
                        onCloseEditor = onCloseEditor,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SchedulePanel(
                        uiState = uiState,
                        onRetry = onRetry,
                        onAddEvent = onAddEvent,
                        onEventSelected = onEventSelected,
                        modifier = Modifier.fillMaxWidth().height(560.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarPanel(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onEditorChanged: (ScheduleEditorState) -> Unit,
    onSaveEvent: () -> Unit,
    onDeleteEvent: () -> Unit,
    onCloseEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.padding(horizontal = 28.dp, vertical = 22.dp).verticalScroll(rememberScrollState()),
        ) {
            MonthHeader(uiState, onPreviousMonth, onNextMonth)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                weekLabels.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = when (index) {
                            0 -> MaterialTheme.colorScheme.error
                            6 -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            CalendarMonth.dates(uiState.displayedMonth).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        DateCell(
                            date = date,
                            isCurrentMonth = date.monthValue == uiState.displayedMonth.monthValue,
                            isSelected = date == uiState.selectedDate,
                            hasEvents = date in uiState.eventDates,
                            onClick = { onDateSelected(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            uiState.editor?.let { editor ->
                Spacer(Modifier.height(18.dp))
                ScheduleEditor(
                    editor = editor,
                    tags = uiState.tags,
                    isSaving = uiState.isSaving,
                    onChanged = onEditorChanged,
                    onSave = onSaveEvent,
                    onDelete = onDeleteEvent,
                    onClose = onCloseEditor,
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPreviousMonth) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
        Text(
            text = "${uiState.displayedMonth.year}년 ${uiState.displayedMonth.monthValue}월",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onNextMonth) { Text("›", style = MaterialTheme.typography.headlineMedium) }
        Spacer(Modifier.weight(1f))
        Text(
            text = "${uiState.selectedDate.dayOfMonth}일 선택",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DateCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.height(50.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(width = 58.dp, height = 42.dp).clickable(onClick = onClick),
            shape = RoundedCornerShape(4.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    text = date.dayOfMonth.toString(),
                    modifier = if (isCurrentMonth || isSelected) Modifier else Modifier.alpha(0.28f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
                if (hasEvents) {
                    Box(
                        Modifier.size(5.dp).background(
                            if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                            RoundedCornerShape(100.dp),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SchedulePanel(
    uiState: CalendarUiState,
    onRetry: () -> Unit,
    onAddEvent: () -> Unit,
    onEventSelected: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = uiState.selectedDate.dayOfMonth.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = uiState.selectedDate.format(weekdayFormatter),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Button(onClick = onAddEvent, enabled = !uiState.isSaving) { Text("+ 일정 추가") }
            }
            uiState.statusMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                Spacer(Modifier.width(24.dp))
                when {
                    uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    uiState.errorMessage != null -> ErrorMessage(uiState.errorMessage, onRetry)
                    uiState.events.isEmpty() -> Message(
                        "${uiState.selectedDate.format(compactDateFormatter)}\n등록된 일정이 없습니다.",
                    )
                    else -> EventList(uiState.events, uiState.tags, onEventSelected)
                }
            }
        }
    }
}

@Composable
private fun EventList(
    events: List<CalendarEvent>,
    tags: List<ScheduleTag>,
    onEventSelected: (CalendarEvent) -> Unit,
) {
    val tagsById = tags.associateBy(ScheduleTag::id)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(events, key = CalendarEvent::id) { event ->
            EventPill(event, event.tagIds.firstOrNull()?.let(tagsById::get)) { onEventSelected(event) }
        }
    }
}

@Composable
private fun EventPill(event: CalendarEvent, tag: ScheduleTag?, onClick: () -> Unit) {
    val pillColor = tag?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier.fillMaxWidth().background(pillColor, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                text = if (event.isAllDay) "종일" else event.localStartTime().format(timeFormatter),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(18.dp))
            Text(
                text = event.title,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        event.location?.address?.takeIf(String::isNotBlank)?.let {
            Text(it, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f))
        }
    }
}

@Composable
private fun ScheduleEditor(
    editor: ScheduleEditorState,
    tags: List<ScheduleTag>,
    isSaving: Boolean,
    onChanged: (ScheduleEditorState) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (editor.mode == ScheduleEditorMode.Create) "새 일정" else "일정 상세 및 수정",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedTextField(
                value = editor.title,
                onValueChange = { onChanged(editor.copy(title = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("일정 제목 *") },
                singleLine = true,
            )
            OutlinedTextField(
                value = editor.description,
                onValueChange = { onChanged(editor.copy(description = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("설명") },
                maxLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EditorField(editor.dateText, { onChanged(editor.copy(dateText = it)) }, "시작 날짜", Modifier.weight(1f))
                EditorField(editor.startTimeText, { onChanged(editor.copy(startTimeText = it)) }, "시작 시간", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EditorField(editor.endDateText, { onChanged(editor.copy(endDateText = it)) }, "종료 날짜", Modifier.weight(1f))
                EditorField(editor.endTimeText, { onChanged(editor.copy(endTimeText = it)) }, "종료 시간", Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("종일 일정", modifier = Modifier.weight(1f))
                Switch(
                    checked = editor.isAllDay,
                    onCheckedChange = { onChanged(editor.copy(isAllDay = it)) },
                )
            }
            OutlinedTextField(
                value = editor.address,
                onValueChange = { onChanged(editor.copy(address = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("장소 또는 주소") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoordinateField(editor.latitudeText, { onChanged(editor.copy(latitudeText = it)) }, "위도", Modifier.weight(1f))
                CoordinateField(editor.longitudeText, { onChanged(editor.copy(longitudeText = it)) }, "경도", Modifier.weight(1f))
            }
            if (tags.isNotEmpty()) {
                Text("태그", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tags, key = ScheduleTag::id) { tag ->
                        FilterChip(
                            selected = tag.id in editor.selectedTagIds,
                            onClick = {
                                val selected = editor.selectedTagIds.toMutableSet().apply {
                                    if (!add(tag.id)) remove(tag.id)
                                }
                                onChanged(editor.copy(selectedTagIds = selected))
                            },
                            label = { Text(tag.name) },
                        )
                    }
                }
            }
            editor.validationError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSave, enabled = !isSaving) {
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (editor.mode == ScheduleEditorMode.Create) "저장" else "수정 저장")
                }
                TextButton(onClick = onClose, enabled = !isSaving) { Text("취소") }
                Spacer(Modifier.weight(1f))
                if (editor.mode == ScheduleEditorMode.Edit) {
                    TextButton(onClick = onDelete, enabled = !isSaving) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        supportingText = { Text(if (label.contains("날짜")) "yyyy-MM-dd" else "HH:mm") },
        singleLine = true,
    )
}

@Composable
private fun CoordinateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("다시 시도") }
    }
}

@Composable
private fun Message(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
