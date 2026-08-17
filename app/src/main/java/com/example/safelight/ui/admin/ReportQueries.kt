package com.example.safelight.ui.admin

import com.example.safelight.data.net.AdminReportPageDto
import com.example.safelight.data.net.SafeLightApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 관리자 신고 조회 공용. 웹 reportsAggregate.js 를 옮긴 것이다.
 *
 * 상태·기간·키워드는 **서버가** 거른다. 예전 웹은 전체를 긁어와 화면에서 걸렀는데,
 * 그러면 한 페이지 안에서만 맞는 결과라 건수도 페이지도 어긋났다.
 */

/** 필터 코드. '허위'는 상태가 아니라 플래그로 거른다 — 상태만 FALSE 인 경로가 따로 있다. */
enum class ReportFilter(val label: String) {
    All("전체"),
    Received("접수"),
    Resolved("해결"),
    False("오탐"),
}

data class ReportStats(
    val total: Int = 0,
    val received: Int = 0,
    val resolved: Int = 0,
    val falseCount: Int = 0,
)

/** `<input type=date>` 자리의 `2026-08-17` 을 백엔드가 요구하는 ISO_DATE_TIME 으로. */
private fun startOfDay(date: String?): String? =
    date?.takeIf { it.isNotBlank() }?.let { "${it}T00:00:00" }

/** 종료일은 그 날 하루를 통째로 포함해야 한다. */
private fun endOfDay(date: String?): String? =
    date?.takeIf { it.isNotBlank() }?.let { "${it}T23:59:59" }

private fun statusParam(filter: ReportFilter): String? = when (filter) {
    ReportFilter.Received -> "RECEIVED"
    ReportFilter.Resolved -> "RESOLVED"
    else -> null
}

private fun falseParam(filter: ReportFilter): Boolean? =
    if (filter == ReportFilter.False) true else null

suspend fun SafeLightApi.reportPage(
    filter: ReportFilter = ReportFilter.All,
    keyword: String = "",
    startDate: String = "",
    endDate: String = "",
    page: Int = 0,
    size: Int = 20,
): AdminReportPageDto? {
    val response = runCatching {
        getAdminReports(
            page = page,
            size = size,
            status = statusParam(filter),
            isFalseReport = falseParam(filter),
            keyword = keyword.takeIf { it.isNotBlank() },
            startDate = startOfDay(startDate),
            endDate = endOfDay(endDate),
        )
    }.getOrNull() ?: return null
    if (!response.isSuccessful || response.body()?.success != true) return null
    return response.body()?.data
}

/** 건수만 필요할 때. size=1 로 부르고 totalElements 만 읽는다(행은 한 건만 오간다). */
private suspend fun SafeLightApi.reportCount(
    filter: ReportFilter,
    startDate: String,
    endDate: String,
): Int = reportPage(filter, "", startDate, endDate, 0, 1)?.totalElements?.toInt() ?: 0

/**
 * KPI 4종. 목록과 따로 센다 — 페이지를 넘겨도 숫자가 흔들리지 않는다.
 * 검색어는 넣지 않는다(웹과 같다). 칩의 숫자는 '지금 조건에서 몇 건인가'가 아니라
 * '기간 안에 상태별로 몇 건인가'여서, 검색어까지 걸면 고르기 전에 셀 수가 없다.
 */
suspend fun SafeLightApi.reportStats(startDate: String = "", endDate: String = ""): ReportStats =
    coroutineScope {
        val total = async { reportCount(ReportFilter.All, startDate, endDate) }
        val received = async { reportCount(ReportFilter.Received, startDate, endDate) }
        val resolved = async { reportCount(ReportFilter.Resolved, startDate, endDate) }
        val falseCount = async { reportCount(ReportFilter.False, startDate, endDate) }
        ReportStats(total.await(), received.await(), resolved.await(), falseCount.await())
    }
