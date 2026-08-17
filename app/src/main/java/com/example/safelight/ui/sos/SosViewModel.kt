package com.example.safelight.ui.sos

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.net.EmergencyReportCreateRequest
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.errorMessage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** 확인 창이 저절로 접수되기까지. 웹과 같은 3초다. */
internal const val COUNTDOWN_MS = 3_000L

/** 완료 배너가 남아 있는 시간. 웹과 같은 6초다. */
internal const val DONE_MS = 6_000L

enum class SosPhase { Idle, Confirm, Done }

/** 실패·안내 창 하나. [retry] 면 확인 버튼이 '다시 시도'가 된다. */
data class SosDialog(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val retry: Boolean = false,
)

/**
 * 긴급 신고 접수. 웹 SosButton 의 handleConfirm 을 옮긴 것이다.
 *
 * 화면이 아니라 뷰모델이 들고 있는 이유: 접수 도중 화면이 다시 그려져도(지도 갱신·시트 이동)
 * 진행 중인 요청과 단계가 그대로 이어져야 한다.
 */
class SosViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    var phase by mutableStateOf(SosPhase.Idle)
        private set
    var loading by mutableStateOf(false)
        private set
    var reportId by mutableStateOf<Long?>(null)
        private set
    var dialog by mutableStateOf<SosDialog?>(null)
        private set

    /**
     * 접수가 진행 중인지 곧바로 기록한다. [loading] 은 상태라 다음 그림까지 반영되지 않아
     * 연타 사이의 짧은 순간을 막지 못한다 — 웹에서 같은 신고가 누른 횟수만큼 올라가던 원인이다.
     */
    private var submitting = false

    fun openConfirm() {
        if (loading) return
        phase = SosPhase.Confirm
    }

    fun cancel() {
        if (loading) return
        phase = SosPhase.Idle
    }

    fun dismissDone() {
        if (phase == SosPhase.Done) phase = SosPhase.Idle
    }

    fun dismissDialog() {
        dialog = null
    }

    fun onPermissionDenied() {
        phase = SosPhase.Idle
        dialog = SosDialog(
            title = "위치 권한이 꺼져 있습니다",
            message = "지금 어디 계신지 알 수 없어 신고를 보낼 수 없습니다. " +
                "설정 → 앱 → Safe Light → 권한 → 위치를 허용으로 바꾼 뒤 다시 눌러주세요.",
            confirmLabel = "확인",
        )
    }

    /** 확인 버튼 또는 3초 카운트다운이 부른다. 권한이 없으면 [requestPermission] 로 넘긴다. */
    fun confirm(context: Context, requestPermission: (String) -> Unit) {
        if (submitting) return
        if (!context.hasLocationPermission()) {
            requestPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        submit(context)
    }

    fun submit(context: Context) {
        if (submitting) return
        submitting = true
        loading = true
        viewModelScope.launch {
            val location = currentLocation(context)
            if (location == null) {
                loading = false
                submitting = false
                phase = SosPhase.Idle
                // 실내·지하에서 흔하다. 사용자 잘못이 아니라는 걸 알려주고 재시도로 잇는다.
                dialog = SosDialog(
                    title = "위치를 확인하지 못했습니다",
                    message = "신호가 약한 곳에서는 시간이 더 걸릴 수 있습니다. " +
                        "창가나 실외로 나가서 다시 시도해주세요.",
                    confirmLabel = "다시 시도",
                    retry = true,
                )
                return@launch
            }

            val response = runCatching {
                api.createEmergencyReport(
                    EmergencyReportCreateRequest(
                        latitude = location.first,
                        longitude = location.second,
                        description = "긴급 신고",
                    ),
                )
            }.getOrNull()

            loading = false
            submitting = false

            if (response == null || response.body()?.success != true) {
                phase = SosPhase.Idle
                dialog = SosDialog(
                    title = "신고를 접수하지 못했습니다",
                    // 블랙리스트·검증 실패 문구가 error.message 에 담겨 온다. 그대로 보여준다 —
                    // 왜 안 되는지는 서버만 알고 있다.
                    message = response?.errorMessage("잠시 후 다시 시도해주세요.")
                        ?: "네트워크 상태를 확인한 뒤 다시 시도해주세요.",
                    confirmLabel = "다시 시도",
                    retry = true,
                )
                return@launch
            }

            reportId = response.body()?.data?.reportId
            phase = SosPhase.Done
        }
    }

    /**
     * 지금 위치. 캐시된 마지막 위치([lastLocation])를 쓰지 않는다 —
     * 몇 시간 전 다른 동네의 좌표가 그대로 신고에 실릴 수 있다.
     * 웹도 `maximumAge: 0, enableHighAccuracy: true` 로 새로 받는다.
     */
    @SuppressLint("MissingPermission")   // 부르기 전에 hasLocationPermission 으로 확인한다
    private suspend fun currentLocation(context: Context): Pair<Double, Double>? =
        suspendCoroutine { continuation ->
            val cancellation = CancellationTokenSource()
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                .addOnSuccessListener { location ->
                    continuation.resume(location?.let { it.latitude to it.longitude })
                }
                .addOnFailureListener { continuation.resume(null) }
        }
}
