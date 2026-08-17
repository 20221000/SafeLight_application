package com.example.safelight.ui.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safelight.data.AttachmentFiles
import com.example.safelight.data.net.PostListDto
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.theme.SafeLightTheme

/**
 * 공지. 웹 AdminNoticePage 를 옮겼다 — 작성 폼이 위, 발행된 공지가 아래.
 * 폼은 웹에서도 전폭 입력이라 모바일에서 달라질 것이 없고, 목록만 표 대신 카드다.
 */
@Composable
fun AdminNoticeScreen(vm: AdminNoticeViewModel = viewModel()) {
    val colors = SafeLightTheme.colors
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<PostListDto?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> vm.addFiles(context, uris) }

    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(vm.message) {
        val text = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.messageShown()
    }

    Scaffold(
        containerColor = colors.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            vm.error?.let { item { AdminErrorBox("데이터를 불러오지 못했습니다: $it") } }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("공지 작성", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textStrong)
                    AdminField("제목", vm.title, { vm.title = it }, placeholder = "공지 제목")
                    AdminField(
                        "내용",
                        vm.content,
                        { vm.content = it },
                        minHeight = 140,
                        singleLine = false,
                        placeholder = "공지 내용",
                    )

                    Column {
                        Text(
                            "첨부파일 (선택)",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textMuted,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.bg)
                                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                .clickable { picker.launch(AttachmentFiles.ALLOWED_MIME_TYPES) }
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                SafeIcons.Paperclip,
                                null,
                                tint = colors.bluePrimary,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                "눌러서 파일 고르기",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textStrong,
                            )
                            // 허용 목록은 백엔드 규칙 그대로다. 여기 적힌 것과 서버가 받는 것이 늘 같다.
                            Text(
                                AttachmentFiles.ALLOWED_HINT,
                                fontSize = 12.sp,
                                color = colors.textMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                        vm.files.forEach { file ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.bg)
                                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 13.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    SafeIcons.Paperclip,
                                    null,
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    file.name,
                                    Modifier.weight(1f),
                                    fontSize = 13.sp,
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    SafeIcons.Close,
                                    "빼기",
                                    tint = colors.danger,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { vm.removeFile(file) },
                                )
                            }
                        }
                    }

                    AdminActionButton(
                        label = if (vm.submitting) "등록 중…" else "공지 등록",
                        tone = ActionTone.Primary,
                        filled = true,
                        onClick = { if (!vm.submitting) vm.create(context) },
                    )
                }
            }

            item {
                Text(
                    "발행된 공지 (${vm.notices.size})",
                    Modifier.padding(top = 4.dp),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textStrong,
                )
            }

            if (vm.notices.isEmpty()) {
                item { AdminEmptyCard(if (vm.loading) "불러오는 중…" else "등록된 공지가 없습니다.") }
            } else {
                items(vm.notices, key = { it.postId }) { notice ->
                    NoticeCard(
                        notice = notice,
                        onEdit = { vm.openEdit(notice) },
                        onDelete = { confirmDelete = notice },
                    )
                }
            }
        }
    }

    confirmDelete?.let { notice ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("공지 '${notice.title}' 을(를) 삭제할까요?") },
            text = { Text("되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(notice)
                    confirmDelete = null
                }) { Text("삭제", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("취소") } },
            containerColor = colors.surface,
            titleContentColor = colors.textStrong,
            textContentColor = colors.textMuted,
        )
    }

    if (vm.editingId != null) EditNoticeDialog(vm)
}

@Composable
private fun NoticeCard(notice: PostListDto, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = SafeLightTheme.colors
    AdminCard(accent = colors.bluePrimary) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(
                notice.title,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textStrong,
            )
            Text(
                "#${notice.postId} · ${fmtDate(notice.createdAt)} · 조회 ${notice.viewCount}",
                Modifier.padding(top = 7.dp),
                fontSize = 11.5.sp,
                color = colors.textMuted,
            )
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp).background(colors.border))
            Row(
                Modifier.padding(top = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AdminActionButton("수정", Modifier.weight(1f), onClick = onEdit)
                AdminActionButton("삭제", Modifier.weight(1f), tone = ActionTone.Danger, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun EditNoticeDialog(vm: AdminNoticeViewModel) {
    val colors = SafeLightTheme.colors
    AlertDialog(
        onDismissRequest = vm::closeEdit,
        title = { Text("공지 수정 · #${vm.editingId}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
        text = {
            if (vm.editLoading) {
                Text(
                    "내용 불러오는 중…",
                    Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    fontSize = 13.sp,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AdminField("제목", vm.editTitle, { vm.editTitle = it })
                    AdminField(
                        "내용",
                        vm.editContent,
                        { vm.editContent = it },
                        minHeight = 160,
                        singleLine = false,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::saveEdit, enabled = !vm.saving && !vm.editLoading) {
                Text(if (vm.saving) "저장 중…" else "저장", color = colors.bluePrimary)
            }
        },
        dismissButton = { TextButton(onClick = vm::closeEdit) { Text("취소") } },
        containerColor = colors.surface,
        titleContentColor = colors.textStrong,
        textContentColor = colors.textMuted,
    )
}
