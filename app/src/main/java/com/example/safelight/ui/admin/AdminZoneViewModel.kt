package com.example.safelight.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.net.DangerLevelRequest
import com.example.safelight.data.net.DangerZoneDto
import com.example.safelight.data.net.EmergencyReportDto
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.ReportStatusRequest
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.errorMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** 위험도 세 등급. 시트에 이 순서로 놓는다. */
val DANGER_LEVELS = listOf("LOW", "MEDIUM", "HIGH")

/**
 * 위험구역 관리.
 *
 * 구역은 긴급신고로 자동 생성된다 — 만드는 API 가 없고 이름 필드도 없어서
 * 웹 목업의 '+ 등록'과 구역명은 넣지 않았다.
 */
class AdminZoneViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    var zones by mutableStateOf<List<DangerZoneDto>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /**
     * 지도가 비추고 있는 구역. id 만 들고 있으면 같은 구역을 다시 눌렀을 때 상태가 그대로라
     * 지도가 안 움직인다 — 지도를 손으로 옮겨 놓고 되돌릴 수가 없다. 누를 때마다 [focusSeq] 를 올린다.
     */
    var focusId by mutableStateOf<Long?>(null)
        private set
    var focusSeq by mutableStateOf(0)
        private set

    /** 열려 있는 신고 내역. */
    var detailZone by mutableStateOf<DangerZoneDto?>(null)
        private set
    var detailReports by mutableStateOf<List<EmergencyReportDto>>(emptyList())
        private set
    var detailLoading by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    /** 신고를 처리할 때마다 오른다. 탭바의 '신고' 배지가 이걸 보고 다시 센다. */
    var reportsRevision by mutableStateOf(0)
        private set

    private var started = false

    val highCount: Int get() = zones.count { it.dangerLevel == "HIGH" }

    /** 이번 주에 새로 생긴 구역. 그릴 때마다 시각을 재면 화면이 흔들려서 불러올 때 한 번만 센다. */
    var newThisWeek by mutableStateOf(0)
        private set

    fun messageShown() {
        message = null
    }

    fun start() {
        if (started) return
        started = true
        load()
    }

    fun focus(zoneId: Long) {
        focusId = zoneId
        focusSeq++
    }

    private fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            val envelope = runCatching { api.getDangerZones() }.getOrNull()
            if (envelope == null || !envelope.success) {
                error = envelope?.message ?: "위험구역을 불러오지 못했습니다."
            } else {
                val list = envelope.data.orEmpty()
                zones = list
                val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                newThisWeek = list.count { (parseTime(it.createdAt) ?: 0L) >= weekAgo }
            }
            loading = false
        }
    }

    fun changeLevel(zone: DangerZoneDto, level: String) {
        if (level == zone.dangerLevel) return
        viewModelScope.launch {
            val response = runCatching {
                api.setDangerLevel(zone.dangerZoneId, DangerLevelRequest(level))
            }.getOrNull()
            if (response == null || response.body()?.success != true) {
                message = "위험도 변경 실패: " +
                    (response?.errorMessage("알 수 없는 오류") ?: "서버에 연결하지 못했습니다.")
                return@launch
            }
            message = "위험구역 #${zone.dangerZoneId} 을(를) $level 로 바꿨습니다."
            load()
        }
    }

    fun deactivate(zone: DangerZoneDto) {
        viewModelScope.launch {
            val response = runCatching { api.deactivateDangerZone(zone.dangerZoneId) }.getOrNull()
            if (response == null || response.body()?.success != true) {
                message = "비활성화 실패: " +
                    (response?.errorMessage("알 수 없는 오류") ?: "서버에 연결하지 못했습니다.")
                return@launch
            }
            message = "위험구역 #${zone.dangerZoneId} 을(를) 비활성화했습니다."
            load()
        }
    }

    /**
     * 신고 내역. 구역 요약은 목록에 있는 것을 그대로 쓰고 신고만 따로 받는다 —
     * 상세 응답은 목록 한 줄과 같은 레코드라 다시 받을 이유가 없다.
     */
    fun openDetail(zone: DangerZoneDto) {
        detailZone = zone
        detailReports = emptyList()
        loadDetailReports(zone.dangerZoneId)
    }

    fun closeDetail() {
        detailZone = null
        detailReports = emptyList()
    }

    private fun loadDetailReports(zoneId: Long) {
        detailLoading = true
        viewModelScope.launch {
            val response = runCatching { api.getZoneReports(zoneId) }.getOrNull()
            detailReports =
                if (response?.isSuccessful == true && response.body()?.success == true) {
                    response.body()?.data.orEmpty()
                } else {
                    message = "신고 내역을 불러오지 못했습니다."
                    emptyList()
                }
            detailLoading = false
        }
    }

    fun setReportStatus(report: EmergencyReportDto, status: String) {
        patchReport("상태 변경 실패") { api.setReportStatus(report.reportId, ReportStatusRequest(status)) }
    }

    fun markFalse(report: EmergencyReportDto) {
        patchReport("허위신고 처리 실패") { api.markFalseReport(report.reportId) }
    }

    fun cancelFalse(report: EmergencyReportDto) {
        patchReport("허위신고 취소 실패") { api.cancelFalseReport(report.reportId) }
    }

    private fun patchReport(
        failure: String,
        call: suspend () -> retrofit2.Response<com.example.safelight.data.net.ApiEnvelope<EmergencyReportDto>>,
    ) {
        viewModelScope.launch {
            val response = runCatching { call() }.getOrNull()
            if (response == null || response.body()?.success != true) {
                message = "$failure: " +
                    (response?.errorMessage("알 수 없는 오류") ?: "서버에 연결하지 못했습니다.")
                return@launch
            }
            reportsRevision++
            // 신고 상태가 바뀌면 구역의 신고 수·위험도까지 다시 계산될 수 있어 둘 다 받는다.
            detailZone?.let { loadDetailReports(it.dangerZoneId) }
            load()
        }
    }
}

// ── 자동 해제 ────────────────────────────────────────────────────────────────
// 구역은 긴급신고로 만들어질 때 expiredAt = 생성 + 24시간 이 박힌다. 목록은 아직 만료되지 않은
// 것만 주므로 이 값은 언제나 '해제 예정 시각'이다.

private val TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

/** 뒤에 붙는 소수점 이하 초는 parse 가 알아서 무시한다. */
fun parseTime(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching { TIME_FORMAT.parse(iso)?.time }.getOrNull()
}

fun remainText(iso: String?): String? {
    val target = parseTime(iso) ?: return null
    val ms = target - System.currentTimeMillis()
    if (ms <= 0) return "해제 대기"
    val minutes = ms / 60000
    if (minutes < 60) return "${minutes}분 뒤 해제"
    val hours = minutes / 60
    return if (minutes % 60 == 0L) "${hours}시간 뒤 해제" else "${hours}시간 ${minutes % 60}분 뒤 해제"
}

/** 1시간 미만이면 곧 사라지는 구역이라 눈에 띄게 한다. */
fun isExpiringSoon(iso: String?): Boolean {
    val target = parseTime(iso) ?: return false
    val ms = target - System.currentTimeMillis()
    return ms in 1..3_600_000
}
