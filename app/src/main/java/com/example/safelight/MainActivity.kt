package com.example.safelight

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.safelight.alert.AlertNotifier
import com.example.safelight.data.SettingsStore
import com.example.safelight.ui.nav.SafeLightRoot
import com.example.safelight.ui.theme.SafeLightTheme

class MainActivity : ComponentActivity() {

    // 상태바 알림을 누를 때마다 1씩 오른다. 값이 바뀌는 것 자체가 '알림함을 열어라' 신호다.
    private var openInboxSignal by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 회전 등으로 다시 만들어질 때는 이미 처리한 알림이라 다시 열지 않는다.
        if (savedInstanceState == null) handleAlertIntent(intent)
        enableEdgeToEdge()
        setContent {
            // 야간 모드는 테마보다 위에 있어야 한다 — 헤더 버튼이 테마 자체를 바꾸기 때문이다.
            var night by remember { mutableStateOf(SettingsStore.nightMode) }

            // 상태바·내비게이션바 아이콘 색을 **앱 테마**에 맞춘다.
            // enableEdgeToEdge() 의 기본값은 기기의 다크 모드를 따라가는데, 우리는 기기 설정을
            // 따르지 않으므로(웹처럼 사용자 토글) 폰이 다크 모드면 밝은 화면 위에 흰 아이콘이 얹혀
            // 시계·배터리가 아예 안 보였다. 밝은 배경이면 어두운 아이콘을 쓰게 직접 지정한다.
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !night
                    isAppearanceLightNavigationBars = !night
                }
            }

            SafeLightTheme(darkTheme = night) {
                SafeLightRoot(
                    openInboxSignal = openInboxSignal,
                    night = night,
                    onToggleNight = {
                        night = !night
                        SettingsStore.nightMode = night
                    },
                )
            }
        }
    }

    // 앱이 이미 떠 있을 때 알림을 누르면 새 화면을 만들지 않고 여기로 온다(SINGLE_TOP).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAlertIntent(intent)
    }

    private fun handleAlertIntent(intent: Intent?) {
        if (intent?.getStringExtra(AlertNotifier.EXTRA_OPEN) == AlertNotifier.OPEN_INBOX) openInboxSignal++
    }
}
