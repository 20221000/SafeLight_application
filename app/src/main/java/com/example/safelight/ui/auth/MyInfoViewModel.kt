package com.example.safelight.ui.auth

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.SettingsStore
import com.example.safelight.data.net.EmergencyReportDto
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.PostListDto
import com.example.safelight.data.net.ProfileUpdateRequest
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.UserProfileDto
import com.example.safelight.data.net.errorMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import retrofit2.Response

private const val TAG = "MyInfo"

/** 백엔드 UserService.updateProfile 의 정규식과 같게 맞춘다. 어긋나면 서버가 400/409 로 되돌린다. */
private val EMAIL_RE = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val PHONE_RE = Regex("^010-\\d{4}-\\d{4}$")

/** 숫자만 눌러도 010-1234-5678 로 맞춰 준다. 백엔드 형식이 엄격하다. */
fun formatPhone(value: String): String {
    val d = value.filter { it.isDigit() }.take(11)
    return when {
        d.length <= 3 -> d
        d.length <= 7 -> "${d.take(3)}-${d.drop(3)}"
        else -> "${d.take(3)}-${d.substring(3, 7)}-${d.drop(7)}"
    }
}

/**
 * '내 정보' 탭의 서버 상태. 웹 MyInfoPage.jsx 가 마운트 때 한 번에 부르는 다섯 요청을 그대로 옮겼다.
 *
 * 로그인·로그아웃은 [AuthViewModel] 이 계속 맡는다 — 여기는 로그인한 뒤의 내용만 다룬다.
 */
class MyInfoViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    /** 지금 화면이 담고 있는 사용자. 계정이 바뀌면 다시 읽는다. */
    private var loadedUserId: Long? = null

    var profile by mutableStateOf<UserProfileDto?>(null)
        private set
    var myPosts by mutableStateOf<List<PostListDto>>(emptyList())
        private set
    var myReports by mutableStateOf<List<EmergencyReportDto>>(emptyList())
        private set
    var unreadMessages by mutableStateOf(0)
        private set
    var unreadNotifications by mutableStateOf(0)
        private set

    var loading by mutableStateOf(false)
        private set

    // 계정 설정 입력값. 프로필을 받으면 현재 값으로 한 번 채우고, 저장할 때 바뀐 것만 보낸다.
    var nickname by mutableStateOf("")
    var email by mutableStateOf("")
    var phone by mutableStateOf("")
        private set

    /** 기본은 읽기 전용. '프로필 수정'을 눌러야 입력칸이 열린다(웹과 같다). */
    var editingProfile by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set

    var currentPassword by mutableStateOf("")
    var newPassword by mutableStateOf("")

    var alarmSound by mutableStateOf(SettingsStore.alarmSound)
        private set

    /** 스낵바로 한 번 보여주고 지울 문구. */
    var message by mutableStateOf<String?>(null)
        private set

    /** 탈퇴가 끝나 로그아웃해야 할 때 true 가 된다. */
    var withdrawn by mutableStateOf(false)
        private set

    fun messageShown() {
        message = null
    }

    fun onPhoneChange(value: String) {
        phone = formatPhone(value)
    }

    fun toggleAlarmSound() {
        alarmSound = !alarmSound
        SettingsStore.alarmSound = alarmSound
    }

    /** 화면에 들어올 때마다 부른다. 같은 사용자면 다시 읽지 않는다. */
    fun start(userId: Long) {
        if (loadedUserId == userId) return
        loadedUserId = userId
        load(userId)
    }

    /** 계정이 바뀌었을 때(로그아웃·탈퇴) 남은 내용을 비운다. */
    fun clear() {
        loadedUserId = null
        profile = null
        myPosts = emptyList()
        myReports = emptyList()
        unreadMessages = 0
        unreadNotifications = 0
        nickname = ""
        email = ""
        phone = ""
        editingProfile = false
        currentPassword = ""
        newPassword = ""
        withdrawn = false
    }

    fun refresh() {
        loadedUserId?.let { load(it) }
    }

    /**
     * 다섯 곳을 한꺼번에 부른다. 하나가 실패해도 나머지는 그대로 보여준다 —
     * 쪽지 개수를 못 받았다고 내가 쓴 글까지 감출 이유가 없다.
     */
    private fun load(userId: Long) {
        loading = true
        viewModelScope.launch {
            val profileCall = async { runCatching { api.getProfile(userId) }.getOrNull() }
            val postsCall = async { runCatching { api.getMyPosts() }.getOrNull() }
            val reportsCall = async { runCatching { api.getMyReports() }.getOrNull() }
            val messagesCall = async { runCatching { api.getUnreadMessageCount() }.getOrNull() }
            val notificationsCall = async { runCatching { api.getUnreadNotificationCount() }.getOrNull() }

            profileCall.await()?.dataOrNull()?.let {
                profile = it
                // 수정 중이라면 사용자가 치던 값을 서버 값으로 덮지 않는다.
                if (!editingProfile) fillInputs(it)
            }
            myPosts = postsCall.await()?.dataOrNull().orEmpty()
            myReports = reportsCall.await()?.dataOrNull().orEmpty()
            unreadMessages = messagesCall.await()?.dataOrNull()?.unreadCount ?: 0
            unreadNotifications = notificationsCall.await()?.dataOrNull()?.unreadCount ?: 0
            loading = false
        }
    }

    private fun fillInputs(p: UserProfileDto) {
        nickname = p.nickname
        email = p.email.orEmpty()
        phone = p.phone.orEmpty()
    }

    fun startProfileEdit() {
        editingProfile = true
    }

    /** 수정을 접고 서버에서 받은 값으로 되돌린다. */
    fun cancelProfileEdit() {
        profile?.let { fillInputs(it) }
        editingProfile = false
    }

    fun saveProfile() {
        val userId = loadedUserId ?: return
        val nn = nickname.trim()
        val em = email.trim().lowercase()
        val ph = phone.trim()

        val invalid = when {
            nn.isEmpty() -> "닉네임은 비워둘 수 없습니다."
            nn.length > 50 -> "닉네임은 50자 이하여야 합니다."
            em.isNotEmpty() && !EMAIL_RE.matches(em) -> "올바른 이메일 형식이 아닙니다."
            em.length > 100 -> "이메일은 100자 이하여야 합니다."
            ph.isNotEmpty() && !PHONE_RE.matches(ph) -> "전화번호는 010-1234-5678 형식으로 입력해주세요."
            else -> null
        }
        if (invalid != null) {
            message = invalid
            return
        }

        // 바뀐 것만 보낸다. 안 바뀐 이메일까지 보내면 중복검사에 걸릴 여지가 있다.
        val current = profile
        val request = ProfileUpdateRequest(
            nickname = nn.takeIf { it != current?.nickname },
            email = em.takeIf { it.isNotEmpty() && it != current?.email },
            phone = ph.takeIf { it.isNotEmpty() && it != current?.phone },
        )
        if (request.nickname == null && request.email == null && request.phone == null) {
            editingProfile = false
            return
        }

        saving = true
        viewModelScope.launch {
            val response = runCatching { api.updateProfile(userId, request) }.getOrNull()
            val saved = response?.dataOrNull()
            if (saved != null) {
                profile = current?.copy(
                    nickname = saved.nickname,
                    email = saved.email,
                    phone = saved.phone,
                )
                nickname = saved.nickname
                email = saved.email.orEmpty()
                phone = saved.phone.orEmpty()
                editingProfile = false
                message = "프로필이 저장되었습니다."
            } else {
                // 이메일 중복(409)·형식 오류(400) 문구가 서버에서 그대로 올라온다.
                message = response?.errorMessage("프로필 저장에 실패했습니다.") ?: "서버에 연결하지 못했습니다."
            }
            saving = false
        }
    }

    fun changePassword() {
        val userId = loadedUserId ?: return
        if (currentPassword.isBlank() || newPassword.isBlank()) {
            message = "비밀번호를 입력해주세요."
            return
        }
        // 회원가입(@Size(min=8))과 규칙을 맞춘다. 변경 API 에는 검증이 없어 여기서 막는다.
        if (newPassword.length < 8) {
            message = "비밀번호는 8자 이상이어야 합니다."
            return
        }
        saving = true
        viewModelScope.launch {
            val response = runCatching {
                api.updateProfile(userId, ProfileUpdateRequest(password = newPassword))
            }.getOrNull()
            if (response?.dataOrNull() != null) {
                currentPassword = ""
                newPassword = ""
                message = "비밀번호가 변경되었습니다."
            } else {
                message = response?.errorMessage("비밀번호 변경에 실패했습니다.") ?: "서버에 연결하지 못했습니다."
            }
            saving = false
        }
    }

    fun withdraw() {
        val userId = loadedUserId ?: return
        viewModelScope.launch {
            val response = runCatching { api.withdraw(userId) }.getOrNull()
            if (response?.isSuccessful == true && response.body()?.success == true) {
                withdrawn = true
                return@launch
            }
            Log.w(TAG, "회원 탈퇴 실패: ${response?.code()}")
            message = response?.errorMessage("회원 탈퇴에 실패했습니다.") ?: "서버에 연결하지 못했습니다."
        }
    }

    /** 성공 봉투에서 알맹이만 꺼낸다. 실패했으면 null 이라 부르는 쪽이 기본값으로 넘어간다. */
    private fun <T> Response<com.example.safelight.data.net.ApiEnvelope<T>>.dataOrNull(): T? =
        if (isSuccessful && body()?.success == true) body()?.data else null
}
