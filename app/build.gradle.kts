import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 키는 소스에 넣지 않는다. local.properties 는 .gitignore 대상이라 저장소에 올라가지 않는다.
// 웹에서 index.html 을 gitignore 하고 index.html.example 만 올린 것과 같은 방식이다.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String = localProps.getProperty(key) ?: ""

// 백엔드 주소는 사람마다 다르다(에뮬레이터냐 실기기냐, 팀원 PC 냐). 소스를 고치게 하면
// 매번 커밋에 섞여 들어오므로 local.properties 에서 덮어쓰게 하고 기본값만 여기 둔다.
//   에뮬레이터 : http://10.0.2.2:8080/   (기본값 — 10.0.2.2 는 에뮬레이터가 보는 본 PC 의 localhost)
//   실기기 USB : http://localhost:8080/  + `adb reverse tcp:8080 tcp:8080`
val apiBaseUrl: String = localProps.getProperty("API_BASE_URL") ?: "http://10.0.2.2:8080/"

// 배포용(release) 빌드는 사람마다 다를 일이 없다 — 팀원이 배포한 운영 서버로 고정한다.
// 위의 API_BASE_URL(개발용 localhost 등)이 배포 APK 에 섞여 들어가지 않게 따로 둔다.
val releaseApiBaseUrl = "https://api.safelight26.com/"

// 배포 APK 서명 키. 키스토어 파일과 비밀번호는 공개 저장소에 올리면 안 되므로 local.properties 에서 읽는다.
// 네 값이 다 있을 때만 서명 설정을 만든다(없으면 Android Studio 의 Generate Signed APK 로 직접 서명).
val releaseStoreFile: String? = localProps.getProperty("RELEASE_STORE_FILE")
val hasReleaseSigning = listOf(
    "RELEASE_STORE_FILE", "RELEASE_STORE_PASSWORD", "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD",
).all { !localProps.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.example.safelight"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.safelight"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"${secret("KAKAO_NATIVE_APP_KEY")}\"")
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"${secret("KAKAO_REST_API_KEY")}\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.kakao.map)
    implementation(libs.play.services.location)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
