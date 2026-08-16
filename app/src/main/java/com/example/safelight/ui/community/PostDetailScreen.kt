package com.example.safelight.ui.community

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safelight.data.AttachmentFiles
import com.example.safelight.data.SessionUser
import com.example.safelight.data.net.AttachmentDto
import com.example.safelight.data.net.CommentDto
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.theme.SafeLightTheme

/** 삭제를 물어볼 대상. 문구가 대상마다 달라서 무엇을 지우려는지 함께 들고 있는다. */
private sealed interface DeleteTarget {
    val mine: Boolean

    data class Post(override val mine: Boolean) : DeleteTarget
    data class Comment(val commentId: Long, override val mine: Boolean) : DeleteTarget

    /** 첨부파일은 글 작성자만 지울 수 있어서 남의 것을 지우는 경우가 없다. */
    data class Attachment(val attachmentId: Long) : DeleteTarget {
        override val mine: Boolean get() = true
    }
}

/** 웹 PostDetailPage.jsx. 공지 글은 좋아요·댓글이 없다(웹과 같다). */
@Composable
fun PostDetailScreen(
    postId: Long,
    user: SessionUser?,
    /** 이 값이 바뀌면 글을 다시 읽는다. 글을 고치고 돌아왔을 때 화면에 옛 내용이 남지 않게. */
    reloadKey: Int,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onGoLogin: () -> Unit,
    modifier: Modifier = Modifier,
    vm: PostDetailViewModel = viewModel(),
) {
    val colors = SafeLightTheme.colors
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirming by remember { mutableStateOf<DeleteTarget?>(null) }
    var lastReloadKey by remember { mutableStateOf(reloadKey) }

    // Android 10 아래에서만 쓴다. 권한을 받고 나서 이어서 받을 파일을 여기 잠깐 둔다.
    var pendingDownload by remember { mutableStateOf<AttachmentDto?>(null) }
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val file = pendingDownload
        pendingDownload = null
        if (granted && file != null) vm.download(context, file)
    }

    LaunchedEffect(postId) { vm.start(postId) }
    LaunchedEffect(reloadKey) {
        // 화면에 처음 들어올 때는 start() 가 이미 읽었으므로 건너뛴다.
        if (reloadKey != lastReloadKey) {
            lastReloadKey = reloadKey
            vm.reload()
        }
    }
    LaunchedEffect(vm.deleted) { if (vm.deleted) onBack() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }
    // 저장만 하고 끝내면 파일이 어디 갔는지 알 수 없다. 바로 열 수 있는 길을 함께 준다.
    LaunchedEffect(vm.savedFile) {
        val saved = vm.savedFile ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "다운로드 폴더에 저장했습니다 — ${saved.name}",
            actionLabel = "열기",
        )
        vm.savedFileShown()
        if (result != SnackbarResult.ActionPerformed) return@LaunchedEffect
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(saved.uri, saved.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // 열 수 있는 앱이 하나도 없을 수 있다(txt·webp 등). 그때는 앱이 죽지 않게 알리기만 한다.
        runCatching { context.startActivity(view) }
            .onFailure { snackbar.showSnackbar("이 파일을 열 수 있는 앱이 없습니다.") }
    }

    val post = vm.post
    val isNotice = post?.category == "NOTICE"
    // 관리자에게 열린 건 '삭제'뿐이다(백엔드 PostService.isOwnerOrAdmin — deletePost·deleteComment).
    // 수정은 게시글·댓글 모두 작성자 검사를 그대로 둬서 관리자가 눌러도 403 이다.
    val isOwner = user != null && post != null && user.userId == post.userId
    val canDeletePost = isOwner || (user?.isAdmin == true && post != null)

    Box(modifier.fillMaxSize().background(colors.bg)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.clickable(onClick = onBack),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(SafeIcons.ChevronLeft, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                        Text("목록으로", fontSize = 14.sp, color = colors.textMuted)
                    }
                    Box(Modifier.weight(1f))
                    if (canDeletePost) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // 남의 글에 뜬 삭제 버튼은 근거가 보여야 오작동으로 읽히지 않는다.
                            if (!isOwner) {
                                Text("관리자 권한", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
                            }
                            if (isOwner) {
                                OutlineButton("수정", colors.textMuted, colors.border) { onEdit(postId) }
                            }
                            OutlineButton("삭제", colors.danger, colors.danger) {
                                confirming = DeleteTarget.Post(isOwner)
                            }
                        }
                    }
                }
            }

            if (vm.loading && post == null) {
                item { CenterNote("불러오는 중...", verticalPadding = 60.dp) }
            } else if (post == null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 52.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(SafeIcons.AlertTriangle, null, tint = colors.warning, modifier = Modifier.size(22.dp))
                        Text(
                            vm.error.ifBlank { "게시글을 찾을 수 없습니다." },
                            fontSize = 13.5.sp,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (user == null) {
                            Box(
                                Modifier
                                    .padding(top = 14.dp)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.bluePrimary)
                                    .clickable(onClick = onGoLogin)
                                    .padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("로그인하러 가기", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                    ) {
                        CategoryBadge(post.category, fontSize = 11f)
                        Text(
                            post.title,
                            modifier = Modifier.padding(top = 12.dp, bottom = 14.dp),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textStrong,
                            lineHeight = 30.sp,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Avatar(post.nickname, 30.dp)
                            Text(post.nickname, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textStrong)
                            Text(post.createdAt.toDateOnly(), fontSize = 13.sp, color = colors.textMuted)
                            IconCount(SafeIcons.Eye, post.viewCount)
                            if (!isNotice) IconCount(SafeIcons.Heart, vm.likeCount)
                        }
                        Divider(top = 18.dp, bottom = 18.dp)
                        Text(
                            post.content,
                            fontSize = 15.sp,
                            color = colors.textStrong,
                            lineHeight = 27.sp,
                        )

                        if (post.attachments.isNotEmpty()) {
                            AttachmentList(
                                files = post.attachments,
                                downloadingId = vm.downloadingId,
                                canDelete = isOwner,
                                onDownload = { file ->
                                    // Android 10 아래에서는 공용 폴더에 직접 쓰므로 권한이 필요하다.
                                    if (AttachmentFiles.needsLegacyPermission()) {
                                        pendingDownload = file
                                        storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    } else {
                                        vm.download(context, file)
                                    }
                                },
                                onDelete = { confirming = DeleteTarget.Attachment(it.attachmentId) },
                            )
                        }

                        if (!isNotice) {
                            Divider(top = 20.dp, bottom = 18.dp)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                WideButton(
                                    icon = SafeIcons.Heart,
                                    label = "좋아요 ${vm.likeCount}",
                                    active = vm.isLiked,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (user == null) onGoLogin() else vm.toggleLike()
                                }
                                WideButton(
                                    icon = SafeIcons.Link,
                                    label = "공유하기",
                                    active = false,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    // 웹은 주소를 복사한다. 앱에는 주소가 없어서 안드로이드 공유 시트를 연다
                                    // (실제로 보낼 곳은 사용자가 고른다).
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${post.title} — Safe Light 커뮤니티")
                                    }
                                    context.startActivity(Intent.createChooser(send, "공유하기"))
                                }
                            }
                            Divider(top = 18.dp, bottom = 16.dp)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(SafeIcons.Message, null, tint = colors.bluePrimary, modifier = Modifier.size(17.dp))
                                Text(
                                    "댓글 ${post.comments.size}개",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textStrong,
                                )
                            }

                            Box(Modifier.padding(top = 14.dp, bottom = 6.dp)) {
                                if (user == null) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(colors.bg)
                                            .border(1.dp, colors.border, RoundedCornerShape(11.dp))
                                            .clickable(onClick = onGoLogin)
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Text("댓글을 작성하려면 로그인이 필요합니다.", fontSize = 13.5.sp, color = colors.textMuted)
                                    }
                                } else {
                                    CommentComposer(
                                        value = vm.commentInput,
                                        onValueChange = { vm.commentInput = it },
                                        placeholder = "댓글을 입력해주세요...",
                                        onSubmit = vm::submitComment,
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isNotice) {
                    if (post.comments.isEmpty()) {
                        item { CenterNote("첫 번째 댓글을 작성해보세요!", verticalPadding = 30.dp, fontSize = 13f) }
                    }
                    items(post.comments, key = { it.commentId }) { comment ->
                        CommentBlock(
                            comment = comment,
                            user = user,
                            vm = vm,
                            onAskDelete = { id, mine -> confirming = DeleteTarget.Comment(id, mine) },
                        )
                    }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    // 남의 글을 지우는 건 내 글을 지우는 것과 무게가 다르다. 관리자 권한으로 지울 때는 그렇다고 말한다.
    confirming?.let { target ->
        val what = when (target) {
            is DeleteTarget.Post -> "게시글"
            is DeleteTarget.Comment -> "댓글"
            is DeleteTarget.Attachment -> "첨부파일"
        }
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(if (target.mine) "${what}을 삭제할까요?" else "다른 사용자의 ${what}입니다.") },
            text = { Text(if (target.mine) "되돌릴 수 없습니다." else "관리자 권한으로 삭제할까요? 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        is DeleteTarget.Post -> vm.deletePost()
                        is DeleteTarget.Comment -> vm.deleteComment(target.commentId)
                        is DeleteTarget.Attachment -> vm.deleteAttachment(target.attachmentId)
                    }
                    confirming = null
                }) { Text("삭제", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("취소") } },
            containerColor = colors.surface,
            titleContentColor = colors.textStrong,
            textContentColor = colors.textMuted,
        )
    }
}

@Composable
private fun CommentBlock(
    comment: CommentDto,
    user: SessionUser?,
    vm: PostDetailViewModel,
    onAskDelete: (Long, Boolean) -> Unit,
) {
    val colors = SafeLightTheme.colors
    Column {
        CommentRow(
            comment = comment,
            user = user,
            vm = vm,
            avatarSize = 34.dp,
            startPadding = 0.dp,
            canReply = user != null,
            onAskDelete = onAskDelete,
        )
        comment.replies.forEach { reply ->
            Box(Modifier.fillMaxWidth().background(colors.bg)) {
                CommentRow(
                    comment = reply,
                    user = user,
                    vm = vm,
                    avatarSize = 30.dp,
                    startPadding = 46.dp,
                    canReply = false,
                    onAskDelete = onAskDelete,
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentDto,
    user: SessionUser?,
    vm: PostDetailViewModel,
    avatarSize: androidx.compose.ui.unit.Dp,
    startPadding: androidx.compose.ui.unit.Dp,
    canReply: Boolean,
    onAskDelete: (Long, Boolean) -> Unit,
) {
    val colors = SafeLightTheme.colors
    val mine = user != null && user.userId == comment.userId
    val canDelete = mine || user?.isAdmin == true
    val editing = vm.editingId == comment.commentId

    Column(Modifier.fillMaxWidth().padding(start = startPadding)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(comment.nickname, avatarSize)
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(comment.nickname, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textStrong)
                    Text(comment.createdAt.toDateOnly(), fontSize = 12.sp, color = colors.textMuted)
                }
                if (editing) {
                    InlineEditor(
                        value = vm.editingContent,
                        onValueChange = { vm.editingContent = it },
                        onSave = { vm.saveEdit(comment.commentId) },
                        onCancel = vm::cancelEdit,
                    )
                } else {
                    Text(
                        comment.content,
                        modifier = Modifier.padding(top = 6.dp),
                        fontSize = 14.sp,
                        color = colors.textStrong,
                        lineHeight = 22.sp,
                    )
                    Row(
                        Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (canReply) {
                            TextAction("답글", colors.textMuted) { vm.startReply(comment.commentId) }
                        }
                        if (mine) {
                            TextAction("수정", colors.textMuted) { vm.startEdit(comment.commentId, comment.content) }
                        }
                        if (canDelete) {
                            TextAction("삭제", colors.danger) { onAskDelete(comment.commentId, mine) }
                        }
                    }
                }
                if (vm.replyTargetId == comment.commentId) {
                    Box(Modifier.padding(top = 8.dp)) {
                        CommentComposer(
                            value = vm.replyInput,
                            onValueChange = { vm.replyInput = it },
                            placeholder = "답글을 입력해주세요...",
                            onSubmit = { vm.submitReply(comment.commentId) },
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

/**
 * 댓글·답글 공용 입력칸. 한 줄이 차면 옆으로 밀지 않고 줄을 바꾸며 칸이 아래로 자란다.
 *
 * Enter 를 등록으로 가로채지 않는다 — 화면 키보드가 줄바꿈 키를 띄워주는데 여기서 가로채면
 * 줄을 바꾸려다 글이 올라간다(웹도 모바일에서는 같은 이유로 Enter 를 넘긴다). 등록은 버튼으로만.
 */
@Composable
private fun CommentComposer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 42.dp, max = 120.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.bg)
                .border(1.dp, colors.border, RoundedCornerShape(11.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            if (value.isEmpty()) Text(placeholder, fontSize = 13.5.sp, color = colors.textMuted)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.5.sp, color = colors.textStrong, lineHeight = 20.sp),
                cursorBrush = SolidColor(colors.bluePrimary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.bluePrimary)
                .clickable(onClick = onSubmit)
                .padding(horizontal = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("등록", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun InlineEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    Column(Modifier.padding(top = 4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.bg)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = colors.textStrong, lineHeight = 22.sp),
                cursorBrush = SolidColor(colors.bluePrimary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(colors.bluePrimary)
                    .clickable(onClick = onSave)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("저장", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            OutlineButton("취소", colors.textMuted, colors.border, height = 34.dp, onClick = onCancel)
        }
    }
}

/**
 * 첨부파일 목록. 줄을 누르면 기기의 '다운로드' 폴더에 내려받는다(웹의 다운로드 링크 자리).
 * 종류 배지·크기는 웹과 같은 규칙으로 계산한다 — 파일명 확장자가 아니라 서버가 기록한 MIME 을 본다.
 */
@Composable
private fun AttachmentList(
    files: List<AttachmentDto>,
    downloadingId: Long?,
    canDelete: Boolean,
    onDownload: (AttachmentDto) -> Unit,
    onDelete: (AttachmentDto) -> Unit,
) {
    val colors = SafeLightTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Row(
            Modifier.padding(top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(SafeIcons.Paperclip, null, tint = colors.bluePrimary, modifier = Modifier.size(15.dp))
            Text("첨부파일", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textStrong)
        }
        files.forEach { file ->
            val kind = fileKind(file.contentType)
            val downloading = downloadingId == file.attachmentId
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bg)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        // 내려받는 중에는 같은 줄을 다시 누를 수 없다.
                        .clickable(enabled = downloadingId == null) { onDownload(file) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(SafeIcons.File, null, tint = kind.color, modifier = Modifier.size(14.dp))
                    Text(
                        file.originalFilename,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (downloading) {
                        Text("받는 중...", fontSize = 11.sp, color = colors.bluePrimary)
                    } else {
                        Text(
                            kind.label,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.surface)
                                .border(1.dp, kind.color.copy(alpha = .2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = kind.color,
                        )
                        Text(fileSize(file.size), fontSize = 11.sp, color = colors.textMuted)
                    }
                }
                // 개별 삭제는 글 작성자만 볼 수 있다(백엔드도 작성자만 허용한다 — 관리자도 안 된다).
                if (canDelete) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                            .clickable { onDelete(file) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(SafeIcons.Trash, "첨부파일 삭제", tint = colors.danger, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

private data class FileKind(val label: String, val color: Color)

/** 웹 PostDetailPage 의 FILE_KINDS 와 같은 판정·색이다. */
@Composable
private fun fileKind(contentType: String): FileKind {
    val colors = SafeLightTheme.colors
    val t = contentType.lowercase()
    return when {
        t.isBlank() -> FileKind("파일", colors.textMuted)
        t.startsWith("image/") -> FileKind("이미지", Color(0xFF0EA5E9))
        t.startsWith("video/") -> FileKind("영상", Color(0xFF8B5CF6))
        t == "application/pdf" -> FileKind("PDF", Color(0xFFE11D48))
        Regex("zip|compressed|x-tar|gzip|x-7z|rar").containsMatchIn(t) -> FileKind("압축", Color(0xFFF59E0B))
        Regex("excel|spreadsheet|csv").containsMatchIn(t) -> FileKind("표", Color(0xFF10B981))
        Regex("word|hwp|opendocument|presentation|powerpoint|rtf").containsMatchIn(t) -> FileKind("문서", Color(0xFF2563EB))
        t.startsWith("text/") -> FileKind("텍스트", Color(0xFF64748B))
        else -> FileKind("파일", colors.textMuted)
    }
}

/** 1KB 미만 파일이 '0.0KB' 로 보이지 않게 단위를 올린다(웹과 같다). */
private fun fileSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> String.format("%.1fKB", bytes / 1024f)
    else -> String.format("%.1fMB", bytes / 1024f / 1024f)
}

@Composable
internal fun Avatar(name: String?, size: androidx.compose.ui.unit.Dp) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier.size(size).clip(CircleShape).background(colors.blueTint),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name?.take(1) ?: "?",
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.Bold,
            color = colors.bluePrimary,
        )
    }
}

@Composable
private fun IconCount(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int) {
    val colors = SafeLightTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
        Text("$count", fontSize = 13.sp, color = colors.textMuted)
    }
}

@Composable
private fun Divider(top: androidx.compose.ui.unit.Dp, bottom: androidx.compose.ui.unit.Dp) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = top, bottom = bottom)
            .height(1.dp)
            .background(colors.border),
    )
}

@Composable
internal fun OutlineButton(
    label: String,
    contentColor: Color,
    borderColor: Color,
    height: androidx.compose.ui.unit.Dp = 36.dp,
    onClick: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
    }
}

@Composable
private fun WideButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    val content = if (active) colors.danger else colors.textMuted
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) colors.danger.copy(alpha = .08f) else colors.surface)
            .border(1.dp, if (active) colors.danger else colors.border, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = content)
    }
}

@Composable
private fun TextAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.clickable(onClick = onClick),
        fontSize = 12.sp,
        color = color,
    )
}
