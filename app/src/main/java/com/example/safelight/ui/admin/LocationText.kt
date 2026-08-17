package com.example.safelight.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safelight.data.net.Network
import com.example.safelight.ui.theme.SafeLightTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 좌표 → 주소. 웹 utils/geocode.js 를 옮긴 것이다.
 *
 * 백엔드는 긴급신고를 위경도로만 내려준다. 숫자 두 개는 사람이 읽을 수 있는 위치가 아니라서
 * "서울 마포구 양화로 45" 를 앞세우고 좌표는 아래에 작게 남긴다 —
 * 좌표는 관리자가 지도 앱에 찍어 넣거나 신고끼리 대조할 때 쓴다.
 */
private object AddressCache {

    /** 값이 null 인 항목도 담는다 — '주소 없음'(바다·산속 등)도 확정된 답이다. */
    private val cache = mutableMapOf<String, String?>()

    /** 같은 좌표를 여러 줄이 동시에 물어보면 요청 하나로 합친다. */
    private val inflight = mutableMapOf<String, CompletableDeferred<String?>>()
    private val lock = Mutex()

    fun key(latitude: Double, longitude: Double): String =
        "%.6f,%.6f".format(latitude, longitude)

    /** 이미 물어본 좌표면 첫 그림부터 주소가 있어 글자가 깜빡이지 않는다. */
    fun cached(latitude: Double, longitude: Double): String? = cache[key(latitude, longitude)]

    fun isKnown(latitude: Double, longitude: Double): Boolean =
        cache.containsKey(key(latitude, longitude))

    suspend fun lookup(latitude: Double, longitude: Double): String? {
        val key = key(latitude, longitude)

        // 잠금 안에서는 '누가 먼저인가'만 정하고, 기다리는 것은 밖에서 한다.
        // 안에서 await 하면 먼저 물어본 쪽이 답을 적어 넣으려고 같은 잠금을 기다리는 사이
        // 둘 다 멈춘다 — 한 화면에 같은 좌표의 신고가 여러 건이면 바로 걸린다.
        var waitFor: CompletableDeferred<String?>? = null
        var pending: CompletableDeferred<String?>? = null
        lock.withLock {
            if (cache.containsKey(key)) return cache[key]
            val running = inflight[key]
            if (running != null) {
                waitFor = running
            } else {
                pending = CompletableDeferred<String?>().also { inflight[key] = it }
            }
        }
        waitFor?.let { return it.await() }
        val mine = pending ?: return null

        val address = runCatching {
            // 카카오는 경도를 x, 위도를 y 로 받는다. 뒤집어 넣으면 엉뚱한 나라가 나온다.
            val document = Network.kakaoLocal
                .coord2Address(longitude = longitude.toString(), latitude = latitude.toString())
                .documents.firstOrNull()
            // 도로명이 없는 곳(공터·산간)도 흔하다. 그때는 지번을 쓴다.
            document?.roadAddress?.addressName?.ifBlank { null }
                ?: document?.address?.addressName?.ifBlank { null }
        }.getOrNull()

        lock.withLock {
            cache[key] = address
            inflight.remove(key)
        }
        mine.complete(address)
        return address
    }
}

/** 좌표 표기는 소수점 5자리(약 1m)면 충분하다. 그 아래는 GPS 오차 범위라 의미가 없다. */
fun fmtCoord(latitude: Double?, longitude: Double?): String =
    if (latitude == null || longitude == null) "-" else "%.5f, %.5f".format(latitude, longitude)

/**
 * 주소 한 줄 + 그 아래 좌표. 조회 중에는 좌표만 보여준다 —
 * '불러오는 중' 문구를 넣으면 목록이 그 문구로 뒤덮인다.
 */
@Composable
fun LocationText(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    addressSize: Double = 13.0,
    coordSize: Double = 11.0,
) {
    val colors = SafeLightTheme.colors
    // 어느 좌표의 답인지 함께 들고 있는다. 좌표가 바뀌는 순간 이전 좌표의 주소를
    // 잠깐이라도 보여주면 엉뚱한 곳이 찍힌다.
    val key = remember(latitude, longitude) { AddressCache.key(latitude, longitude) }
    var entry by remember(key) {
        mutableStateOf(key to AddressCache.cached(latitude, longitude))
    }
    var resolved by remember(key) { mutableStateOf(AddressCache.isKnown(latitude, longitude)) }

    LaunchedEffect(key) {
        if (resolved) return@LaunchedEffect
        val address = AddressCache.lookup(latitude, longitude)
        entry = key to address
        resolved = true
    }

    val address = (entry.second.takeIf { entry.first == key })
        ?.takeIf { it.isNotBlank() }
        ?: if (resolved) "주소를 찾을 수 없는 위치" else null

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (address != null) {
            Text(
                address,
                fontSize = addressSize.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textStrong,
            )
        }
        Text(fmtCoord(latitude, longitude), fontSize = coordSize.sp, color = colors.textMuted)
    }
}
