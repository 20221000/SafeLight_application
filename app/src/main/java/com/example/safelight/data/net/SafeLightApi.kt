package com.example.safelight.data.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * 우리 Spring 백엔드. 응답은 모두 {success, data, message} 봉투에 들어온다([ApiEnvelope]).
 *
 * 웹의 대응 관계:
 *  - MapView.jsx 의 `fetch('/cctvs')`             → [getCctvs]
 *  - useSafetyData.js 의 `fetch('/danger-zones')` → [getDangerZones]
 *  - RoutePage.jsx 의 `fetch('/routes', POST)`    → [getRoutes]
 *  - RoutePage.jsx 의 `/bookmarks`·`/recent-routes` → 아래 나머지
 *
 * 실패 사유를 사용자에게 그대로 보여줘야 하는 것들은 [Response] 로 받는다 —
 * Retrofit 이 4xx 를 예외로 바꿔 버리면 봉투 안의 message 를 읽을 수 없다([errorMessage]).
 */
interface SafeLightApi {

    @GET("cctvs")
    suspend fun getCctvs(): ApiEnvelope<List<CctvDto>>

    /** 토큰이 없으면 빈 목록이 온다(웹도 토큰이 없으면 아예 요청하지 않는다). */
    @GET("danger-zones")
    suspend fun getDangerZones(): ApiEnvelope<List<DangerZoneDto>>

    /**
     * 안전 경로 탐색. 백엔드가 안전 점수 상위 경로를 최대 3개 돌려준다(개수는 백엔드가 정한다).
     * 로그인 상태면 백엔드가 이 검색을 최근 경로에 알아서 저장한다 — 앱이 따로 저장하지 않는다.
     */
    @POST("routes")
    suspend fun getRoutes(@Body body: RouteRequest): Response<ApiEnvelope<List<RouteDto>>>

    @GET("bookmarks")
    suspend fun getBookmarks(): ApiEnvelope<List<BookmarkDto>>

    @POST("bookmarks")
    suspend fun saveBookmark(@Body body: BookmarkRequest): Response<ApiEnvelope<BookmarkDto>>

    @DELETE("bookmarks/{id}")
    suspend fun deleteBookmark(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @GET("recent-routes")
    suspend fun getRecentRoutes(): ApiEnvelope<List<RouteHistoryDto>>

    @DELETE("recent-routes/{routeHistoryId}")
    suspend fun deleteRecentRoute(@Path("routeHistoryId") id: Long): Response<ApiEnvelope<JsonElement>>

    @DELETE("recent-routes/all")
    suspend fun deleteAllRecentRoutes(): Response<ApiEnvelope<JsonElement>>

    // ── 커뮤니티 ────────────────────────────────────────────────────────────────
    // 목록은 셋 다 같은 화면을 채운다. 전체 탭은 공지 3개가 따로 오므로 응답 모양만 다르다.
    //
    // [sort] 는 백엔드 PostService.buildSearchSort 가 아는 다섯 값뿐이다
    // (latest · oldest · views · likes · comments). 여기 없는 값은 조용히 latest 로 떨어진다.

    @GET("posts/community")
    suspend fun getCommunity(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort") sort: String,
    ): Response<ApiEnvelope<CommunityPostsDto>>

    @GET("posts")
    suspend fun getPostsByCategory(
        @Query("category") category: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort") sort: String,
    ): Response<ApiEnvelope<PostListPageDto>>

    @GET("posts/search")
    suspend fun searchPosts(
        @Query("keyword") keyword: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort") sort: String,
    ): Response<ApiEnvelope<PostListPageDto>>

    @GET("posts/{postId}")
    suspend fun getPost(@Path("postId") postId: Long): Response<ApiEnvelope<PostDetailDto>>

    /** 조회수 증가. 웹도 목록에서 글을 열기 직전에 한 번 부른다. */
    @POST("posts/{postId}/view")
    suspend fun increaseViewCount(@Path("postId") postId: Long): Response<ApiEnvelope<JsonElement>>

    @POST("posts")
    suspend fun createPost(@Body body: PostCreateRequest): Response<ApiEnvelope<JsonElement>>

    /**
     * 첨부파일이 있는 글. 제목·내용·카테고리도 JSON 이 아니라 multipart 조각으로 간다
     * (백엔드가 @RequestParam 으로 받는다).
     */
    @Multipart
    @POST("posts/with-files")
    suspend fun createPostWithFiles(
        @Part("title") title: RequestBody,
        @Part("content") content: RequestBody,
        @Part("category") category: RequestBody,
        @Part files: List<MultipartBody.Part>,
    ): Response<ApiEnvelope<JsonElement>>

    /**
     * 첨부파일 내려받기. 봉투가 아니라 파일 바이트가 그대로 온다.
     * [Streaming] 이 없으면 Retrofit 이 파일 전체를 메모리에 올린 뒤에 넘겨준다.
     */
    @Streaming
    @GET("posts/attachments/{attachmentId}")
    suspend fun downloadAttachment(@Path("attachmentId") attachmentId: Long): Response<ResponseBody>

    /** 글 작성자만 지울 수 있다(백엔드 PostAttachmentService). */
    @DELETE("posts/attachments/{attachmentId}")
    suspend fun deleteAttachment(@Path("attachmentId") attachmentId: Long): Response<ApiEnvelope<JsonElement>>

    @PUT("posts/{postId}")
    suspend fun updatePost(
        @Path("postId") postId: Long,
        @Body body: PostUpdateRequest,
    ): Response<ApiEnvelope<JsonElement>>

    @DELETE("posts/{postId}")
    suspend fun deletePost(@Path("postId") postId: Long): Response<ApiEnvelope<JsonElement>>

    @POST("posts/{postId}/comments")
    suspend fun createComment(
        @Path("postId") postId: Long,
        @Body body: CommentCreateRequest,
    ): Response<ApiEnvelope<JsonElement>>

    @PUT("posts/{postId}/comments/{commentId}")
    suspend fun updateComment(
        @Path("postId") postId: Long,
        @Path("commentId") commentId: Long,
        @Body body: CommentUpdateRequest,
    ): Response<ApiEnvelope<JsonElement>>

    @DELETE("posts/{postId}/comments/{commentId}")
    suspend fun deleteComment(
        @Path("postId") postId: Long,
        @Path("commentId") commentId: Long,
    ): Response<ApiEnvelope<JsonElement>>

    @POST("posts/{postId}/likes")
    suspend fun likePost(@Path("postId") postId: Long): Response<ApiEnvelope<JsonElement>>

    @DELETE("posts/{postId}/likes")
    suspend fun unlikePost(@Path("postId") postId: Long): Response<ApiEnvelope<JsonElement>>
}

@Serializable
data class CctvDto(
    val cctvId: Long = 0,
    val cctvName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val purpose: String = "",
)

@Serializable
data class DangerZoneDto(
    val dangerZoneId: Long = 0,
    val centerLatitude: Double = 0.0,
    val centerLongitude: Double = 0.0,
    /** 미터 */
    val radius: Double = 0.0,
    /** HIGH · MEDIUM · LOW */
    val dangerLevel: String = "LOW",
    val isActive: Boolean = true,
)

/** 위경도 한 쌍. 백엔드 LocationDto 와 같다(경로 좌표·시설 좌표에 두루 쓰인다). */
@Serializable
data class LocationDto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

@Serializable
data class RouteRequest(
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double,
    val endLongitude: Double,
)

/**
 * 탐색된 경로 하나.
 *
 * [safetyScore] 는 CCTV 수 + 편의점 수의 합이다(백엔드 RouteService.analyzeSafetyData).
 * 'CCTV 개수'가 아니므로 화면에서도 '안전시설 n곳'이라고 적는다.
 */
@Serializable
data class RouteDto(
    val routeId: Int = 0,
    val path: List<LocationDto> = emptyList(),
    val safetyScore: Int = 0,
    val description: String = "",
    val cctvLocations: List<LocationDto> = emptyList(),
    val storeLocations: List<LocationDto> = emptyList(),
)

@Serializable
data class BookmarkDto(
    val id: Long = 0,
    val routeName: String = "",
    val startLatitude: Double = 0.0,
    val startLongitude: Double = 0.0,
    val endLatitude: Double = 0.0,
    val endLongitude: Double = 0.0,
    val safetyScore: Int = 0,
)

@Serializable
data class BookmarkRequest(
    val routeName: String,
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double,
    val endLongitude: Double,
    val safetyScore: Int,
)

/**
 * 최근 검색 경로. 백엔드가 자동 저장할 때 이름을 '최근 검색 경로'로 고정하므로
 * 목록에서는 도착지 좌표를 역지오코딩한 주소를 대신 보여준다(웹도 같다).
 *
 * [searchedAt] 은 `2026-08-06T08:35:12` 형태의 문자열이다.
 */
@Serializable
data class RouteHistoryDto(
    val routeHistoryId: Long = 0,
    val routeName: String = "",
    val startLatitude: Double = 0.0,
    val startLongitude: Double = 0.0,
    val endLatitude: Double = 0.0,
    val endLongitude: Double = 0.0,
    val searchedAt: String = "",
)

/** 목록 한 줄. 백엔드 PostListResponse. [createdAt] 은 `2026-08-16T08:35:12` 형태다. */
@Serializable
data class PostListDto(
    val postId: Long = 0,
    val title: String = "",
    /** NOTICE · INFO · QUESTION · REPORT · TIP */
    val category: String = "",
    val userId: Long = 0,
    val nickname: String = "",
    val viewCount: Int = 0,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val createdAt: String = "",
)

@Serializable
data class PostPageInfoDto(
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

@Serializable
data class PostListPageDto(
    val items: List<PostListDto> = emptyList(),
    val pageInfo: PostPageInfoDto? = null,
)

/** '전체' 탭 전용 응답. [notices] 는 정렬과 무관하게 항상 최신 공지 3개다(백엔드가 그렇게 고정한다). */
@Serializable
data class CommunityPostsDto(
    val notices: List<PostListDto> = emptyList(),
    val items: List<PostListDto> = emptyList(),
    val pageInfo: PostPageInfoDto? = null,
)

@Serializable
data class PostDetailDto(
    val postId: Long = 0,
    val title: String = "",
    val content: String = "",
    val category: String = "",
    val userId: Long = 0,
    val nickname: String = "",
    val viewCount: Int = 0,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val isLiked: Boolean = false,
    val createdAt: String = "",
    val comments: List<CommentDto> = emptyList(),
    val attachments: List<AttachmentDto> = emptyList(),
)

@Serializable
data class CommentDto(
    val commentId: Long = 0,
    val userId: Long = 0,
    val nickname: String = "",
    val content: String = "",
    val parentId: Long? = null,
    val createdAt: String = "",
    val replies: List<CommentDto> = emptyList(),
)

@Serializable
data class AttachmentDto(
    val attachmentId: Long = 0,
    val originalFilename: String = "",
    val contentType: String = "",
    val size: Long = 0,
)

@Serializable
data class PostCreateRequest(
    val title: String,
    val content: String,
    val category: String,
)

/**
 * 수정 요청. [category] 가 null 이면 아예 보내지 않는다(kotlinx 는 기본값과 같은 필드를 빼고 직렬화한다) —
 * 백엔드 updatePost 는 category 가 null 이 아니면 그대로 덮어써서, 값을 모른 채 보내면
 * 질문·팁·안전신고 글이 조용히 바뀐다. 웹 PostWritePage 도 같은 이유로 빼고 보낸다.
 */
@Serializable
data class PostUpdateRequest(
    val title: String,
    val content: String,
    val category: String? = null,
)

/** [parentId] 가 null 이면 댓글, 값이 있으면 그 댓글의 답글이다. */
@Serializable
data class CommentCreateRequest(
    val content: String,
    val parentId: Long? = null,
)

@Serializable
data class CommentUpdateRequest(
    val content: String,
)
