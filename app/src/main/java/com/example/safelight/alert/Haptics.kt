package com.example.safelight.alert

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 휴대폰 진동. 패턴은 웹(src/utils/vibrate.js)과 같은 값이다 — [울림, 쉼, 울림, …] ms.
 *
 * 친구 긴급신고·쪽지는 상태바 알림 채널([AlertNotifier])이 같은 패턴으로 울린다.
 * 여기서 직접 울리는 건 알림을 띄우지 않는 순간(내 신고 접수)뿐이다.
 */
object Haptics {

    val SOS_ACCEPTED = longArrayOf(200, 100, 200)
    val FRIEND_EMERGENCY = longArrayOf(500, 200, 500, 200, 500)
    val MESSAGE = longArrayOf(150)

    fun vibrate(context: Context, pattern: LongArray) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        val timings = timings(pattern)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    /**
     * 안드로이드는 웹과 달리 첫 칸을 '울리기 전 대기'로 읽는다(Vibrator·알림 채널 둘 다).
     * 0 을 앞에 붙여 웹과 같은 [울림, 쉼, …] 순서로 맞춘다.
     */
    fun timings(pattern: LongArray): LongArray = longArrayOf(0) + pattern

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
