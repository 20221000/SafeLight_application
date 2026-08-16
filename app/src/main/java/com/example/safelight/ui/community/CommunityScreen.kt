package com.example.safelight.ui.community

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safelight.data.net.PostListDto
import com.example.safelight.ui.icon.SafeIcons
import com.example.safelight.ui.theme.SafeLightTheme

/**
 * 웹 CommunityPage.jsx 의 모바일 화면.
 *
 * 데스크탑의 우측 280px 컬럼(카테고리별 인기글)은 웹 모바일과 같이 피드 아래로 내려오고,
 * 글쓰기 버튼도 웹 모바일과 같이 떠 있는 원형 버튼이다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    loggedIn: Boolean,
    onOpenPost: (Long) -> Unit,
    onWrite: () -> Unit,
    onGoLogin: () -> Unit,
    modifier: Modifier = Modifier,
    vm: CommunityViewModel,
) {
    val colors = SafeLightTheme.colors
    var sortOpen by remember { mutableStateOf(false) }
    val sortSheet = rememberModalBottomSheetState()

    Box(modifier.fillMaxSize().background(colors.bg)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            // 아래 여백은 떠 있는 글쓰기 버튼이 마지막 줄을 가리지 않게 하는 몫이다(웹 84px 과 같다).
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 84.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Column(Modifier.padding(bottom = 16.dp)) {
                    Text("커뮤니티", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = colors.textStrong)
                    Text(
                        "동네 안전 정보를 나누는 공간",
                        fontSize = 13.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            item {
                SearchBar(
                    value = vm.searchInput,
                    onValueChange = { vm.searchInput = it },
                    onSubmit = vm::submitSearch,
                )
            }

            item { CategoryChips(active = vm.tab, onSelect = vm::selectTab) }

            // 공지 — 최신 3개까지. 백엔드 getCommunity 도 3개만 내려주는 의도된 동작이다.
            items(vm.notices.take(3), key = { "notice-${it.postId}" }) { notice ->
                NoticeRow(notice, onClick = { onOpenPost(notice.postId) })
            }

            item {
                SortBar(
                    totalElements = vm.pageInfo?.totalElements,
                    sort = vm.sort,
                    open = sortOpen,
                    onToggle = { sortOpen = !sortOpen },
                    topPadding = if (vm.notices.isEmpty()) 0.dp else 14.dp,
                )
            }

            when {
                vm.loading -> item { CenterNote("불러오는 중...", verticalPadding = 60.dp) }

                vm.posts.isNotEmpty() -> items(vm.posts, key = { it.postId }) { post ->
                    PostRow(post, metric = vm.sort.metric, onClick = { onOpenPost(post.postId) })
                }

                // 조회 실패는 '글이 없음'과 다르다 — 사유를 보여주고, 로그인 문제면 바로 갈 수 있게 한다.
                vm.error.isNotBlank() -> item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 52.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(SafeIcons.AlertTriangle, null, tint = colors.warning, modifier = Modifier.size(22.dp))
                        Text(
                            vm.error,
                            fontSize = 13.5.sp,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (!loggedIn) {
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

                else -> item { CenterNote("게시글이 없습니다.", verticalPadding = 60.dp) }
            }

            val info = vm.pageInfo
            if (info != null && info.totalPages > 1) {
                item { Pagination(page = vm.page, totalPages = info.totalPages, onSelect = vm::selectPage) }
            }

            item { PopularCard(vm.popularGroups, vm.popularError, onOpenPost) }
        }

        // 글쓰기 — 웹 모바일의 플로팅 버튼. 웹은 탭바 높이(72px)만큼 띄우지만 여기는 본문 영역이
        // 이미 탭바 위에서 끝나므로 화면 아래 여백만 준다.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.bluePrimary)
                .clickable { if (loggedIn) onWrite() else onGoLogin() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(SafeIcons.Plus, "글쓰기", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }

    // 정렬 — 웹 모바일은 팝오버 대신 액션시트를 쓴다(좁은 화면에서 팝오버가 잘리고 터치 타깃도 작다).
    if (sortOpen) {
        ModalBottomSheet(
            onDismissRequest = { sortOpen = false },
            sheetState = sortSheet,
            containerColor = colors.surface,
        ) {
            Text(
                "정렬",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textMuted,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
            )
            PostSort.entries.forEach { option ->
                val on = option == vm.sort
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.selectSort(option)
                            sortOpen = false
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option.label,
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) colors.bluePrimary else colors.textStrong,
                    )
                    if (on) Icon(SafeIcons.Check, null, tint = colors.bluePrimary, modifier = Modifier.size(18.dp))
                }
            }
            // 시트 아래쪽 제스처 바 자리.
            Box(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SearchBar(value: String, onValueChange: (String) -> Unit, onSubmit: () -> Unit) {
    val colors = SafeLightTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(SafeIcons.Search, null, tint = colors.textMuted, modifier = Modifier.size(17.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("게시글 검색...", fontSize = 14.sp, color = colors.textMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = colors.textStrong),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.bluePrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            "검색",
            modifier = Modifier.clickable(onClick = onSubmit),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = colors.bluePrimary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(active: PostTab, onSelect: (PostTab) -> Unit) {
    val colors = SafeLightTheme.colors
    // 들어가는 폭에서는 한 줄, 안 들어가면 다음 줄로 내린다. 가로 스크롤은 쓰지 않는다 —
    // 스크롤바가 없으면 화면 밖 칩을 발견할 방법이 없다(웹과 같은 판단).
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PostTab.entries.forEach { tab ->
            val on = tab == active
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) colors.bluePrimary else colors.surface)
                    .then(if (on) Modifier else Modifier.border(1.dp, colors.border, RoundedCornerShape(20.dp)))
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            ) {
                Text(
                    tab.label,
                    fontSize = 12.5.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) Color.White else colors.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun NoticeRow(notice: PostListDto, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 왼쪽 파란 굵은 선(웹 borderLeft: 4px solid).
        Box(Modifier.width(4.dp).height(48.dp).background(colors.bluePrimary))
        Row(
            Modifier.weight(1f).padding(start = 12.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryBadge("NOTICE")
            Text(
                notice.title,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(notice.createdAt.toDateOnly(), fontSize = 12.sp, color = colors.textMuted, maxLines = 1)
        }
    }
}

@Composable
private fun SortBar(
    totalElements: Long?,
    sort: PostSort,
    open: Boolean,
    onToggle: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    val colors = SafeLightTheme.colors
    Column(Modifier.padding(top = topPadding)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (totalElements == null) "" else "전체 ${totalElements}개",
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                color = colors.textMuted,
            )
            Row(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(1.dp, if (open) colors.bluePrimary else colors.border, RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    sort.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (open) colors.bluePrimary else colors.textStrong,
                )
                Icon(
                    SafeIcons.ChevronDown,
                    null,
                    tint = (if (open) colors.bluePrimary else colors.textStrong).copy(alpha = .7f),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun PostRow(post: PostListDto, metric: PostMetric?, onClick: () -> Unit) {
    val colors = SafeLightTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CategoryBadge(post.category)
            Text(
                post.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 정렬 기준이 된 지표만 파랑·굵게로 올린다 — 목록만 보고 왜 이 순서인지 알 수 있게.
        Row(
            Modifier.padding(top = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(post.nickname, fontSize = 12.sp, color = colors.textMuted, maxLines = 1)
            Text(post.createdAt.toDateOnly(), fontSize = 12.sp, color = colors.textMuted, maxLines = 1)
            MetricText(SafeIcons.Eye, post.viewCount, highlighted = metric == PostMetric.Views)
            MetricText(SafeIcons.Heart, post.likeCount, highlighted = metric == PostMetric.Likes)
            MetricText(SafeIcons.Message, post.commentCount, highlighted = metric == PostMetric.Comments)
        }
    }
}

@Composable
private fun MetricText(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    highlighted: Boolean,
) {
    val colors = SafeLightTheme.colors
    val color = if (highlighted) colors.bluePrimary else colors.textMuted
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        Text(
            "$count",
            fontSize = 12.sp,
            color = color,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Pagination(page: Int, totalPages: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageButton("‹", enabled = page > 0) { onSelect((page - 1).coerceAtLeast(0)) }
        repeat(totalPages) { n ->
            PageButton("${n + 1}", active = n == page) { onSelect(n) }
        }
        PageButton("›", enabled = page < totalPages - 1) { onSelect((page + 1).coerceAtMost(totalPages - 1)) }
    }
}

@Composable
private fun PageButton(
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier
            .widthIn(min = 32.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) colors.bluePrimary else colors.surface)
            .then(if (active) Modifier else Modifier.border(1.dp, colors.border, RoundedCornerShape(8.dp)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = (if (active) Color.White else colors.textMuted).copy(alpha = if (enabled) 1f else .5f),
        )
    }
}

/**
 * 카테고리별 인기글. 데스크탑에서는 우측 컬럼이고 모바일에서는 피드 아래로 내려오므로,
 * 웹과 같이 얇은 가로선으로 게시글 영역과 구분한다.
 */
@Composable
private fun PopularCard(groups: List<PopularGroup>, error: String, onOpenPost: (Long) -> Unit) {
    val colors = SafeLightTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(SafeIcons.Flame, null, tint = colors.bluePrimary, modifier = Modifier.size(15.dp))
                Text("카테고리별 인기글", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textStrong)
            }
            if (groups.isEmpty()) {
                CenterNote(
                    error.ifBlank { "아직 등록된 글이 없습니다." },
                    verticalPadding = 12.dp,
                    fontSize = 12f,
                )
                return@Column
            }
            groups.forEach { group ->
                Column(Modifier.padding(top = 16.dp)) {
                    CategoryBadge(group.category)
                    group.items.forEach { post ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 9.dp)
                                .clickable { onOpenPost(post.postId) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                post.title,
                                modifier = Modifier.weight(1f),
                                fontSize = 12.5.sp,
                                color = colors.textStrong,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // 조회수가 아니라 좋아요를 보여준다 — 이 카드의 순서를 만든 지표가 그것이라
                            // 숫자가 내림차순으로 읽혀야 "왜 이 순서인지"가 보인다.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(SafeIcons.Heart, null, tint = colors.textMuted, modifier = Modifier.size(11.dp))
                                Text("${post.likeCount}", fontSize = 11.sp, color = colors.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CenterNote(
    text: String,
    verticalPadding: androidx.compose.ui.unit.Dp,
    fontSize: Float = 13.5f,
) {
    val colors = SafeLightTheme.colors
    Box(
        Modifier.fillMaxWidth().padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = fontSize.sp, color = colors.textMuted)
    }
}
