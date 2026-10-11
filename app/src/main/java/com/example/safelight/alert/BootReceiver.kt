package com.example.safelight.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 휴대폰을 껐다 켜거나 앱이 업데이트되면 서비스도 내려간다.
 * 로그인해 둔 상태라면 앱을 다시 열지 않아도 알림을 계속 받도록 여기서 다시 띄운다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> AlertWatchService.start(context)
        }
    }
}
