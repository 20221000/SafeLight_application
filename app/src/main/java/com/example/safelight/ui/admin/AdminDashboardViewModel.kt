package com.example.safelight.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.net.ApiEnvelope
import com.example.safelight.data.net.DangerZoneDto
import com.example.safelight.data.net.EmergencyReportDto
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.UserProfileDto
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import retrofit2.Response

/**
 * 관리자 대시보드. 웹 AdminDashboardPage 의 모바일 분기와 같은 것을 센다.
 *
 * 목업에 있던 '전주 대비 증감'과 '신고 접수 추이' 차트는 넣지 않았다 —
 * 백엔드에 과거 스냅샷도, 해결 시각도 없어 정확한 시계열을 만들 수 없다(지어낼 수는 없다).
 */
class AdminDashboardViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    var users by mutableStateOf<List<UserProfileDto>>(emptyList())
        private set
    var zones by mutableStateOf<List<DangerZoneDto>>(emptyList())
        private set
    var recentReports by mutableStateOf<List<EmergencyReportDto>>(emptyList())
        private set
    var stats by mutableStateOf(ReportStats())
        private set

    /** 요약 API 가 막히면(권한·네트워크) 목록 길이로 대신한다. */
    var totalPosts by mutableStateOf(0L)
        private set
    var todayPosts by mutableStateOf(0L)
        private set
    private var summaryUsers: Long? = null

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val totalUsers: Long get() = summaryUsers ?: users.size.toLong()
    val blacklistCount: Int get() = users.count { it.isBlacklisted }

    private val revision = AdminRevision()

    /** 처음 열면 불러오고, 다른 탭에서 신고·회원을 처리했으면 숫자를 띄워 둔 채 조용히 다시 받는다. */
    fun sync(dataRevision: Int) =
        revision.follow(dataRevision, first = { load() }, changed = { load(silent = true) })

    fun refresh() = load()

    /** [silent] 면 KPI 를 '-' 로 비우지 않는다 — 다시 받는 사이 숫자가 깜빡이면 오히려 헷갈린다. */
    private fun load(silent: Boolean = false) {
        if (!silent) loading = true
        error = null
        viewModelScope.launch {
            // 한 곳이 실패해도 나머지 숫자는 살린다. 게시글 집계를 못 받았다고
            // 신고 건수까지 감추면 관제 화면으로 쓸 수 없다.
            val usersCall = async { runCatching { api.getAllUsers() }.getOrNull() }
            val zonesCall = async { runCatching { api.getDangerZones() }.getOrNull() }
            val recentCall = async { api.reportPage(size = 5) }
            val statsCall = async { runCatching { api.reportStats() }.getOrNull() }
            val summaryCall = async { runCatching { api.getDashboardSummary() }.getOrNull() }

            val userList = usersCall.await()
            if (userList == null || userList.body()?.success != true) {
                error = "회원 목록을 불러오지 못했습니다."
            }
            users = userList?.dataOrNull().orEmpty()

            zonesCall.await()?.let { envelope ->
                if (envelope.success) zones = envelope.data.orEmpty()
            }
            recentReports = recentCall.await()?.reports.orEmpty()
            statsCall.await()?.let { stats = it }
            summaryCall.await()?.dataOrNull()?.let {
                totalPosts = it.totalPosts
                todayPosts = it.todayPosts
                summaryUsers = it.totalUsers
            }
            if (!silent) loading = false
        }
    }

    private fun <T> Response<ApiEnvelope<T>>.dataOrNull(): T? =
        if (isSuccessful && body()?.success == true) body()?.data else null
}
