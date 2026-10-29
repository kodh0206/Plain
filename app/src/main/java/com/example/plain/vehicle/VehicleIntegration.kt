package com.example.plain.vehicle

/** 차량 SDK 기능의 사용 가능 여부를 명시적으로 표현합니다. */
sealed interface VehicleIntegrationStatus {
    data object Unavailable : VehicleIntegrationStatus
    data object Ready : VehicleIntegrationStatus
    data class Failed(val reason: String) : VehicleIntegrationStatus
}

/**
 * 향후 Pleos SDK 어댑터가 구현할 차량 연동 경계입니다.
 *
 * 현재 프로젝트에는 Pleos SDK와 권한 정보가 없으므로 실제 차량 기능을 제공한다고
 * 가정하지 않습니다. 이 인터페이스는 플랫폼 코드를 도메인 및 UI와 격리하기 위한 자리입니다.
 */
interface VehicleIntegration {
    val status: VehicleIntegrationStatus

    /** SDK가 보유한 리스너와 자원을 해제합니다. */
    fun release()
}

/** Pleos SDK가 연결되기 전 사용하는 안전한 기본 구현입니다. */
class UnsupportedVehicleIntegration : VehicleIntegration {
    override val status: VehicleIntegrationStatus = VehicleIntegrationStatus.Unavailable

    override fun release() = Unit
}
