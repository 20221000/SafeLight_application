package com.example.safelight.ui.community

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.AttachmentFiles
import com.example.safelight.data.net.ApiEnvelope
import com.example.safelight.data.net.AttachmentDto
import com.example.safelight.data.net.CommentCreateRequest
import com.example.safelight.data.net.CommentUpdateRequest
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.PostDetailDto
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.errorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

private const val TAG = "PostDetail"

/** 다운로드 폴더에 저장이 끝난 파일. [uri] 는 다른 앱으로 열 수 있는 주소다. */
data class SavedFile(val uri: Uri, val name: String, val mimeType: String)

/** 웹 PostDetailPage.jsx 의 상태. 게시글 하나 + 댓글 트리를 다룬다. */
class PostDetailViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    private var postId: Long = 0

    var post by mutableStateOf<PostDetailDto?>(null)
        private set
    var loading by mutableStateOf(true)
        private set

    /** 조회 실패 사유(403·404 등). '못 찾음'과 '권한 없음'은 사용자가 할 일이 다르다. */
    var error by mutableStateOf("")
        private set

    // 좋아요는 응답을 기다리지 않고 먼저 화면에 반영한다(웹과 같다) — 그래서 별도 상태로 들고 있다.
    var isLiked by mutableStateOf(false)
        private set
    var likeCount by mutableStateOf(0)
        private set

    var commentInput by mutableStateOf("")
    var replyTargetId by mutableStateOf<Long?>(null)
        private set
    var replyInput by mutableStateOf("")
    var editingId by mutableStateOf<Long?>(null)
        private set
    var editingContent by mutableStateOf("")

    /** 스낵바로 한 번 보여주고 지울 문구. */
    var message by mutableStateOf<String?>(null)
        private set

    /** 게시글이 지워져 목록으로 돌아가야 할 때 true 가 된다. */
    var deleted by mutableStateOf(false)
        private set

    /** 지금 내려받는 중인 첨부파일. 같은 줄을 두 번 누르는 것을 막고 진행 표시에 쓴다. */
    var downloadingId by mutableStateOf<Long?>(null)
        private set

    /** 저장이 끝난 파일. 스낵바에서 '열기'로 이어진다. */
    var savedFile by mutableStateOf<SavedFile?>(null)
        private set

    fun start(postId: Long) {
        if (this.postId == postId) return
        this.postId = postId
        reload()
    }

    fun messageShown() {
        message = null
    }

    fun reload() {
        loading = true
        viewModelScope.launch {
            runCatching { api.getPost(postId) }
                .onSuccess { response ->
                    val body = response.body()
                    val data = body?.data
                    if (!response.isSuccessful || body?.success != true || data == null) {
                        error = if (response.isSuccessful) body?.message ?: "게시글을 불러오지 못했습니다."
                        else response.errorMessage("게시글을 불러오지 못했습니다.")
                        return@onSuccess
                    }
                    error = ""
                    post = data
                    isLiked = data.isLiked
                    likeCount = data.likeCount
                }
                .onFailure {
                    Log.e(TAG, "게시글 조회 실패", it)
                    error = "서버에 연결하지 못했습니다."
                }
            loading = false
        }
    }

    fun toggleLike() {
        val next = !isLiked
        // 먼저 반영하고 요청을 보낸다. 실패하면 되돌린다.
        isLiked = next
        likeCount += if (next) 1 else -1
        viewModelScope.launch {
            val ok = runCatching {
                if (next) api.likePost(postId) else api.unlikePost(postId)
            }.getOrNull()?.isSuccessful == true
            if (!ok) {
                isLiked = !next
                likeCount += if (next) -1 else 1
                message = "좋아요를 반영하지 못했습니다."
            }
        }
    }

    fun submitComment() {
        val text = commentInput.trim()
        if (text.isEmpty()) return
        send({ api.createComment(postId, CommentCreateRequest(text)) }, "댓글 등록에 실패했습니다.") {
            commentInput = ""
        }
    }

    fun startReply(commentId: Long) {
        replyTargetId = if (replyTargetId == commentId) null else commentId
        replyInput = ""
    }

    fun submitReply(parentId: Long) {
        val text = replyInput.trim()
        if (text.isEmpty()) return
        send({ api.createComment(postId, CommentCreateRequest(text, parentId)) }, "답글 등록에 실패했습니다.") {
            replyInput = ""
            replyTargetId = null
        }
    }

    fun startEdit(commentId: Long, content: String) {
        editingId = commentId
        editingContent = content
    }

    fun cancelEdit() {
        editingId = null
        editingContent = ""
    }

    fun saveEdit(commentId: Long) {
        val text = editingContent.trim()
        if (text.isEmpty()) return
        send({ api.updateComment(postId, commentId, CommentUpdateRequest(text)) }, "댓글 수정에 실패했습니다.") {
            cancelEdit()
        }
    }

    fun deleteComment(commentId: Long) {
        send({ api.deleteComment(postId, commentId) }, "댓글 삭제에 실패했습니다.") {}
    }

    /**
     * 첨부파일을 기기의 '다운로드' 폴더에 저장한다. 웹은 브라우저 내려받기로 같은 자리에 떨군다.
     *
     * Android 10 아래에서는 호출하는 쪽이 저장 권한을 먼저 받아 둬야 한다
     * ([AttachmentFiles.needsLegacyPermission]).
     */
    fun download(context: Context, attachment: AttachmentDto) {
        if (downloadingId != null) return
        downloadingId = attachment.attachmentId
        viewModelScope.launch {
            val result = runCatching {
                val response = api.downloadAttachment(attachment.attachmentId)
                if (!response.isSuccessful) {
                    throw IllegalStateException(response.errorMessage("첨부파일을 받지 못했습니다."))
                }
                val body = response.body() ?: throw IllegalStateException("첨부파일이 비어 있습니다.")
                // 파일을 쓰는 동안 화면이 멈추지 않게 한다.
                withContext(Dispatchers.IO) {
                    AttachmentFiles.saveToDownloads(
                        context,
                        attachment.originalFilename,
                        attachment.contentType,
                        body,
                    )
                }
            }
            downloadingId = null
            result
                .onSuccess { uri ->
                    savedFile = SavedFile(uri, attachment.originalFilename, attachment.contentType)
                }
                .onFailure {
                    Log.e(TAG, "첨부파일 저장 실패", it)
                    message = it.message ?: "첨부파일을 저장하지 못했습니다."
                }
        }
    }

    fun savedFileShown() {
        savedFile = null
    }

    /** 첨부파일 개별 삭제. 백엔드는 글 작성자만 허용한다. */
    fun deleteAttachment(attachmentId: Long) {
        send({ api.deleteAttachment(attachmentId) }, "첨부파일 삭제에 실패했습니다.") {}
    }

    fun deletePost() {
        viewModelScope.launch {
            val response = runCatching { api.deletePost(postId) }.getOrNull()
            if (response?.isSuccessful == true && response.body()?.success == true) {
                deleted = true
                return@launch
            }
            message = response?.errorMessage("게시글 삭제에 실패했습니다.") ?: "서버에 연결하지 못했습니다."
        }
    }

    /** 성공하면 [onSuccess] 뒤에 상세를 다시 읽는다 — 댓글 수·트리 모양은 서버가 정한다. */
    private fun send(
        request: suspend () -> Response<out ApiEnvelope<*>>,
        failMessage: String,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            val response = runCatching { request() }.getOrNull()
            if (response?.isSuccessful == true && response.body()?.success == true) {
                onSuccess()
                reload()
                return@launch
            }
            message = response?.errorMessage(failMessage) ?: "서버에 연결하지 못했습니다."
        }
    }
}
