package com.example.safelight.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.net.ApiEnvelope
import com.example.safelight.data.net.EmergencyReportDto
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.ReportStatusRequest
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.errorMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Response

/** 한 번에 받아올 행 수. 카드 목록이라 페이지 번호 대신 이어 붙인다. */
private const val PAGE_SIZE = 20

/** 타이핑 한 글자마다 서버를 때리지 않도록 검색어만 늦춘다. */
private const val SEARCH_DEBOUNCE_MS = 350L

/**
 * 신고 관리. 상태·기간·검색어를 서버가 거르고, 건수는 목록과 따로 센다
 * (그래야 페이지를 넘겨도 칩의 숫자가 흔들리지 않는다).
 */
class AdminReportViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    var reports by mutableStateOf<List<EmergencyReportDto>>(emptyList())
        private set
    var stats by mutableStateOf(ReportStats())
        private set

    var page by mutableStateOf(0)
        private set
    var totalElements by mutableStateOf(0)
        private set
    var isLast by mutableStateOf(true)
        private set

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var filter by mutableStateOf(ReportFilter.All)
        private set
    var startDate by mutableStateOf("")
        private set
    var endDate by mutableStateOf("")
        private set

    /** 입력 중인 값. 확정된 검색어는 [searchTerm] 이다. */
    var search by mutableStateOf("")
        private set
    var searchTerm by mutableStateOf("")
        private set

    /** 스낵바로 한 번 보여주고 지울 문구. */
    var message by mutableStateOf<String?>(null)
        private set

    /**
     * 신고를 하나 처리할 때마다 오른다. 탭바의 '신고' 배지가 이걸 보고 다시 센다 —
     * 허위로 처리하면 서버 쪽 집계에서 그 건이 빠지는데, 배지는 콘솔을 다시 열기 전까지
     * 옛 숫자를 들고 있었다.
     */
    var reportsRevision by mutableStateOf(0)
        private set

    private var debounceJob: Job? = null
    private var started = false

    /** 기간을 거꾸로 넣으면 백엔드가 400 을 던지므로 미리 막는다. */
    val invalidRange: Boolean
        get() = startDate.isNotBlank() && endDate.isNotBlank() && startDate > endDate

    val hasFilter: Boolean
        get() = filter != ReportFilter.All || startDate.isNotBlank() ||
            endDate.isNotBlank() || search.isNotBlank()

    fun messageShown() {
        message = null
    }

    fun start() {
        if (started) return
        started = true
        reload()
    }

    fun selectFilter(next: ReportFilter) {
        if (filter == next) return
        filter = next
        reload()
    }

    fun pickStartDate(value: String) {
        startDate = value
        reload()
    }

    fun pickEndDate(value: String) {
        endDate = value
        reload()
    }

    fun onSearchChange(value: String) {
        search = value
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val trimmed = value.trim()
            if (trimmed == searchTerm) return@launch
            searchTerm = trimmed
            reload()
        }
    }

    fun resetFilters() {
        debounceJob?.cancel()
        filter = ReportFilter.All
        startDate = ""
        endDate = ""
        search = ""
        searchTerm = ""
        reload()
    }

    /** 필터가 바뀌면 언제나 첫 페이지부터 다시 받는다. */
    private fun reload() {
        if (invalidRange) return
        loadPage(0, append = false)
        loadStats()
    }

    fun loadMore() {
        if (loading || isLast) return
        loadPage(page + 1, append = true)
    }

    private fun loadPage(target: Int, append: Boolean) {
        loading = true
        error = null
        viewModelScope.launch {
            val result = api.reportPage(filter, searchTerm, startDate, endDate, target, PAGE_SIZE)
            if (result == null) {
                error = "신고 목록을 불러오지 못했습니다."
            } else {
                reports = if (append) reports + result.reports else result.reports
                page = result.page
                totalElements = result.totalElements.toInt()
                isLast = result.last
            }
            loading = false
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            // 집계가 실패해도 목록까지 막을 이유는 없다.
            runCatching { api.reportStats(startDate, endDate) }.getOrNull()?.let { stats = it }
        }
    }

    fun setStatus(report: EmergencyReportDto, status: String) {
        patch(report.reportId, "상태 변경 실패") { api.setReportStatus(it, ReportStatusRequest(status)) }
    }

    fun markFalse(report: EmergencyReportDto) {
        patch(report.reportId, "허위신고 처리 실패") { api.markFalseReport(it) }
    }

    fun cancelFalse(report: EmergencyReportDto) {
        patch(report.reportId, "허위신고 취소 실패") { api.cancelFalseReport(it) }
    }

    private fun patch(
        reportId: Long,
        failure: String,
        call: suspend (Long) -> Response<ApiEnvelope<EmergencyReportDto>>,
    ) {
        viewModelScope.launch {
            val response = runCatching { call(reportId) }.getOrNull()
            if (response == null) {
                message = "$failure: 서버에 연결하지 못했습니다."
                return@launch
            }
            if (!response.isSuccessful || response.body()?.success != true) {
                message = "$failure: ${response.errorMessage(response.body()?.message ?: "알 수 없는 오류")}"
                return@launch
            }
            reportsRevision++
            val updated = response.body()?.data
            if (updated == null) {
                reload()
                return@launch
            }
            applyUpdated(reportId, updated)
        }
    }

    /**
     * 응답이 갱신된 신고 한 건이라 목록을 다시 받지 않고 그 줄만 바꾼다 —
     * 다시 받으면 '더 보기'로 쌓아 둔 것이 전부 날아간다.
     * 바뀐 뒤 지금 필터에서 벗어난 줄은 목록에서 뺀다.
     */
    private fun applyUpdated(reportId: Long, updated: EmergencyReportDto) {
        reports = reports.mapNotNull { row ->
            if (row.reportId != reportId) row
            else updated.takeIf { matchesFilter(it) }
        }
        loadStats()
    }

    private fun matchesFilter(report: EmergencyReportDto): Boolean = when (filter) {
        ReportFilter.All -> true
        ReportFilter.False -> report.isFalseReport
        ReportFilter.Received -> report.reportStatus == "RECEIVED"
        ReportFilter.Resolved -> report.reportStatus == "RESOLVED"
    }
}
