package com.example.safelight.ui.community

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.example.safelight.data.PickedFile
import com.example.safelight.data.SessionUser
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.theme.SafeLightTheme

/**
 * 웹 PostWritePage.jsx. [postId] 가 있으면 수정 모드다.
 *
 * 첨부파일 올리기는 옮기지 않았다 — 웹도 수정 모드에서는 감추고, 새 글의 첨부는
 * /posts/with-files(multipart)라 파일 선택기까지 함께 붙여야 한다.
 */
@Composable
fun PostWriteScreen(
    postId: Long?,
    user: SessionUser?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    vm: PostWriteViewModel = viewModel(),
) {
    val colors = SafeLightTheme.colors
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // 웹은 <input type="file" multiple>. 안드로이드는 시스템 문서 선택기를 연다.
    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> vm.addFiles(context, uris) }

    LaunchedEffect(postId, user?.userId) { vm.start(postId, user?.userId) }
    LaunchedEffect(vm.saved) { if (vm.saved) onSaved() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
            // 열 수 없는 글이었으면 알린 뒤에 되돌아간다.
            if (vm.bounce) onBack()
        }
    }

    Box(modifier.fillMaxSize().background(colors.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // 뒤로가기를 제목 위에 둔다. 한 줄에 나란히 두면 제목이 버튼에 딸린 것처럼 보인다.
            Row(
                Modifier.clickable(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(SafeIcons.ChevronLeft, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                Text(if (vm.isEdit) "게시글로" else "목록으로", fontSize = 14.sp, color = colors.textMuted)
            }
            Text(
                if (vm.isEdit) "게시글 수정" else "게시글 작성",
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textStrong,
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .padding(18.dp),
            ) {
                FieldLabel("카테고리")
                CategoryPicker(selected = vm.category, onSelect = vm::selectCategory)
                // 수정 모드에서 아무 칩도 켜지지 않는 두 경우. 비워 두면 "안 고른 것"으로 읽혀
                // 사용자가 아무거나 눌러 카테고리를 바꿔버린다. 왜 비었는지 말해 준다.
                if (vm.isEdit && vm.category == "NOTICE") {
                    Hint("공지 글입니다. 카테고리는 바뀌지 않습니다.")
                }
                if (vm.isEdit && vm.category == null) {
                    Hint("현재 카테고리를 불러올 수 없습니다. 그대로 두려면 선택하지 마세요 — 고르면 그 값으로 바뀝니다.")
                }

                Box(Modifier.height(22.dp))
                FieldLabel("제목")
                CountedField(
                    value = vm.title,
                    onValueChange = { if (it.length <= TITLE_MAX) vm.title = it },
                    placeholder = "제목을 입력해주세요",
                    counter = "${vm.title.length} / $TITLE_MAX",
                    singleLine = true,
                    minHeight = 46.dp,
                )

                Box(Modifier.height(22.dp))
                FieldLabel("내용")
                CountedField(
                    value = vm.content,
                    onValueChange = { if (it.length <= CONTENT_MAX) vm.content = it },
                    placeholder = "내용을 입력해주세요.",
                    counter = "${vm.content.length} / $CONTENT_MAX",
                    singleLine = false,
                    minHeight = 220.dp,
                )

                Box(Modifier.height(22.dp))
                // 첨부파일은 수정 모드에서 감춘다. PUT /posts/{postId} 는 파일을 다루지 않으며,
                // 기존 첨부는 게시글 상세 화면에서 개별 삭제로 관리한다(웹과 같다).
                if (vm.isEdit) {
                    Text(
                        "첨부파일은 게시글 상세 화면에서 개별 삭제로 관리합니다.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.bg)
                            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        fontSize = 12.5.sp,
                        color = colors.textMuted,
                    )
                } else {
                    FieldLabel("첨부파일")
                    FilePicker(
                        files = vm.files,
                        onPick = { pickFiles.launch(AttachmentFiles.ALLOWED_MIME_TYPES) },
                        onRemove = vm::removeFile,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                OutlineButton("취소", colors.textMuted, colors.border, height = 46.dp, onClick = onBack)
                Box(
                    Modifier
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bluePrimary)
                        .alpha(if (vm.loading) .7f else 1f)
                        .clickable(enabled = !vm.loading) { vm.submit(context) }
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when {
                            vm.loading && vm.isEdit -> "수정 중..."
                            vm.loading -> "등록 중..."
                            vm.isEdit -> "수정하기"
                            else -> "등록하기"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(selected: String?, onSelect: (String) -> Unit) {
    val colors = SafeLightTheme.colors
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WritableCategory.entries.forEach { option ->
            val on = selected == option.code
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) colors.bluePrimary else colors.bg)
                    .then(if (on) Modifier else Modifier.border(1.dp, colors.border, RoundedCornerShape(10.dp)))
                    .clickable { onSelect(option.code) }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
            ) {
                Text(
                    option.label,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) Color.White else colors.textMuted,
                )
            }
        }
    }
}

/** 글자 수를 오른쪽 아래에 붙인 입력칸. 웹의 `n / 100` 표시와 같은 자리다. */
@Composable
private fun CountedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    counter: String,
    singleLine: Boolean,
    minHeight: androidx.compose.ui.unit.Dp,
) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(11.dp))
            .background(colors.bg)
            .border(1.dp, colors.border, RoundedCornerShape(11.dp))
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = if (singleLine) 14.dp else 30.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = colors.textMuted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = colors.textStrong, lineHeight = 24.sp),
            cursorBrush = SolidColor(colors.bluePrimary),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            counter,
            modifier = Modifier.align(if (singleLine) Alignment.CenterEnd else Alignment.BottomEnd),
            fontSize = 12.sp,
            color = colors.textMuted,
        )
    }
}

/**
 * 웹의 드래그&드롭 자리. 안드로이드에는 드래그가 없으므로 눌러서 시스템 선택기를 여는 칸 하나로 둔다.
 * 선택기에 넘기는 MIME 목록도 백엔드 허용 목록과 같아서 아예 못 고르는 파일은 회색으로 보인다.
 */
@Composable
private fun FilePicker(
    files: List<PickedFile>,
    onPick: () -> Unit,
    onRemove: (PickedFile) -> Unit,
) {
    val colors = SafeLightTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bg)
            .border(1.5.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onPick)
            .padding(vertical = 30.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(SafeIcons.Paperclip, null, tint = colors.bluePrimary, modifier = Modifier.size(28.dp))
        Text("눌러서 파일 선택", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textStrong)
        Text(AttachmentFiles.ALLOWED_HINT, fontSize = 12.sp, color = colors.textMuted)
    }
    files.forEach { file ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.bg)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(start = 13.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(SafeIcons.Paperclip, null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
            Text(
                file.name,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onRemove(file) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SafeIcons.Close, "첨부 취소", tint = colors.danger, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    val colors = SafeLightTheme.colors
    Text(
        text,
        modifier = Modifier.padding(bottom = 10.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.textMuted,
    )
}

@Composable
private fun Hint(text: String) {
    val colors = SafeLightTheme.colors
    Text(text, modifier = Modifier.padding(top = 8.dp), fontSize = 12.5.sp, color = colors.textMuted)
}
