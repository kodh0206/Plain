package com.rhinos.plain.domain.identity

import kotlinx.coroutines.flow.StateFlow

/**
 * 차량 식별 준비 상태입니다.
 *
 * Firebase 익명 사용자 UID를 차량 식별자로 사용하며 로그인 화면은 노출하지 않습니다.
 * 앱 삭제나 데이터 초기화 후에는 새로운 식별자가 발급될 수 있습니다.
 */
sealed interface VehicleIdentityState {
    /** 저장된 인증 정보를 확인하거나 새 익명 사용자를 생성하는 중입니다. */
    data object Initializing : VehicleIdentityState

    /**
     * 차량 식별자가 준비된 상태입니다.
     *
     * @property vehicleId 서버 요청에서 차량 소유 데이터를 구분할 Firebase UID
     */
    data class Ready(val vehicleId: String) : VehicleIdentityState

    /**
     * 차량 식별자를 준비하지 못한 상태입니다.
     *
     * @property message 사용자에게 표시할 수 있는 간결한 실패 사유
     */
    data class Error(val message: String) : VehicleIdentityState
}

/** 로그인 UI 없이 차량 식별자를 준비하고 상태를 제공하는 계약입니다. */
interface VehicleIdentityProvider {
    /** 애플리케이션 수명 동안 관찰할 수 있는 현재 차량 식별 상태입니다. */
    val state: StateFlow<VehicleIdentityState>

    /** 저장된 익명 인증을 복원하거나 없으면 새 인증을 생성합니다. */
    fun initialize()
}
