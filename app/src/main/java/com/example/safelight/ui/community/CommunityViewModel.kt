package com.example.safelight.ui.community

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.net.ApiEnvelope
import com.example.safelight.data.net.Network
import com.example.safelight.data.net.PostListDto
import com.example.safelight.data.net.PostPageInfoDto
import com.example.safelight.data.net.SafeLightApi
import com.example.safelight.data.net.errorMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import retrofit2.Response

private const val TAG = "Community"

/**
 * 탭 라벨 → 백엔드 카테고리. 웹 CommunityPage 의 CATEGORIES · CATEGORY_MAP 과 같은 순서다.
 * '안전 신고'는 커뮤니티 신고글(REPORT)이고 원클릭 긴급신고와는 무관하다.
 */
enum class PostTab(val label: String, val code: String?) {
    All("전체", null),
    Notice("공지", "NOTICE"),
    Info("정보", "INFO"),
    Question("질문", "QUESTION"),
    Report("안전 신고", "REPORT"),
    Tip("팁", "TIP"),
}

/**
 * 백엔드 PostService.buildSearchSort 가 받는 값 5종. 여기 없는 값을 보내면 조용히 latest 로 떨어진다.
 * [metric] 은 그 정렬의 근거가 되는 지표 — 목록에서 해당 숫자만 강조해 "왜 이 순서인지" 보이게 한다.
 */
enum class PostSort(val code: String, val label: String, val metric: PostMetric?) {
    Latest("latest", "최신순", null),
    Oldest("oldest", "오래된순", null),
    Views("views", "조회순", PostMetric.Views),
    Likes("likes", "좋아요순", PostMetric.Likes),
    Comments("comments", "댓글순", PostMetric.Comments),
}

enum class PostMetric { Views, Likes, Comments }

/** 사이드바 '카테고리별 인기글'에 그릴 순서. 웹 POPULAR_CATEGORIES 와 같다. */
private val POPULAR_CATEGORIES = listOf("NOTICE", "INFO", "QUESTION", "REPORT", "TIP")

/** buildSearchSort: likeCount DESC, 동점이면 createdAt DESC */
private const val POPULAR_SORT = "likes"

/** 한 페이지에 담는 글 수. 웹은 모바일에서 5개로 줄인다(데스크탑 10개). */
private const val PAGE_SIZE = 5

/** 인기글 카드에 카테고리마다 몇 줄까지 보일지. 웹 모바일과 같다(데스크탑은 5줄). */
const val POPULAR_ITEMS_PER_CATEGORY = 3

data class PopularGroup(val category: String, val items: List<PostListDto>)

/** 웹 CommunityPage 의 목록 상태를 옮긴 것이다. */
class CommunityViewModel : ViewModel() {

    private val api: SafeLightApi = Network.backend(SafeLightApi::class.java)

    var tab by mutableStateOf(PostTab.All)
        private set
    var sort by mutableStateOf(PostSort.Latest)
        private set
    var page by mutableStateOf(0)
        private set

    /** 입력 중인 검색어. 실제 조회는 '검색'을 눌렀을 때의 [searchKeyword] 로 한다. */
    var searchInput by mutableStateOf("")
    var searchKeyword by mutableStateOf("")
        private set

    var notices by mutableStateOf<List<PostListDto>>(emptyList())
        private set
    var posts by mutableStateOf<List<PostListDto>>(emptyList())
        private set
    var pageInfo by mutableStateOf<PostPageInfoDto?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    /** 조회 실패 사유. '글이 없음'과 구분해서 보여준다(비로그인이면 401 이라 목록이 빈다). */
    var error by mutableStateOf("")
        private set

    var popularGroups by mutableStateOf<List<PopularGroup>>(emptyList())
        private set
    var popularError by mutableStateOf("")
        private set

    init {
        load()
        loadPopular()
    }

    fun selectTab(next: PostTab) {
        if (next == tab) return
        tab = next
        page = 0
        // 카테고리를 바꾸면 검색은 푼다(웹 handleCategoryChange 와 같다).
        searchKeyword = ""
        searchInput = ""
        load()
    }

    /** 정렬을 바꾸면 페이지를 되돌린다 — 3페이지에서 기준만 바꾸면 엉뚱한 구간이 나온다. */
    fun selectSort(next: PostSort) {
        if (next == sort) return
        sort = next
        page = 0
        load()
    }

    fun selectPage(next: Int) {
        if (next == page) return
        page = next
        load()
    }

    fun submitSearch() {
        searchKeyword = searchInput
        page = 0
        load()
    }

    /** 글을 쓰거나 지우고 목록으로 돌아왔을 때. 보던 탭·정렬·페이지는 그대로 둔다. */
    fun refresh() {
        load()
        loadPopular()
    }

    private fun load() {
        loading = true
        viewModelScope.launch {
            runCatching { fetch() }
                .onSuccess { loading = false }
                .onFailure {
                    Log.e(TAG, "게시글 조회 실패", it)
                    error = "서버에 연결하지 못했습니다."
                    clearList()
                    loading = false
                }
        }
    }

    private suspend fun fetch() {
        // '전체' 탭만 응답 모양이 다르다(공지 3개가 따로 온다). 검색 중이면 카테고리와 무관하게 검색이 이긴다.
        val keyword = searchKeyword
        if (keyword.isNotBlank()) {
            apply(api.searchPosts(keyword, page, PAGE_SIZE, sort.code)) { data ->
                notices = emptyList()
                posts = data.items
                pageInfo = data.pageInfo
            }
            return
        }
        val code = tab.code
        if (code == null) {
            apply(api.getCommunity(page, PAGE_SIZE, sort.code)) { data ->
                // 공지 3개는 백엔드가 findTop3...로 고정한다 — 정렬은 그 아래 일반글에만 걸린다.
                notices = data.notices
                posts = data.items
                pageInfo = data.pageInfo
            }
            return
        }
        apply(api.getPostsByCategory(code, page, PAGE_SIZE, sort.code)) { data ->
            notices = emptyList()
            posts = data.items
            pageInfo = data.pageInfo
        }
    }

    private fun <T> apply(response: Response<ApiEnvelope<T>>, onData: (T) -> Unit) {
        val body = response.body()
        val data = body?.data
        if (!response.isSuccessful || body?.success != true || data == null) {
            error = if (response.isSuccessful) body?.message ?: "게시글을 불러오지 못했습니다."
            else response.errorMessage("게시글을 불러오지 못했습니다.")
            clearList()
            return
        }
        error = ""
        onData(data)
    }

    private fun clearList() {
        notices = emptyList()
        posts = emptyList()
        pageInfo = null
    }

    /**
     * 카테고리별 인기글. 카테고리마다 요청이 하나씩 필요해서(한 번에 여러 카테고리를 받는 엔드포인트가 없다)
     * 다섯 개를 한꺼번에 던진다 — 순차로 하면 카드가 다섯 번에 걸쳐 나눠 채워진다.
     *
     * /posts/summary 는 쓰지 않는다. 그쪽은 findTop5ByCategoryOrderByCreatedAtDesc 라 최신순이
     * 이름에 박혀 있어 좋아요순을 못 내고, NOTICE·QUESTION·INFO 세 개만 준다.
     */
    private fun loadPopular() {
        viewModelScope.launch {
            val results = POPULAR_CATEGORIES.map { cat ->
                async {
                    val response = runCatching {
                        api.getPostsByCategory(cat, 0, POPULAR_ITEMS_PER_CATEGORY, POPULAR_SORT)
                    }.getOrNull()
                    val body = response?.body()
                    if (response?.isSuccessful == true && body?.success == true && body.data != null) {
                        cat to body.data.items
                    } else {
                        cat to null
                    }
                }
            }.awaitAll()

            // 전부 실패했을 때만 사유를 띄운다(비로그인이면 다섯 개가 나란히 401 이다).
            // 일부만 실패하면 성공한 카테고리는 그대로 보여주는 게 낫다 — 카드 전체를 지울 이유가 없다.
            val ok = results.mapNotNull { (cat, items) -> items?.let { PopularGroup(cat, it) } }
            if (ok.isEmpty()) {
                popularError = "인기글을 불러오지 못했습니다."
                popularGroups = emptyList()
                return@launch
            }
            popularError = ""
            // 빈 카테고리는 배지만 덩그러니 남으므로 뺀다.
            popularGroups = ok.filter { it.items.isNotEmpty() }
        }
    }

    /** 글을 여는 순간 조회수를 올린다. 실패해도 화면은 그대로 연다(웹도 결과를 보지 않는다). */
    fun countView(postId: Long) {
        viewModelScope.launch { runCatching { api.increaseViewCount(postId) } }
    }
}
