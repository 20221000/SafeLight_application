package com.example.safelight.ui.common

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationSettingsRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.safelight.ui.theme.SafeLightTheme
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * 화면 여럿이 똑같이 갖고 있던 조각들을 한곳에 모았다.
 * 여기 있는 것은 '어느 화면에 속한 UI'가 아니라 '어느 화면에서나 같은 모양'인 것들만이다.
 */

/**
 * 목록이 비었을 때의 회색 한 줄. 다섯 화면이 각자 정의하고 있었다(글자 크기·색·정렬은 전부 같고
 * 여백만 달랐다).
 *
 * 기본 여백은 쪽지·알림 화면 값이다. 다른 여백을 쓰던 화면은 호출부에서 넘긴다 —
 * 한 값으로 통일하면 그 화면들의 간격이 바뀐다.
 */
@Composable
fun EmptyText(
    text: String,
    horizontal: Dp = 20.dp,
    vertical: Dp = 48.dp,
) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontal, vertical = vertical),
        fontSize = 13.sp,
        color = SafeLightTheme.colors.textMuted,
        textAlign = TextAlign.Center,
    )
}

/** 지도 우하단 줌·현재위치 버튼을 담는 흰 판. 지도 화면과 경로 화면이 글자 하나까지 같았다. */
@Composable
fun MapControlSurface(content: @Composable () -> Unit) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(11.dp)),
    ) { content() }
}

@Composable
fun MapControlButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * 위치 권한을 물을 때 넘기는 목록. 정밀·대략 위치를 **반드시 함께** 묻는다 —
 * Android 12 부터는 정밀 위치만 물으면 시스템이 요청을 무시해 권한 창이 아예 뜨지 않는다.
 * 사용자는 창에서 '정확한 위치'와 '대략적인 위치' 중 하나를 고른다.
 * 결과는 `RequestMultiplePermissions` 로 받고, 둘 중 하나라도 허용이면 위치를 쓸 수 있다.
 */
val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/**
 * 위치 권한이 있는지. 지도·경로·SOS 세 곳이 각자 같은 확장함수를 갖고 있었다.
 * 위치를 읽기 전에 반드시 이걸로 먼저 확인한다(@SuppressLint("MissingPermission") 의 근거다).
 * '대략적인 위치'만 허용해도 true 다 — 예전에는 정밀 위치만 봐서 그 사람은 위치를 전혀 못 썼다
 * (대략적인 위치는 수 km 단위로 흐려져서 내 위치 점이 실제와 떨어져 보일 수 있다).
 */
fun Context.hasLocationPermission(): Boolean =
    LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

/** 휴대폰의 위치(GPS) 설정이 켜져 있는지. 꺼져 있으면 권한이 있어도 위치가 오지 않는다. */
fun Context.isLocationEnabled(): Boolean {
    val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return LocationManagerCompat.isLocationEnabled(manager)
}

/**
 * 휴대폰 위치(GPS)가 꺼져 있으면 시스템의 '위치 사용' 창을 띄울 수 있게 해 준다.
 * 켜져 있으면 아무 일도 하지 않는다. [onResolvable] 에 받은 예외의 `resolution` 을
 * StartIntentSenderForResult 로 띄우면 된다.
 */
fun Context.checkLocationSettings(onResolvable: (ResolvableApiException) -> Unit) {
    val request = LocationSettingsRequest.Builder()
        .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TRACK_INTERVAL_MS).build())
        .build()
    LocationServices.getSettingsClient(this)
        .checkLocationSettings(request)
        .addOnFailureListener { if (it is ResolvableApiException) onResolvable(it) }
}

/** 마지막 위치를 그대로 써도 되는 나이. 이보다 오래되면 이미 다른 곳에 있을 수 있다. */
private const val LAST_LOCATION_MAX_AGE_MS = 2 * 60 * 1000L

/**
 * 경로 화면의 출발지 '현재 위치' 처럼 한 번만 필요한 위치. 빨리 뜨도록 휴대폰에 남아 있는
 * 마지막 위치를 먼저 쓰되 2분이 넘었으면 버리고 새로 받는다 — 예전에는 나이를 안 봐서
 * 몇 시간 전 다른 동네가 찍힐 수 있었다. 마지막 위치가 아예 없을 때(재부팅 직후)도 새로 받는다.
 * 지도에서 계속 따라가는 위치는 [rememberMyLocation], 긴급신고는 항상 새로 받는다(SosViewModel).
 */
@SuppressLint("MissingPermission")   // 부르기 전에 hasLocationPermission 으로 확인한다
fun Context.fetchMyLocation(onLocation: (Double, Double) -> Unit, onFailure: (Exception) -> Unit) {
    val client = LocationServices.getFusedLocationProviderClient(this)
    client.lastLocation
        .addOnSuccessListener { last ->
            val ageMs = last?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 }
            if (last != null && ageMs != null && ageMs <= LAST_LOCATION_MAX_AGE_MS) {
                onLocation(last.latitude, last.longitude)
                return@addOnSuccessListener
            }
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { now -> if (now != null) onLocation(now.latitude, now.longitude) }
                .addOnFailureListener(onFailure)
        }
        .addOnFailureListener(onFailure)
}

/**
 * 가장 최근에 받은 위치(웹 useMyLocation 의 getRecentFix 자리). 지도 화면이 떠 있는 동안
 * [rememberMyLocation] 이 계속 채운다. 긴급신고가 GPS 를 처음부터 다시 잡느라 기다리지 않도록 같이 쓴다.
 */
object RecentLocation {
    @Volatile
    private var last: Location? = null

    fun update(location: Location) {
        last = location
    }

    /** [maxAgeMs] 안에 받은, 오차 [maxAccuracyM] 이내의 (위도, 경도). 없으면 null. */
    fun get(maxAgeMs: Long, maxAccuracyM: Float): Pair<Double, Double>? {
        val fix = last ?: return null
        val ageMs = (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000
        if (ageMs > maxAgeMs) return null
        if (!fix.hasAccuracy() || fix.accuracy > maxAccuracyM) return null
        return fix.latitude to fix.longitude
    }
}

/**
 * 걸을 때 위치를 받는 간격. 지도 화면은 점을 이 시간 동안 미끄러지듯 옮겨서 끊김 없이 이어 보이게 한다.
 * 예전 2초는 걸을 때 점이 뚝뚝 끊겨 보였다. 지도 화면이 떠 있는 동안만 받으므로 배터리 부담은 크지 않다.
 */
const val TRACK_INTERVAL_MS = 1_000L

/** 이보다 덜 움직였으면 새 위치로 치지 않는다 — 제자리에서 GPS 오차로 점이 떨리는 걸 막는다. */
private const val TRACK_MIN_DISTANCE_M = 2f

/**
 * 내 위치를 계속 받는다(웹 useMyLocation 자리). 걸어가면 돌려주는 값이 따라 바뀐다.
 *
 * 예전에는 지도가 뜰 때와 버튼을 누를 때만 한 번씩 받아서, 야외에서 걸어도 점이 멈춰 있었다.
 * 화면이 보이는 동안(ON_START~ON_STOP)만 받는다 — 앱을 닫으면 멈추므로 백그라운드 위치 권한이 필요 없다.
 * [enabled] 는 위치 권한이 있는지다. 권한을 받는 순간 true 로 바뀌면 그때부터 받기 시작한다.
 *
 * 반환: (위도, 경도), 아직 한 번도 못 받았으면 null.
 */
@SuppressLint("MissingPermission")   // enabled = hasLocationPermission() 이고, 시작할 때 한 번 더 확인한다
@Composable
fun rememberMyLocation(enabled: Boolean): Pair<Double, Double>? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var location by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) return@DisposableEffect onDispose {}
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TRACK_INTERVAL_MS)
            .setMinUpdateDistanceMeters(TRACK_MIN_DISTANCE_M)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    RecentLocation.update(it)
                    location = it.latitude to it.longitude
                }
            }
        }
        var running = false
        fun start() {
            if (running || !context.hasLocationPermission()) return
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            running = true
        }
        fun stop() {
            if (!running) return
            client.removeLocationUpdates(callback)
            running = false
        }
        // 이미 화면이 보이는 중이면 addObserver 가 ON_START 를 곧바로 한 번 보내 준다.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }
    return location
}
