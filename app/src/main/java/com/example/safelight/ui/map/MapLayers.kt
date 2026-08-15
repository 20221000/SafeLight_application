package com.example.safelight.ui.map

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.safelight.data.net.CctvDto
import com.example.safelight.data.net.DangerZoneDto
import com.example.safelight.data.net.LocationDto
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.label.CompetitionType
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.PolygonOptions
import com.kakao.vectormap.shape.PolygonStyles
import com.kakao.vectormap.shape.ShapeLayer
import com.kakao.vectormap.shape.ShapeLayerOptions
import com.kakao.vectormap.shape.ShapeLayerPass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 원을 그릴 때 쓰는 다각형 변의 수. 이보다 줄이면 가까이서 각이 보인다. */
private const val CIRCLE_SEGMENTS = 64

/**
 * 지도 위 레이어를 만들고 다시 그린다. 웹 MapView.jsx 의 renderCctvInBounds ·
 * renderStoresInBounds · drawDangerZones 에 해당한다.
 *
 * 레이어 순서는 웹 CustomOverlay 의 zIndex 를 그대로 옮겼다 — 위험구역이 가장 아래,
 * 그 위에 CCTV, 편의점, 내 위치가 맨 위다. 다만 **기준점이 0 이 아니다**:
 * 카카오 기본 지도의 라벨 레이어가 zOrder [LabelManager.DEFAULT_Z_ORDER](=10001) 를 쓰기 때문에,
 * 웹처럼 2·3·4 를 주면 우리 마커가 전부 지도 자체의 POI 아이콘 **아래**로 깔린다.
 * 편의점 알약처럼 큰 마커는 가장자리가 삐져나와 보이지만, 시설 점은 POI 아이콘보다 작아
 * 통째로 가려져 아예 안 보였다(2026-08-15 확인). 그래서 기본값 위에서 번호를 매긴다.
 */
private const val BASE_Z_ORDER = 10001

class MapLayers(
    private val map: KakaoMap,
    private val markers: MapMarkers,
    private val density: Float,
) {
    private val labelManager = map.labelManager
    private val shapeManager = map.shapeManager

    // ShapeLayerPass.Overlay — 기본 패스로 두면 원이 아예 보이지 않는다(확인함, 2026-08-15).
    // 라벨 쪽 zOrder 문제(위 주석)와 같은 이유다. Overlay 패스는 순서와 무관하게 지도 위에 얹는다.
    private val dangerShapes: ShapeLayer? = shapeManager?.addLayer(
        ShapeLayerOptions.from("dangerZones", 1, ShapeLayerPass.Overlay),
    )
    // 경로 선은 시설 점보다 아래다 — 선이 점을 덮으면 어디에 CCTV 가 있는지 안 보인다.
    private val routeLines = map.routeLineManager?.addLayer("routes", BASE_Z_ORDER + 1)
    private val dangerBadges = labelLayer("dangerBadges", BASE_Z_ORDER + 2)
    private val cctvLabels = labelLayer("cctv", BASE_Z_ORDER + 3)
    private val storeLabels = labelLayer("stores", BASE_Z_ORDER + 4)
    // 경로 위 안전시설 점은 배경 CCTV 와 겹쳐도 보여야 하므로 그 위에 둔다(웹 zIndex 2 자리).
    private val routeFacilityLabels = labelLayer("routeFacilities", BASE_Z_ORDER + 5)
    private val myLocationLabels = labelLayer("myLocation", BASE_Z_ORDER + 6)
    // 출발·도착과 검색 핀은 사용자가 직접 지정한 것이라 무엇에도 가리지 않게 맨 위에 둔다.
    private val routeEndpointLabels = labelLayer("routeEndpoints", BASE_Z_ORDER + 7)
    private val searchLabels = labelLayer("search", BASE_Z_ORDER + 8)

    // 점 모양은 모든 마커가 같으니 스타일을 한 번만 등록해 돌려 쓴다.
    // 지도를 움직일 때마다 새로 등록하면 SDK 의 스타일 표가 계속 불어난다.
    // 상호·위험도 배지는 글자마다 비트맵이 달라 어쩔 수 없이 매번 등록한다.
    private val cctvDotStyle by lazy { registerStyle(markers.dot(LayerColor.cctv)) }
    private val storeDotStyle by lazy { registerStyle(markers.dot(LayerColor.store)) }
    private val myLocationStyle by lazy { registerStyle(markers.myLocation()) }
    private val routeCctvDotStyle by lazy {
        registerStyle(markers.dot(LayerColor.cctv, ROUTE_DOT_SIZE_DP))
    }
    private val routeStoreDotStyle by lazy {
        registerStyle(markers.dot(LayerColor.store, ROUTE_DOT_SIZE_DP))
    }

    /**
     * 겹쳐도 서로 지우지 않게 한다(CompetitionType.None).
     * 기본값은 겹치는 라벨을 감추는데, 그러면 웹에서 다 보이던 점이 안드로이드에서만 사라진다.
     */
    private fun labelLayer(id: String, zOrder: Int): LabelLayer? =
        labelManager?.addLayer(
            LabelLayerOptions.from(id)
                .setZOrder(zOrder)
                .setCompetitionType(CompetitionType.None),
        )

    private fun registerStyle(bitmap: Bitmap, anchorX: Float = 0.5f, anchorY: Float = 0.5f): LabelStyles? =
        labelManager?.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from(bitmap)
                    // 비트맵을 이미 화면 배율로 그렸으므로 SDK 가 또 키우지 않게 한다.
                    .setApplyDpScale(false)
                    .setAnchorPoint(anchorX, anchorY),
            ),
        )

    private fun LabelLayer.putAll(options: List<LabelOptions>) {
        removeAll()
        if (options.isNotEmpty()) addLabels(options)
    }

    /**
     * [small] 은 경로 안내 화면용이다 — 웹 RoutePage 는 배경 CCTV 를 지도 화면(11px)보다
     * 작은 9px 로 그린다. 경로 위 안전시설 점보다 크면 그쪽으로 가야 할 시선을 뺏기 때문이다.
     */
    fun drawCctv(items: List<CctvDto>, small: Boolean = false) {
        val style = (if (small) routeCctvDotStyle else cctvDotStyle) ?: return
        cctvLabels?.putAll(
            items.map { LabelOptions.from(LatLng.from(it.latitude, it.longitude)).setStyles(style) },
        )
    }

    /** [showNames] 면 상호가 붙은 알약을, 아니면 시설 점을 그린다(웹과 같은 규칙). */
    fun drawStores(items: List<StorePlace>, showNames: Boolean) {
        val layer = storeLabels ?: return
        if (!showNames) {
            val style = storeDotStyle ?: return
            layer.putAll(
                items.map { LabelOptions.from(LatLng.from(it.latitude, it.longitude)).setStyles(style) },
            )
            return
        }
        layer.putAll(
            items.mapNotNull { store ->
                val style = registerStyle(markers.storePill(store.name)) ?: return@mapNotNull null
                LabelOptions.from(LatLng.from(store.latitude, store.longitude)).setStyles(style)
            },
        )
    }

    /**
     * 위험구역 — 반투명 원 + 위험도 배지.
     * 웹: strokeWeight 2 · strokeOpacity .9 · fillOpacity .15
     */
    fun drawDangerZones(zones: List<DangerZoneDto>) {
        dangerShapes?.removeAll()
        dangerBadges?.removeAll()
        if (zones.isEmpty()) return

        zones.forEach { zone ->
            val color = dangerLevelColor(zone.dangerLevel)
            dangerShapes?.addPolygon(
                PolygonOptions.from(
                    circlePoints(zone.centerLatitude, zone.centerLongitude, zone.radius),
                    PolygonStyles.from(
                        color.copy(alpha = 0.15f).toArgb(),
                        2f * density,
                        color.copy(alpha = 0.9f).toArgb(),
                    ),
                ),
            )
        }

        dangerBadges?.putAll(
            zones.mapNotNull { zone ->
                val style = registerStyle(
                    markers.dangerBadge(zone.dangerLevel),
                    anchorY = 1f,   // 비트맵 아래쪽 투명 여백까지 포함한 맨 밑을 좌표에 붙인다
                ) ?: return@mapNotNull null
                LabelOptions
                    .from(LatLng.from(zone.centerLatitude, zone.centerLongitude))
                    .setStyles(style)
            },
        )
    }

    /**
     * 반경 [radiusMeters] 짜리 원을 위경도 다각형으로 만든다.
     *
     * 카카오가 주는 `DotPoints.fromCircle` 은 쓸 수 없다 — 그쪽 반지름은 화면 픽셀이라
     * 확대해도 원이 그대로여서, 미터로 정의된 위험구역을 나타내지 못한다(확인함, 2026-08-15).
     * 웹의 `kakao.maps.Circle({ radius })` 은 미터라서 확대하면 같이 커진다 — 그 동작을 맞춘다.
     */
    private fun circlePoints(lat: Double, lng: Double, radiusMeters: Double): MapPoints {
        val latDelta = radiusMeters / 111_320.0
        val lngDelta = radiusMeters / (111_320.0 * cos(Math.toRadians(lat)))
        // 0..CIRCLE_SEGMENTS 이므로 마지막 점이 첫 점과 같다. 채움은 카카오가 알아서 닫아 주지만
        // 테두리 선은 닫아 주지 않아, 여기서 한 바퀴를 마무리하지 않으면 원 한 곳이 끊겨 보인다.
        val points = (0..CIRCLE_SEGMENTS).map { i ->
            val angle = 2.0 * PI * i / CIRCLE_SEGMENTS
            LatLng.from(lat + latDelta * cos(angle), lng + lngDelta * sin(angle))
        }
        return MapPoints.fromLatLng(points)
    }

    /**
     * 경로 선 + 출발·도착 마커 + 경로 주변 안전시설 점을 한 번에 그린다.
     * 인자가 모두 비면 그려 둔 것이 지워진다(안내 취소).
     *
     * [path] 없이 [start]·[destination] 만 넘길 수 있다 — 경로를 아직 못 찾은 상태에서도
     * 어디를 고른 것인지 지도에 보여야 하기 때문이다(웹도 선택 즉시 마커부터 찍는다).
     *
     * 색은 화면마다 다르다 — 경로 화면은 파란 선, 지도 화면의 '안내 중' 경로는 초록 선이다.
     * 웹이 그렇게 나눠 놓았고([RouteColor]), 두 화면을 한눈에 구분하는 단서라 그대로 옮겼다.
     */
    fun drawRoute(
        path: List<LocationDto>,
        start: LatLng? = null,
        destination: LatLng? = null,
        lineColor: Color = RouteColor.start,
        startColor: Color = RouteColor.start,
        startTextColor: Color = Color.White,
        destinationColor: Color = RouteColor.destination,
        cctvLocations: List<LocationDto> = emptyList(),
        storeLocations: List<LocationDto> = emptyList(),
    ) {
        routeLines?.removeAll()
        routeEndpointLabels?.removeAll()
        routeFacilityLabels?.removeAll()

        if (path.isNotEmpty()) {
            routeLines?.addRouteLine(
                RouteLineOptions.from(
                    RouteLineSegment.from(
                        path.map { LatLng.from(it.latitude, it.longitude) },
                        RouteLineStyle.from(
                            ROUTE_LINE_WIDTH_DP * density,
                            lineColor.copy(alpha = 0.9f).toArgb(),
                        ),
                    ),
                ),
            )
        }

        // 출발·도착은 웹과 같이 원 아래 끝이 좌표에 붙는다(yAnchor:1).
        val endpoints = buildList {
            start?.let { add(it to markers.endpoint("출발", startColor, startTextColor)) }
            destination?.let { add(it to markers.endpoint("도착", destinationColor, Color.White)) }
        }
        routeEndpointLabels?.putAll(
            endpoints.mapNotNull { (position, bitmap) ->
                val style = registerStyle(bitmap, anchorY = 1f) ?: return@mapNotNull null
                LabelOptions.from(position).setStyles(style)
            },
        )

        val facilities = buildList {
            routeCctvDotStyle?.let { style -> cctvLocations.forEach { add(it to style) } }
            routeStoreDotStyle?.let { style -> storeLocations.forEach { add(it to style) } }
        }
        routeFacilityLabels?.putAll(
            facilities.map { (location, style) ->
                LabelOptions.from(LatLng.from(location.latitude, location.longitude)).setStyles(style)
            },
        )
    }

    /** 헤더 검색에서 고른 곳. 꼬리 끝이 좌표에 붙도록 비트맵 맨 아래를 앵커로 삼는다(웹 yAnchor:1). */
    fun drawSearchPin(position: LatLng?, name: String) {
        val layer = searchLabels ?: return
        if (position == null) {
            layer.removeAll()
            return
        }
        val style = registerStyle(markers.searchPin(name), anchorY = 1f) ?: return
        layer.putAll(listOf(LabelOptions.from(position).setStyles(style)))
    }

    fun drawMyLocation(position: LatLng?) {
        val layer = myLocationLabels ?: return
        val style = myLocationStyle ?: return
        layer.putAll(
            if (position == null) emptyList()
            else listOf(LabelOptions.from(position).setStyles(style)),
        )
    }
}
