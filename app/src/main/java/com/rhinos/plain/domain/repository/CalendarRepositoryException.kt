package com.rhinos.plain.domain.repository

/** 화면에서 의미 있는 오류 상태로 변환할 수 있는 일정 저장소 예외입니다. */
sealed class CalendarRepositoryException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Authentication(cause: Throwable? = null) :
        CalendarRepositoryException("차량 인증이 필요합니다.", cause)

    class Network(cause: Throwable? = null) :
        CalendarRepositoryException("네트워크 연결을 확인한 뒤 다시 시도해 주세요.", cause)

    class Conflict(cause: Throwable? = null) :
        CalendarRepositoryException("다른 곳에서 일정이 변경되었습니다. 새로고침 후 다시 시도해 주세요.", cause)

    class NotFound(cause: Throwable? = null) :
        CalendarRepositoryException("일정을 찾을 수 없습니다.", cause)

    class Server(message: String, cause: Throwable? = null) :
        CalendarRepositoryException(message, cause)
}
