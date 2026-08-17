package com.example.safelight.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.ProfileUpdateRequest
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.UserProfileDto
import com.example.safelight.data.net.UserStatusRequest
import com.example.safelight.data.net.errorMessage
import com.example.safelight.ui.auth.formatPhone
import kotlinx.coroutines.launch

/** 모바일 칩. 위험한 회원을 먼저 찾는 자리다. */
enum class UserFilter(val label: String) {
    All("전체"),
    FalseThree("허위 3회↑"),
    Blacklisted("블랙리스트"),
}

/** 사용자 관리. 검색은 목록이 크지 않아 웹처럼 화면에서 거른다(서버에 검색 파라미터가 없다). */
class AdminUserViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    var users by mutableStateOf<List<UserProfileDto>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var search by mutableStateOf("")
    var filter by mutableStateOf(UserFilter.All)

    /** 수정 다이얼로그 대상과 입력값. */
    var editing by mutableStateOf<UserProfileDto?>(null)
        private set
    var editNickname by mutableStateOf("")
    var editEmail by mutableStateOf("")
    var editPhone by mutableStateOf("")
        private set
    var saving by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    private var started = false

    fun messageShown() {
        message = null
    }

    fun start() {
        if (started) return
        started = true
        load()
    }

    /** 검색어에 걸린 사람들. 칩의 건수도 이 결과에서 센다(웹과 같다). */
    val searched: List<UserProfileDto>
        get() {
            val query = search.trim().lowercase()
            if (query.isEmpty()) return users
            return users.filter { user ->
                listOf(user.nickname, user.username, user.email, user.phone)
                    .any { it?.lowercase()?.contains(query) == true }
            }
        }

    val visible: List<UserProfileDto>
        get() = when (filter) {
            UserFilter.All -> searched
            UserFilter.FalseThree -> searched.filter { it.falseReportCount >= 3 }
            UserFilter.Blacklisted -> searched.filter { it.isBlacklisted }
        }

    fun count(target: UserFilter): Int = when (target) {
        UserFilter.All -> searched.size
        UserFilter.FalseThree -> searched.count { it.falseReportCount >= 3 }
        UserFilter.Blacklisted -> searched.count { it.isBlacklisted }
    }

    private fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            val response = runCatching { api.getAllUsers() }.getOrNull()
            if (response == null || response.body()?.success != true) {
                error = response?.errorMessage("회원 목록을 불러오지 못했습니다.")
                    ?: "서버에 연결하지 못했습니다."
            } else {
                users = response.body()?.data.orEmpty()
            }
            loading = false
        }
    }

    fun openEdit(user: UserProfileDto) {
        editing = user
        editNickname = user.nickname
        editEmail = user.email.orEmpty()
        editPhone = user.phone.orEmpty()
    }

    fun closeEdit() {
        editing = null
    }

    fun onEditPhoneChange(value: String) {
        editPhone = formatPhone(value)
    }

    fun saveEdit() {
        val target = editing ?: return
        saving = true
        viewModelScope.launch {
            val response = runCatching {
                api.updateProfile(
                    target.userId,
                    ProfileUpdateRequest(
                        nickname = editNickname.trim(),
                        email = editEmail.trim().lowercase().takeIf { it.isNotEmpty() },
                        phone = editPhone.trim().takeIf { it.isNotEmpty() },
                    ),
                )
            }.getOrNull()
            saving = false
            if (response == null || response.body()?.success != true) {
                message = "수정 실패: " + (response?.errorMessage("알 수 없는 오류") ?: "서버에 연결하지 못했습니다.")
                return@launch
            }
            editing = null
            message = "수정했습니다."
            load()
        }
    }

    fun toggleBlacklist(user: UserProfileDto) {
        val next = !user.isBlacklisted
        changeStatus(user, UserStatusRequest(isBlacklisted = next)) { _ ->
            if (next) "'${user.nickname}' 을(를) 블랙리스트에 등록했습니다."
            else "'${user.nickname}' 의 블랙리스트를 해제했습니다."
        }
    }

    fun changeRole(user: UserProfileDto, role: String) {
        if (role == user.role) return
        changeStatus(user, UserStatusRequest(role = role)) { needsRelogin ->
            // 역할은 토큰에 박혀 있어서 대상자가 다시 로그인해야 실제로 적용된다.
            if (needsRelogin) "권한을 $role 로 바꿨습니다. 해당 사용자가 다시 로그인해야 적용됩니다."
            else "권한을 $role 로 바꿨습니다."
        }
    }

    private fun changeStatus(
        user: UserProfileDto,
        request: UserStatusRequest,
        note: (Boolean) -> String,
    ) {
        viewModelScope.launch {
            val response = runCatching { api.setUserStatus(user.userId, request) }.getOrNull()
            if (response == null || response.body()?.success != true) {
                message = "상태 변경 실패: " +
                    (response?.errorMessage("알 수 없는 오류") ?: "서버에 연결하지 못했습니다.")
                return@launch
            }
            message = note(response.body()?.data?.requiresRelogin == true)
            load()
        }
    }

    fun deleteUser(user: UserProfileDto) {
        viewModelScope.launch {
            val response = runCatching { api.withdraw(user.userId) }.getOrNull()
            if (response == null || response.body()?.success != true) {
                message = "삭제 실패: " +
                    (response?.errorMessage("알 수 없는 오류") ?: "서버에 연결하지 못했습니다.")
                return@launch
            }
            message = "'${user.nickname}' 을(를) 삭제했습니다."
            load()
        }
    }
}
