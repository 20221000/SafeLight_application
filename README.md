# Safe Light — Android

실시간 안전 지도 서비스 **Safe Light** 의 안드로이드 앱.
웹 프론트엔드([light-safe](https://github.com/20221000/light-safe))에서 만든 화면을 네이티브로 옮긴다.
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

- **네이티브 앱 키** — 지도 렌더링(Kakao Maps SDK)
- **REST API 키** — 장소검색 · 역지오코딩(Kakao Local REST)

안드로이드 지도 SDK 에는 웹의 `kakao.maps.services` 에 해당하는 기능이 없어서 키가 두 개 필요하다.

> 키가 비어 있어도 앱은 실행된다. 지도 자리에 안내 문구가 뜬다.

### 3. 백엔드 연결

디버그 빌드의 기본값은 `http://10.0.2.2:8080/` — 에뮬레이터에서 본 PC 의 localhost 다.

실기기를 USB 로 붙여 쓸 때는 포트를 넘겨주고 `app/build.gradle.kts` 의 `API_BASE_URL` 을 `http://localhost:8080/` 으로 바꾼다.

```bash
adb reverse tcp:8080 tcp:8080
```

## 웹과 다른 점

- **확대 레벨 방향이 반대다.** 웹 JS SDK 는 1이 최대 확대지만, 안드로이드 v2 는 숫자가 클수록 확대다. 웹의 레벨 상수를 그대로 옮기면 안 된다.
- **HTML 오버레이를 못 쓴다.** 웹의 `CustomOverlay` 는 `LabelLayer`, 위험구역 원은 `ShapeLayer`, 경로선은 `RouteLineLayer` 로 다시 그려야 한다.
- **경로 계산은 백엔드가 한다.** 카카오 길찾기 API 는 쓰지 않는다.
