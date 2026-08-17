# Safe Light — Android

실시간 안전 지도 서비스 **Safe Light** 의 안드로이드 앱.
웹 프론트엔드([SafeLight_frontend](https://github.com/20221000/SafeLight_frontend))에서 만든 화면을 네이티브로 옮긴다.
백엔드는 별도 저장소의 Spring 서버를 그대로 쓴다.

## 기술 구성

| | |
|---|---|
| UI | Jetpack Compose + Navigation Compose |
| 지도 | Kakao Maps SDK v2 (`com.kakao.vectormap`) |
| 통신 | Retrofit + OkHttp + kotlinx.serialization |
| 이미지 | Coil |
| 최소 SDK | 24 |

## 실행 전 준비

### 1. 카카오 개발자 콘솔에 Android 플랫폼 등록

[Kakao Developers](https://developers.kakao.com) > 내 애플리케이션 > 앱 설정 > 플랫폼 > Android 에서:

- **패키지명**: `com.example.safelight`
- **키 해시**: 아래 명령으로 각자 뽑아서 등록한다 (개발 PC 마다 다르다)

```bash
keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android -keypass android | openssl sha1 -binary | openssl base64
```

`keytool` 이 PATH 에 없으면 안드로이드 스튜디오에 딸린 것을 쓴다:
`C:/Program Files/Android/Android Studio/jbr/bin/keytool.exe`

### 2. 키를 `local.properties` 에 넣기

`local.properties` 는 `.gitignore` 대상이라 저장소에 없다. 각자 아래 두 줄을 추가한다.

```properties
KAKAO_NATIVE_APP_KEY=(네이티브 앱 키)
KAKAO_REST_API_KEY=(REST API 키)
```

백엔드 주소도 여기서 덮어쓴다(생략하면 에뮬레이터용 `http://10.0.2.2:8080/`).

```properties
API_BASE_URL=http://localhost:8080/
```

- **네이티브 앱 키** — 지도 렌더링(Kakao Maps SDK)
- **REST API 키** — 장소검색 · 역지오코딩(Kakao Local REST)

안드로이드 지도 SDK 에는 웹의 `kakao.maps.services` 에 해당하는 기능이 없어서 키가 두 개 필요하다.

> 키가 비어 있어도 앱은 실행된다. 지도 자리에 안내 문구가 뜬다.

### 3. 백엔드 연결

디버그 빌드의 기본값은 `http://10.0.2.2:8080/` — 에뮬레이터에서 본 PC 의 localhost 다.

실기기를 USB 로 붙여 쓸 때는 위 `API_BASE_URL` 을 `http://localhost:8080/` 으로 두고 포트를 넘겨준다.

```bash
adb reverse tcp:8080 tcp:8080
```

## 웹과 다른 점

지도를 옮기면서 하나씩 부딪힌 것들이다. 값을 고칠 땐 웹의 대응 지점도 같이 고쳐야 두 화면이 갈리지 않는다.

- **확대 레벨은 방향도 반대고 눈금도 안 맞는다.** 웹 JS SDK 는 1이 최대 확대, 안드로이드 v2 는 숫자가 클수록 확대다.
  게다가 둘 다 한 단계가 2배지만 서로 겹치지 않는다 — 웹 L4(2.0 m/px)는 안드로이드 z15(3.06)와 z16(1.53) 사이에 떨어진다.
  실측해서 가까운 쪽인 `zoom = 20 - webLevel` 로 맞췄다(`MapLayerStyle.kt` 에 측정값과 근거가 있다).
- **HTML 오버레이를 못 쓴다.** 웹의 `CustomOverlay` 는 전부 `LabelStyle.from(Bitmap)` 으로 다시 그린다(`MapMarkers.kt`).
  CSS 의 padding·radius·border·shadow 를 Canvas 로 옮긴 것이라 숫자가 웹 스타일과 1:1 로 대응한다.
- **레이어 zOrder 는 0 부터 세면 안 된다.** 카카오 기본 지도의 라벨 레이어가 `LabelManager.DEFAULT_Z_ORDER`(=10001)를 쓴다.
  웹의 zIndex(2·3·4)를 그대로 옮기면 우리 마커가 전부 지도 POI 아이콘 **아래**로 깔린다.
  큰 마커(편의점 상호 알약)는 가장자리가 삐져나와 보이지만, 작은 시설 점은 통째로 가려져 아예 안 보인다.
  같은 이유로 `ShapeLayer` 도 `ShapeLayerPass.Overlay` 로 만들어야 위험구역 원이 보인다.
- **원은 `DotPoints.fromCircle` 로 그리면 안 된다.** 그쪽 반지름은 화면 픽셀이라 확대해도 원 크기가 그대로다.
  미터 단위인 위험구역은 `MapPoints.fromLatLng` 로 다각형을 직접 만들고, **첫 점을 끝에 한 번 더 넣어 닫는다**
  (채움은 알아서 닫히지만 테두리 선은 안 닫혀 한 곳이 끊긴다).
- **라벨 레이어는 `CompetitionType.None`** 으로 둔다. 기본값은 겹치는 라벨을 감춰서, 웹에서 다 보이던 점이 안드로이드에서만 사라진다.
- **다크 모드는 기기 설정을 따라가지 않는다.** 웹의 야간 모드는 시스템 설정이 아니라 사용자가 직접 켜는 토글이고 기본값은 밝은 화면이다.
- **카카오 Local REST 로 편의점을 찾는다.** 안드로이드 지도 SDK 에는 웹의 `kakao.maps.services` 대응물이 없다.
- **경로 계산은 백엔드가 한다.** 카카오 길찾기 API 는 쓰지 않는다.
  경로 선은 `RouteLineManager` 로 그린다(`Polyline` 대응물). 이 레이어도 zOrder 를 10001 위에서 매긴다.
- **바텀시트는 손가락 드래그만 본다.** `NestedScrollConnection` 에서 `source == UserInput` 을 확인하지 않으면
  화면에 처음 들어올 때 시트가 혼자 끝까지 올라간다 — 입력칸에 포커스가 잡히면서 Compose 가 '보이게 하려고'
  흘리는 스크롤까지 드래그로 세기 때문이다(`DragSheet.kt`).
- **시트 높이는 상태에 바로 쓰고, 스냅만 애니메이션한다.** 드래그 값을 `Animatable` 에 코루틴으로 실어
  보내면 한 프레임씩 밀려서, 빠르게 쓸어 올릴 때 여러 델타가 같은 옛 높이를 기준으로 계산돼
  시트가 거의 안 움직인다(확인함, 2026-08-17). 놓았을 때만 `animate` 로 미끄러뜨린다 —
  웹 `transition: height .24s ease` 자리다.
- **한 번의 드래그가 시트인지 스크롤인지는 시작할 때 정한다.** 매 이벤트마다 다시 판단하면
  끌어올리는 도중 시트가 full 이 되는 순간부터 같은 손짓이 본문 스크롤로 넘어가 내용이 홱 지나간다.
  판정 규칙은 웹 `useDragSheet` 의 본문 터치 핸들러와 같고, 제목 블록은 웹의 `data-sheet-head` 처럼
  언제나 시트를 움직인다(거기까지 스크롤이면 다 올린 시트를 내릴 방법이 없다).
- **스크롤되는 화면 안의 지도는 바깥의 가로채기를 막아야 한다.** 알림 상세의 신고 위치처럼 지도를
  스크롤 안에 넣으면 세로 드래그를 바깥이 먼저 가져가 지도가 따라오지 않는다. 지도를 감싼 뷰가
  `ACTION_DOWN` 에서 `requestDisallowInterceptTouchEvent(true)` 를 부르면 Compose 의 `AndroidView`
  홀더가 이를 받아 자기 제스처를 양보한다(`KakaoMapHost.kt`). 웹은 지도 div 가 알아서 삼켜 이 코드가 없다.
- **시트가 가리는 만큼은 `KakaoMap.setPadding` 으로 알린다.** 웹은 `setCenter` 뒤에 `panBy` 로 밀지만,
  안드로이드는 지도에 여백을 알려 두면 카메라 이동·`fitMapPoints` 가 알아서 보정된다.
- **지도 준비 여부는 상태로 들고 있어야 한다.** `arrayOfNulls` 홀더에 지도를 넣는 것만으로는 아무도 다시 그리지 않는다.
  `mapReady` 같은 상태를 `LaunchedEffect` 키에 넣지 않으면, 화면에 들어올 때 이미 정해져 있던 것(안내 중인 경로 등)이
  지도가 준비되기 전에 한 번 시도되고 끝나 영영 안 그려진다.
- **실패 문구는 `error.message` 에 있다.** 백엔드 실패 봉투는 최상위 `message` 가 null 이고 진짜 문구는
  `error.message` 에 담겨 온다. 그쪽을 먼저 보지 않으면 모든 실패가 호출한 쪽의 뭉뚱그린 문구로 덮인다
  (`ApiEnvelope.kt` 의 `errorMessage`, 웹 `apiResponse.js` 의 `readEnvelope` 와 같은 순서다).
- **첨부파일은 '다운로드' 폴더에 저장한다.** 웹의 브라우저 내려받기와 같은 자리다.
  Android 10(Q)부터는 MediaStore 로 넣어 권한이 필요 없고, 그 아래에서만 `WRITE_EXTERNAL_STORAGE` 를 받아
  공용 폴더에 직접 쓴다(그때는 다른 앱에 넘길 주소가 필요해 `FileProvider` 를 거친다).
  올리는 쪽은 시스템 문서 선택기(`OpenMultipleDocuments`)이며, 웹의 드래그&드롭 자리에 해당한다.
  허용 목록(jpg·jpeg·png·gif·webp·pdf·txt / 10MB)은 백엔드 `FileStorageService` 를 그대로 옮겼다 —
  서버가 확장자와 MIME 을 **둘 다** 보므로 한쪽만 맞아도 400 이다.
- **multipart 의 글자 조각에는 charset 을 붙인다.** 제목·내용·카테고리가 JSON 이 아니라 조각으로 가는데
  (`@RequestParam`), `text/plain; charset=utf-8` 을 명시하지 않으면 한글이 깨진 채로 저장될 수 있다.
- **SOS 는 캐시된 위치를 쓰지 않는다.** 다른 화면은 `lastLocation`(마지막으로 알던 위치)으로 충분하지만,
  긴급 신고에 몇 시간 전 다른 동네의 좌표가 실리면 안 된다. `getCurrentLocation(PRIORITY_HIGH_ACCURACY)`
  로 그 자리에서 새로 받는다 — 웹의 `enableHighAccuracy: true, maximumAge: 0` 자리다.
  완료 배너에서 '사이렌 켜짐' 한 줄은 뺐다. 양쪽 다 소리를 내지 않는데 켜졌다고 적으면
  소리가 안 나는 것을 고장으로 읽는다.
- **긴급 알람 소리 설정은 저장한다.** 웹은 이 값을 화면 상태로만 들고 있어 새로고침하면 되돌아가지만,
  앱에서는 [SettingsStore] 에 남긴다 — 화면을 나갔다 오면 잊어버리는 스위치는 '꺼 뒀다'고 믿게 만들어
  위험한 쪽으로 어긋난다. (사이렌 재생 자체는 웹과 마찬가지로 아직 없다.)
- **알림 벨의 안 읽음 수는 화면을 보고 있는 동안에만 다시 센다.** 웹은 `focus`·`visibilitychange` 로
  하던 일을 여기서는 `repeatOnLifecycle(RESUMED)` 로 한다(간격은 웹과 같은 30초).
  긴급과 쪽지를 합치지 않고 따로 세는 것도 웹과 같다 — 벨이 둘을 다르게 그린다(긴급 빨강, 쪽지 파랑).
- **관리자 콘솔은 NavHost 목적지가 아니다.** 웹도 `AdminShell` 로 셸 자체가 갈리므로, 사용자 화면의
  헤더·탭바 안에 끼우지 않고 화면을 덮는 상태(`adminOpen`)로 둔다. 그래서 뒤로가기는 탭 사이를
  오가는 게 아니라 콘솔을 닫는다(`BackHandler`). 탭 다섯 개는 데스크탑 웹의 220px 사이드바 자리다.
- **좌표 → 주소 캐시에서 기다리는 것은 잠금 밖에서 한다.** 같은 좌표를 여러 줄이 동시에 물어보면
  요청 하나로 합치는데(웹 `geocode.js` 의 `inflight`), 잠금을 쥔 채 `await` 하면 먼저 물어본 쪽이
  답을 적어 넣으려고 같은 잠금을 기다리는 사이 둘 다 멈춘다 — 한 화면에 같은 좌표의 신고가
  여러 건이면 바로 걸려서, 주소가 영영 안 뜬다(`LocationText.kt`).
- **지도 위의 원은 눌러지지 않는다.** 웹은 `maps.event.addListener(circle, 'click')` 으로 위험구역을
  고르지만, 카카오 v2 의 폴리곤에는 클릭 이벤트가 없다(`shape` 패키지에 리스너 자체가 없다).
  대신 `setOnMapClickListener` 로 받은 좌표에서 반경 안에 든 구역을 직접 찾는다 —
  겹쳐 있으면 중심이 가까운 쪽을 고른다. 큰 구역 안에 작은 구역이 들어 있을 때
  큰 쪽만 잡히면 작은 쪽은 영영 못 고른다(`AdminZoneScreen.kt` 의 `zoneAt`).
- **기간 필터는 안드로이드 기본 달력을 띄운다.** 웹 `<input type="date">` 자리다.
  값은 `startDate=2026-08-17T00:00:00` 형태로 넘긴다 — 백엔드가 `ISO_DATE_TIME` 을 요구해서
  날짜만 보내면 400 이고, 종료일은 그 날 하루를 통째로 담으려고 `23:59:59` 로 맞춘다.
