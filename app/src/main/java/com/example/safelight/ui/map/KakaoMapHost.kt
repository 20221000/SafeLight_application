package com.example.safelight.ui.map

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.safelight.BuildConfig
import com.example.safelight.ui.theme.SafeLightTheme
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView

private const val TAG = "KakaoMapHost"

/**
 * 카카오 지도를 화면에 올리는 껍데기. 지도를 쓰는 화면(지도 탭·경로 안내)이 둘이라
 * 생명주기 연결과 준비 콜백을 여기 모아 둔다.
 *
 * [onReady] 는 지도가 준비되면 한 번 불린다 — 이 시점부터 [MapLayers] 로 그릴 수 있다.
 * [onCameraIdle] 은 지도를 놓을 때마다 불린다(웹의 'idle' 리스너 자리).
 */
@Composable
fun KakaoMapHost(
    modifier: Modifier = Modifier,
    initialPosition: LatLng = LatLng.from(INITIAL_LATITUDE, INITIAL_LONGITUDE),
    initialZoom: Int = INITIAL_ZOOM,
    onCameraIdle: (KakaoMap) -> Unit = {},
    onReady: (KakaoMap, MapLayers) -> Unit,
) {
    if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "카카오 네이티브 앱 키가 없습니다.\nlocal.properties 의 KAKAO_NATIVE_APP_KEY 를 채워주세요.",
                color = SafeLightTheme.colors.textMuted,
            )
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val mapView = remember { MapView(context) }
    val markers = remember { MapMarkers(context) }

    // MapView 는 액티비티 생명주기를 직접 따라가야 한다(안 하면 백그라운드에서 GL 컨텍스트가 샌다).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            mapView.apply {
                start(
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() {
                            Log.d(TAG, "지도 종료")
                        }

                        override fun onMapError(error: Exception) {
                            // 키 해시 미등록 · 네이티브 앱 키 오류가 여기로 온다.
                            Log.e(TAG, "지도 오류", error)
                        }
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(kakaoMap: KakaoMap) {
                            kakaoMap.setOnCameraMoveEndListener { map, _, _ -> onCameraIdle(map) }
                            onReady(kakaoMap, MapLayers(kakaoMap, markers, density))
                            // 처음 한 번은 직접 부른다 — 사용자가 지도를 건드리기 전에도 보여야 한다.
                            onCameraIdle(kakaoMap)
                        }

                        override fun getPosition(): LatLng = initialPosition

                        override fun getZoomLevel(): Int = initialZoom
                    },
                )
            }
        },
    )
}

/** 지금 화면에 보이는 영역. 웹 `map.getBounds()` 자리다. */
fun KakaoMap.currentBounds(): MapBounds? {
    val viewport = viewport ?: return null
    val southWest = fromScreenPoint(0, viewport.height()) ?: return null
    val northEast = fromScreenPoint(viewport.width(), 0) ?: return null
    return MapBounds(
        minLat = southWest.latitude,
        minLng = southWest.longitude,
        maxLat = northEast.latitude,
        maxLng = northEast.longitude,
    )
}
