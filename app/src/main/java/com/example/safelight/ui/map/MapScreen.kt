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

private const val TAG = "MapScreen"

/**
 * 웹 MapView.jsx 의 자리. 지금은 지도를 띄우는 데까지만 한다.
 * 다음 단계: 위험구역 원(ShapeLayer) · CCTV/편의점 라벨(LabelLayer) · 경로선(RouteLineLayer).
 */
@Composable
fun MapScreen(camera: MapCameraState, modifier: Modifier = Modifier) {
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
    val mapView = remember { MapView(context) }
    // 화면을 떠날 때 카메라를 읽어 저장해야 하므로 준비된 지도를 붙들어 둔다.
    val mapHolder = remember { arrayOfNulls<KakaoMap>(1) }

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

    // 탭을 떠나면 여기서 마지막 위치를 받아 적어둔다. 다시 들어오면 그 자리에서 시작한다.
    DisposableEffect(Unit) {
        onDispose {
            mapHolder[0]?.cameraPosition?.let { pos ->
                camera.latitude = pos.position.latitude
                camera.longitude = pos.position.longitude
                camera.zoom = pos.zoomLevel
            }
        }
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
                            mapHolder[0] = kakaoMap
                            // 이 시점의 kakaoMap.zoomLevel 은 아직 getZoomLevel() 이 반영되기 전 값이라 읽어도 의미가 없다.
                            Log.d(TAG, "지도 준비 완료 (zoom=${camera.zoom})")
                        }

                        override fun getPosition(): LatLng =
                            LatLng.from(camera.latitude, camera.longitude)

                        override fun getZoomLevel(): Int = camera.zoom
                    },
                )
            }
        },
    )
}
