package com.example.safelight.ui.admin

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.AttachmentFiles
import com.example.safelight.data.PickedFile
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.NoticeCreateRequest
import com.example.safelight.data.net.PostListDto
import com.example.safelight.data.net.PostUpdateRequest
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.errorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 공지는 많아야 수십 건이라 한 번에 받는다(웹도 size=100 한 장이다). */
private const val NOTICE_PAGE_SIZE = 100

/** 공지 관리. 작성 폼과 목록이 한 화면에 있다(웹 AdminNoticePage 와 같다). */
class AdminNoticeViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    var notices by mutableStateOf<List<PostListDto>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var title by mutableStateOf("")
    var content by mutableStateOf("")
    var files by mutableStateOf<List<PickedFile>>(emptyList())
        private set
    var submitting by mutableStateOf(false)
        private set

    /** 수정 창. 목록에는 본문이 없어 열 때 한 건을 더 받는다. */
    var editingId by mutableStateOf<Long?>(null)
        private set
    var editTitle by mutableStateOf("")
    var editContent by mutableStateOf("")
    var editLoading by mutableStateOf(false)
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

    private fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            val response = runCatching {
                api.getPostsByCategory("NOTICE", 0, NOTICE_PAGE_SIZE, "latest")
            }.getOrNull()
            if (response == null || response.body()?.success != true) {
                error = response?.errorMessage("공지를 불러오지 못했습니다.") ?: "서버에 연결하지 못했습니다."
            } else {
                notices = response.body()?.data?.items.orEmpty()
            }
            loading = false
        }
    }

    /** 서버도 같은 검사를 하지만, 올린 뒤 400 을 보는 것보다 고르는 자리에서 알려주는 편이 낫다. */
    fun addFiles(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val accepted = mutableListOf<PickedFile>()
        var rejected: String? = null
        uris.forEach { uri ->
            val file = AttachmentFiles.read(context, uri)
            if (file == null) {
                rejected = "파일을 읽을 수 없습니다."
                return@forEach
            }
            if (files.any { it.uri == file.uri } || accepted.any { it.uri == file.uri }) return@forEach
            val reason = AttachmentFiles.reject(file)
            if (reason != null) {
                rejected = "${file.name}: $reason"
                return@forEach
            }
            accepted += file
        }
        if (accepted.isNotEmpty()) files = files + accepted
        rejected?.let { message = it }
    }

    fun removeFile(file: PickedFile) {
        files = files.filterNot { it.uri == file.uri }
    }

    fun create(context: Context) {
        val trimmedTitle = title.trim()
        val trimmedContent = content.trim()
        if (trimmedTitle.isEmpty() || trimmedContent.isEmpty()) {
            message = "제목과 내용을 입력해주세요."
            return
        }
        submitting = true
        viewModelScope.launch {
            val response = runCatching {
                if (files.isEmpty()) {
                    api.createNotice(NoticeCreateRequest(trimmedTitle, trimmedContent))
                } else {
                    // 파일 읽기는 메인 스레드에서 하지 않는다(10MB × 여러 개면 화면이 멈춘다).
                    val parts = withContext(Dispatchers.IO) {
                        files.mapNotNull { AttachmentFiles.part(context, it) }
                    }
                    if (parts.size != files.size) {
                        message = "첨부파일을 읽지 못했습니다. 다시 골라주세요."
                        submitting = false
                        return@launch
                    }
                    api.createNoticeWithFiles(
                        AttachmentFiles.textPart(trimmedTitle),
                        AttachmentFiles.textPart(trimmedContent),
                        parts,
                    )
                }
            }.getOrNull()
            submitting = false
            if (response == null || response.body()?.success != true) {
                message = "공지 등록 실패: " +
                    (response?.errorMessage("알 수 없는 오류") ?: "서버에 연결하지 못했습니다.")
                return@launch
            }
            title = ""
            content = ""
            files = emptyList()
            message = "공지를 등록했습니다."
            load()
        }
    }

    fun openEdit(notice: PostListDto) {
        editingId = notice.postId
        editTitle = notice.title
        editContent = ""
        editLoading = true
        viewModelScope.launch {
            val response = runCatching { api.getPost(notice.postId) }.getOrNull()
            editLoading = false
            val detail = if (response?.isSuccessful == true && response.body()?.success == true) {
                response.body()?.data
            } else null
            if (detail == null) {
                message = "공지 내용을 불러오지 못했습니다."
                editingId = null
                return@launch
            }
            editTitle = detail.title
            editContent = detail.content
        }
    }

    fun closeEdit() {
        editingId = null
    }

    fun saveEdit() {
        val postId = editingId ?: return
        if (editTitle.isBlank() || editContent.isBlank()) {
            message = "제목과 내용을 입력해주세요."
            return
        }
        saving = true
        viewModelScope.launch {
            val response = runCatching {
                api.updatePost(
                    postId,
                    PostUpdateRequest(editTitle.trim(), editContent.trim(), "NOTICE"),
                )
            }.getOrNull()
            saving = false
            if (response == null || response.body()?.success != true) {
                message = "공지 수정 실패: " +
                    (response?.errorMessage("알 수 없는 오류") ?: "서버에 연결하지 못했습니다.")
                return@launch
            }
            editingId = null
            message = "공지를 수정했습니다."
            load()
        }
    }

    fun delete(notice: PostListDto) {
        viewModelScope.launch {
            val response = runCatching { api.deletePost(notice.postId) }.getOrNull()
            if (response == null || response.body()?.success != true) {
                message = "삭제 실패: " +
                    (response?.errorMessage("알 수 없는 오류") ?: "서버에 연결하지 못했습니다.")
                return@launch
            }
            message = "공지를 삭제했습니다."
            load()
        }
    }
}
