package com.rhinos.plain.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.rhinos.plain.domain.model.CalendarEvent
import com.rhinos.plain.domain.model.ScheduleLocation
import com.rhinos.plain.domain.repository.CalendarRepositoryException
import java.time.Instant
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Android 모델과 Firebase callable 함수의 Map 기반 계약을 변환합니다.
 *
 * 모든 요청 전에 익명 인증을 보장하며, Firebase 오류 코드를 도메인 저장소 예외로
 * 변환해 ViewModel이 SDK 세부사항을 알 필요가 없도록 합니다.
 */
class FirebaseCalendarRemoteDataSource(
    private val firebaseAuth: FirebaseAuth,
    private val functions: FirebaseFunctions,
) {
    private val authenticationMutex = Mutex()

    /** 지정 범위에 시작하는 서버 일정을 시간순으로 조회합니다. */
    suspend fun getSchedules(startAt: Instant, endAt: Instant): List<CalendarEvent> {
        val response = call(
            name = "getSchedules",
            data = mapOf(
                "startAtMillis" to startAt.toEpochMilli(),
                "endAtMillis" to endAt.toEpochMilli(),
            ),
        )
        return response.mapValue("schedules").asList().map { it.asStringMap().toEvent() }
    }

    /** 단일 일정을 서버에서 조회합니다. */
    suspend fun getSchedule(scheduleId: String): CalendarEvent =
        call("getSchedule", mapOf("scheduleId" to scheduleId)).toEvent()

    /** 클라이언트 생성 ID를 요청 ID로 사용해 새 일정을 중복 없이 생성합니다. */
    suspend fun createSchedule(event: CalendarEvent): CalendarEvent =
        call("createSchedule", event.toRequestMap() + ("requestId" to event.id)).toEvent()

    /** 현재 리비전을 포함해 일정을 수정합니다. */
    suspend fun updateSchedule(event: CalendarEvent): CalendarEvent =
        call(
            "updateSchedule",
            event.toRequestMap() + mapOf(
                "scheduleId" to event.id,
                "expectedRevision" to event.revision,
            ),
        ).toEvent()

    /** 현재 리비전과 일치하는 일정을 삭제합니다. */
    suspend fun deleteSchedule(event: CalendarEvent) {
        call(
            "deleteSchedule",
            mapOf("scheduleId" to event.id, "expectedRevision" to event.revision),
        )
    }

    private suspend fun call(name: String, data: Map<String, Any?>): Map<String, Any?> {
        ensureAuthenticated()
        return try {
            functions.getHttpsCallable(name).call(data).await().data.asStringMap()
        } catch (error: FirebaseFunctionsException) {
            throw error.toRepositoryException()
        } catch (error: Exception) {
            throw CalendarRepositoryException.Network(error)
        }
    }

    private suspend fun ensureAuthenticated() {
        if (firebaseAuth.currentUser != null) return
        authenticationMutex.withLock {
            if (firebaseAuth.currentUser == null) {
                try {
                    firebaseAuth.signInAnonymously().await()
                } catch (error: Exception) {
                    throw CalendarRepositoryException.Authentication(error)
                }
            }
        }
    }
}

private fun CalendarEvent.toRequestMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "description" to description,
    "startAtMillis" to startAt.toEpochMilli(),
    "endAtMillis" to endAt.toEpochMilli(),
    "timeZoneId" to timeZoneId,
    "isAllDay" to isAllDay,
    "location" to location?.let {
        mapOf("address" to it.address, "latitude" to it.latitude, "longitude" to it.longitude)
    },
    "tags" to tagIds,
)

private fun Map<String, Any?>.toEvent(): CalendarEvent {
    val location = this["location"]?.asStringMap()?.let { value ->
        ScheduleLocation(
            address = value.stringValue("address"),
            latitude = value.optionalDouble("latitude"),
            longitude = value.optionalDouble("longitude"),
        )
    }
    return CalendarEvent(
        id = stringValue("id"),
        title = stringValue("title"),
        description = stringValue("description"),
        startAt = Instant.ofEpochMilli(longValue("startAtMillis")),
        endAt = Instant.ofEpochMilli(longValue("endAtMillis")),
        timeZoneId = stringValue("timeZoneId"),
        isAllDay = booleanValue("isAllDay"),
        location = location,
        tagIds = mapValue("tags").asList().map { it as String },
        createdAt = Instant.ofEpochMilli(longValue("createdAtMillis")),
        modifiedAt = Instant.ofEpochMilli(longValue("updatedAtMillis")),
        revision = longValue("revision"),
    )
}

private fun FirebaseFunctionsException.toRepositoryException(): CalendarRepositoryException =
    when (code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> CalendarRepositoryException.Authentication(this)
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        -> CalendarRepositoryException.Network(this)
        FirebaseFunctionsException.Code.ABORTED -> CalendarRepositoryException.Conflict(this)
        FirebaseFunctionsException.Code.NOT_FOUND -> CalendarRepositoryException.NotFound(this)
        else -> CalendarRepositoryException.Server(message ?: "서버 요청에 실패했습니다.", this)
    }

private fun Any?.asStringMap(): Map<String, Any?> {
    val source = this as? Map<*, *> ?: throw CalendarRepositoryException.Server("서버 응답 형식이 올바르지 않습니다.")
    return source.entries.associate { (key, value) -> key.toString() to value }
}

private fun Any?.asList(): List<Any?> =
    this as? List<*> ?: throw CalendarRepositoryException.Server("서버 목록 응답 형식이 올바르지 않습니다.")

private fun Map<String, Any?>.mapValue(key: String): Any? =
    this[key] ?: throw CalendarRepositoryException.Server("서버 응답에 $key 값이 없습니다.")

private fun Map<String, Any?>.stringValue(key: String): String =
    this[key] as? String ?: throw CalendarRepositoryException.Server("서버 응답의 $key 형식이 올바르지 않습니다.")

private fun Map<String, Any?>.longValue(key: String): Long =
    (this[key] as? Number)?.toLong()
        ?: throw CalendarRepositoryException.Server("서버 응답의 $key 형식이 올바르지 않습니다.")

private fun Map<String, Any?>.booleanValue(key: String): Boolean =
    this[key] as? Boolean
        ?: throw CalendarRepositoryException.Server("서버 응답의 $key 형식이 올바르지 않습니다.")

private fun Map<String, Any?>.optionalDouble(key: String): Double? =
    this[key]?.let { value ->
        (value as? Number)?.toDouble()
            ?: throw CalendarRepositoryException.Server("서버 응답의 $key 형식이 올바르지 않습니다.")
    }
