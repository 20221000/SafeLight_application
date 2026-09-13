package com.example.safelight.ui.admin

/**
 * 관리자 탭 하나가 [AdminRoot] 의 데이터 변경 번호를 어디까지 따라왔는지 적어 둔다.
 *
 * 탭 화면의 ViewModel 은 탭을 옮겨도 살아 있다. 그래서 '처음 한 번만 읽기'로 두면
 * 다른 탭에서 처리한 결과를 영영 못 본다 — 신고 탭에서 허위신고를 취소해도
 * 대시보드·회원 탭의 허위신고 횟수는 옛 값이었고, 위험구역 탭은 30초 주기에야 맞춰졌다.
 */
class AdminRevision {

    private var seen: Int? = null

    /** 이 탭이 낸 변경 중 어디까지 알렸는지. 탭에 다시 들어올 때마다 같은 변경을 또 알리지 않게 한다. */
    private var reported = 0

    /** 처음 여는 탭이면 [first], 그사이 번호가 올랐으면 [changed] 를 부른다. 같으면 아무것도 안 한다. */
    fun follow(revision: Int, first: () -> Unit, changed: () -> Unit) {
        val previous = seen
        if (previous == revision) return
        seen = revision
        if (previous == null) first() else changed()
    }

    /**
     * 이 탭이 [localChanges] 번째 변경까지 냈다면 아직 안 알린 만큼을 [notify] 로 알린다.
     * 변경을 낸 탭은 이미 화면에 반영했으니, 그 변경으로 오른 번호에서는 다시 읽지 않는다.
     */
    fun report(localChanges: Int, notify: () -> Int) {
        if (localChanges == reported) return
        reported = localChanges
        val raised = notify()
        if (seen != null) seen = raised
    }
}
