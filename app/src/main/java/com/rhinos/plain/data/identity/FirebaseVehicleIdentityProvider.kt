package com.rhinos.plain.data.identity

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.rhinos.plain.domain.identity.VehicleIdentityProvider
import com.rhinos.plain.domain.identity.VehicleIdentityState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Firebase 익명 인증 UID를 차량 식별자로 제공하는 구현체입니다.
 *
 * 이미 인증된 사용자가 있으면 네트워크 요청 없이 UID를 재사용합니다. 인증 정보가
 * 없을 때만 익명 로그인을 요청하므로 차량 사용자가 로그인 화면을 조작할 필요가 없습니다.
 *
 * @param firebaseAuth Firebase Authentication 진입점
 */
class FirebaseVehicleIdentityProvider(
    private val firebaseAuth: FirebaseAuth,
) : VehicleIdentityProvider {
    private val mutableState = MutableStateFlow<VehicleIdentityState>(
        VehicleIdentityState.Initializing,
    )

    override val state: StateFlow<VehicleIdentityState> = mutableState.asStateFlow()

    override fun initialize() {
        firebaseAuth.currentUser?.uid?.let { savedUid ->
            mutableState.value = VehicleIdentityState.Ready(savedUid)
            Log.i(TAG, "저장된 차량 익명 인증을 복원했습니다.")
            return
        }

        mutableState.value = VehicleIdentityState.Initializing
        firebaseAuth.signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                mutableState.value = if (uid != null) {
                    Log.i(TAG, "차량 익명 인증을 생성했습니다.")
                    VehicleIdentityState.Ready(uid)
                } else {
                    VehicleIdentityState.Error("차량 식별자를 확인할 수 없습니다.")
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "차량 익명 인증에 실패했습니다.", error)
                mutableState.value = VehicleIdentityState.Error(
                    "차량 인증에 실패했습니다. 네트워크 연결을 확인해 주세요.",
                )
            }
    }

    private companion object {
        const val TAG = "PlainVehicleIdentity"
    }
}
