package com.example.safelight.ui.community

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
import com.example.safelight.data.net.PostCreateRequest
import com.example.safelight.data.net.PostUpdateRequest
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.errorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 글쓰기에서 고를 수 있는 카테고리. NOTICE(공지)는 관리자 전용이라 빠진다(웹과 같다). */
enum class WritableCategory(val code: String, val label: String) {
    Info("INFO", "정보"),
    Question("QUESTION", "질문"),
    Report("REPORT", "안전 신고"),
    Tip("TIP", "팁"),
}

const val TITLE_MAX = 100
const val CONTENT_MAX = 5000

/** 웹 PostWritePage.jsx. postId 가 있으면 수정 모드다. */
class PostWriteViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    private var postId: Long? = null
    private var started = false

    val isEdit: Boolean get() = postId != null

    var title by mutableStateOf("")
    var content by mutableStateOf("")

    /**
     * 수정 모드는 null 로 시작해 불러온 값으로 채운다. 'INFO' 를 기본값으로 깔면 응답이 오기 전에
     * 저장했을 때 PUT 이 category:'INFO' 를 실어 보내 질문·팁·안전신고 글이 조용히 '정보'로 바뀐다.
     */
    var category by mutableStateOf<String?>(null)
        private set

    /** 올릴 파일. 수정 모드에서는 쓰지 않는다(PUT /posts/{id} 는 파일을 다루지 않는다). */
    var files by mutableStateOf<List<PickedFile>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    /** 스낵바로 한 번 보여주고 지울 문구. */
    var message by mutableStateOf<String?>(null)
        private set

    /** 저장이 끝났다. 수정이면 그 글로, 새 글이면 목록으로 돌아간다. */
    var saved by mutableStateOf(false)
        private set

    /** 남의 글을 열었을 때처럼 화면을 유지할 이유가 없을 때. */
    var bounce by mutableStateOf(false)
        private set

    fun start(postId: Long?, currentUserId: Long?) {
        if (started) return
        started = true
        this.postId = postId
        if (postId == null) {
            category = WritableCategory.Info.code
            return
        }
        loading = true
        viewModelScope.launch {
            val response = runCatching { api.getPost(postId) }.getOrNull()
            val data = response?.body()?.data
            if (response?.isSuccessful != true || response.body()?.success != true || data == null) {
                message = response?.errorMessage("게시글을 불러오지 못했습니다.") ?: "서버에 연결하지 못했습니다."
                bounce = true
                loading = false
                return@launch
            }
            if (data.userId != currentUserId) {
                message = "본인 게시글만 수정할 수 있습니다."
                bounce = true
                loading = false
                return@launch
            }
            title = data.title
            content = data.content
            // 공지(NOTICE)는 고를 수 있는 칩에 없다. 그래도 담아 둬야 아래에서 '못 불러왔다'가 아니라
            // '공지라 못 바꾼다'고 말할 수 있다.
            if (data.category.isNotBlank()) category = data.category
            loading = false
        }
    }

    fun selectCategory(code: String) {
        category = code
    }

    fun messageShown() {
        message = null
    }

    /**
     * 고른 파일을 담는다. 서버도 같은 검사를 하지만, 올리고 나서 400 을 보는 것보다
     * 고르는 자리에서 바로 알려주는 편이 낫다. 거절된 것만 빼고 나머지는 담는다.
     */
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
            // 같은 파일을 두 번 고르면 서버에도 두 번 올라간다. 여기서 접는다.
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

    fun submit(context: Context) {
        if (title.isBlank()) {
            message = "제목을 입력해주세요."
            return
        }
        if (content.isBlank()) {
            message = "내용을 입력해주세요."
            return
        }
        loading = true
        viewModelScope.launch {
            val id = postId
            val response = runCatching {
                when {
                    // category 를 모르면 아예 보내지 않는다 — 백엔드는 null 이면 기존 값을 그대로 둔다.
                    id != null -> api.updatePost(id, PostUpdateRequest(title, content, category))

                    files.isNotEmpty() -> {
                        val code = category ?: WritableCategory.Info.code
                        // 파일 읽기는 메인 스레드에서 하지 않는다(10MB × 여러 개면 화면이 멈춘다).
                        val parts = withContext(Dispatchers.IO) {
                            files.mapNotNull { AttachmentFiles.part(context, it) }
                        }
                        if (parts.size != files.size) {
                            message = "첨부파일을 읽지 못했습니다. 다시 골라주세요."
                            loading = false
                            return@launch
                        }
                        api.createPostWithFiles(
                            AttachmentFiles.textPart(title),
                            AttachmentFiles.textPart(content),
                            AttachmentFiles.textPart(code),
                            parts,
                        )
                    }

                    else -> api.createPost(
                        PostCreateRequest(title, content, category ?: WritableCategory.Info.code),
                    )
                }
            }.getOrNull()

            if (response?.isSuccessful == true && response.body()?.success == true) {
                saved = true
                loading = false
                return@launch
            }
            message = response?.errorMessage(
                if (id != null) "게시글 수정에 실패했습니다." else "게시글 등록에 실패했습니다.",
            ) ?: "서버에 연결하지 못했습니다."
            loading = false
        }
    }
}
