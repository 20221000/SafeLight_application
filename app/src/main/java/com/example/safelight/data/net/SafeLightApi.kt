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
import retrofit2.http.PATCH
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

    /**
     * 지도에 보이는 범위의 CCTV.
     *
     * 예전에는 매개변수 없이 전건을 받았다(서울 CSV 4만 건, 5.7MB). 백엔드가 서울 CSV 대신
     * 전국 공공데이터를 쓰게 되면서 25만 건이 되어 전체 조회가 없어졌다 —
     * 범위를 안 보내면 400 이 온다. 위·경도 폭은 각각 0.5도까지만 받는다.
     */
    @GET("cctvs")
    suspend fun getCctvs(
        @Query("minLatitude") minLatitude: Double,
        @Query("maxLatitude") maxLatitude: Double,
        @Query("minLongitude") minLongitude: Double,
        @Query("maxLongitude") maxLongitude: Double,
    ): ApiEnvelope<List<CctvDto>>

    /**
     * 지도에 보이는 범위의 가로등(보안등). [getCctvs] 와 같은 규칙이고,
     * 백엔드가 주는 건 좌표뿐이다(LocationDto).
     *
     * 전국 데이터로 바뀌면서 184만 건이 되어 역시 전체 조회가 없다.
     */
    @GET("security-lights")
    suspend fun getSecurityLights(
        @Query("minLatitude") minLatitude: Double,
        @Query("maxLatitude") maxLatitude: Double,
        @Query("minLongitude") minLongitude: Double,
        @Query("maxLongitude") maxLongitude: Double,
    ): ApiEnvelope<List<LocationDto>>

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

    // ── 내 정보 ────────────────────────────────────────────────────────────────
    /**
     * 프로필. 이메일·전화·가입일에 더해 허위신고 횟수·블랙리스트 여부까지 한 번에 준다.
     * 로그인 당시 토큰에서 꺼내 둔 값과 달리 서버의 현재 값이라 관리자가 바꾼 상태도 보인다.
     */
    @GET("users/{userId}")
    suspend fun getProfile(@Path("userId") userId: Long): Response<ApiEnvelope<UserProfileDto>>

    @PUT("users/{userId}")
    suspend fun updateProfile(
        @Path("userId") userId: Long,
        @Body body: ProfileUpdateRequest,
    ): Response<ApiEnvelope<ProfileUpdatedDto>>

    @DELETE("users/{userId}")
    suspend fun withdraw(@Path("userId") userId: Long): Response<ApiEnvelope<JsonElement>>

    /** 내가 쓴 글. 목록 한 줄에 필요한 것만 오고 작성자 정보는 없다(전부 나다). */
    @GET("users/my/posts")
    suspend fun getMyPosts(): Response<ApiEnvelope<List<PostListDto>>>

    /**
     * 내 신고 내역. `users/my/reports` 도 같은 목록을 주지만 5개 필드로 줄어 있어서,
     * 좌표·위험도·인근 CCTV 까지 주는 이쪽을 쓴다(웹도 같다).
     */
    @GET("emergency-reports/my")
    suspend fun getMyReports(): Response<ApiEnvelope<List<EmergencyReportDto>>>

    /**
     * 긴급 신고(SOS). 신고자는 백엔드가 토큰에서 읽는다.
     *
     * 한 번 부르면 되돌릴 수 없다 — 신고가 남고, 반경 300m 위험구역이 생기거나 갱신되며,
     * 위치 공유를 허용한 친구들에게 알림이 나간다. 블랙리스트 사용자는 400 이다.
     */
    @POST("emergency-reports")
    suspend fun createEmergencyReport(
        @Body body: EmergencyReportCreateRequest,
    ): Response<ApiEnvelope<EmergencyReportDto>>

    @GET("messages/unread-count")
    suspend fun getUnreadMessageCount(): Response<ApiEnvelope<UnreadCountDto>>

    @GET("notifications/unread-count")
    suspend fun getUnreadNotificationCount(): Response<ApiEnvelope<UnreadCountDto>>

    // ── 친구 ──────────────────────────────────────────────────────────────────
    @GET("friends")
    suspend fun getFriends(): Response<ApiEnvelope<List<FriendDto>>>

    @GET("friends/requests/received")
    suspend fun getReceivedRequests(): Response<ApiEnvelope<List<ReceivedRequestDto>>>

    @GET("friends/requests/sent")
    suspend fun getSentRequests(): Response<ApiEnvelope<List<SentRequestDto>>>

    @POST("friends/requests")
    suspend fun sendFriendRequest(@Body body: FriendRequestBody): Response<ApiEnvelope<JsonElement>>

    @PUT("friends/requests/{requestId}/accept")
    suspend fun acceptFriendRequest(@Path("requestId") requestId: Long): Response<ApiEnvelope<JsonElement>>

    @PUT("friends/requests/{requestId}/reject")
    suspend fun rejectFriendRequest(@Path("requestId") requestId: Long): Response<ApiEnvelope<JsonElement>>

    @DELETE("friends/requests/{requestId}")
    suspend fun cancelFriendRequest(@Path("requestId") requestId: Long): Response<ApiEnvelope<JsonElement>>

    /** 경로 변수는 친구 관계 id 가 아니라 상대의 userId 다(백엔드 `/friends/{user_id}`). */
    @DELETE("friends/{friendUserId}")
    suspend fun deleteFriend(@Path("friendUserId") friendUserId: Long): Response<ApiEnvelope<JsonElement>>

    /** 본문 없이 보내면 "Required request body is missing" 400 이다 — 값은 필수다. */
    @PUT("friends/{friendsId}/emergency-allow")
    suspend fun setEmergencyAllow(
        @Path("friendsId") friendsId: Long,
        @Body body: EmergencyAllowRequest,
    ): Response<ApiEnvelope<FriendDto>>

    // ── 쪽지 ──────────────────────────────────────────────────────────────────
    @GET("messages/received")
    suspend fun getReceivedMessages(): Response<ApiEnvelope<List<ReceivedMessageDto>>>

    @GET("messages/sent")
    suspend fun getSentMessages(): Response<ApiEnvelope<List<SentMessageDto>>>

    /** 상세 조회에는 부수효과가 있다 — 받는 사람이 열면 그 쪽지가 읽음으로 바뀐다. */
    @GET("messages/{messageId}")
    suspend fun readMessage(@Path("messageId") messageId: Long): Response<ApiEnvelope<JsonElement>>

    @POST("messages/{receiverId}")
    suspend fun sendMessage(
        @Path("receiverId") receiverId: Long,
        @Body body: MessageSendRequest,
    ): Response<ApiEnvelope<JsonElement>>

    @DELETE("messages/{messageId}")
    suspend fun deleteMessage(@Path("messageId") messageId: Long): Response<ApiEnvelope<JsonElement>>

    // ── 알림 ──────────────────────────────────────────────────────────────────
    @GET("notifications")
    suspend fun getNotifications(): Response<ApiEnvelope<List<NotificationDto>>>

    @PATCH("notifications/{notificationId}/read")
    suspend fun markNotificationRead(
        @Path("notificationId") notificationId: Long,
    ): Response<ApiEnvelope<JsonElement>>

    /** 신고자가 위치 공유를 허용한 친구만 볼 수 있다. 아니면 403 과 사유가 온다. */
    @GET("emergency-reports/{reportId}/shared-location")
    suspend fun getSharedLocation(
        @Path("reportId") reportId: Long,
    ): Response<ApiEnvelope<SharedLocationDto>>

    // ── 관리자 ────────────────────────────────────────────────────────────────
    /** 전체 회원. 응답 한 줄이 [UserProfileDto] 와 같은 모양이라 그대로 쓴다. */
    @GET("users")
    suspend fun getAllUsers(): Response<ApiEnvelope<List<UserProfileDto>>>

    @GET("admin/dashboard/summary")
    suspend fun getDashboardSummary(): Response<ApiEnvelope<DashboardSummaryDto>>

    /**
     * 관리자 신고 목록. 상태·기간·키워드를 **서버가** 거른다.
     *
     * [status] 와 [isFalseReport] 는 같이 쓰지 않는다 — '허위'는 상태가 아니라 플래그로 거른다
     * (상태만 FALSE 로 바뀌는 경로가 따로 있어 플래그 쪽이 근거다).
     * [size] 는 최대 100 이고 넘기면 400 이다.
     * [startDate]·[endDate] 는 `2026-08-17T00:00:00` 형태여야 한다(날짜만 보내면 400).
     */
    @GET("admin/emergency-reports")
    suspend fun getAdminReports(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("status") status: String? = null,
        @Query("isFalseReport") isFalseReport: Boolean? = null,
        @Query("keyword") keyword: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
    ): Response<ApiEnvelope<AdminReportPageDto>>

    @PATCH("emergency-reports/{reportId}/status")
    suspend fun setReportStatus(
        @Path("reportId") reportId: Long,
        @Body body: ReportStatusRequest,
    ): Response<ApiEnvelope<EmergencyReportDto>>

    /** 신고자 벌점 +1, 누적 3회면 자동 블랙리스트. 위험구역 집계도 다시 돈다. */
    @PATCH("emergency-reports/{reportId}/false-report")
    suspend fun markFalseReport(
        @Path("reportId") reportId: Long,
    ): Response<ApiEnvelope<EmergencyReportDto>>

    /**
     * 허위신고 되돌리기. 이 경로로만 된다 —
     * `PATCH /status` 로 RECEIVED 를 보내면 서버가 400 이다(벌점·블랙리스트가 남기 때문).
     */
    @PATCH("emergency-reports/{reportId}/false-report/cancel")
    suspend fun cancelFalseReport(
        @Path("reportId") reportId: Long,
    ): Response<ApiEnvelope<EmergencyReportDto>>

    /** 자기 자신의 권한·블랙리스트는 백엔드가 막는다(SecurityException). 화면에서 미리 잠근다. */
    @PATCH("admin/users/{userId}/status")
    suspend fun setUserStatus(
        @Path("userId") userId: Long,
        @Body body: UserStatusRequest,
    ): Response<ApiEnvelope<UserStatusResultDto>>

    // 회원 정보 수정·삭제는 관리자도 같은 경로를 쓴다 — [updateProfile]·[withdraw] 를 그대로 부른다.

    @PATCH("danger-zones/{dangerZoneId}/level")
    suspend fun setDangerLevel(
        @Path("dangerZoneId") dangerZoneId: Long,
        @Body body: DangerLevelRequest,
    ): Response<ApiEnvelope<DangerZoneDto>>

    @PATCH("danger-zones/{dangerZoneId}/deactivate")
    suspend fun deactivateDangerZone(
        @Path("dangerZoneId") dangerZoneId: Long,
    ): Response<ApiEnvelope<DangerZoneDto>>

    /** 구역 상세에는 신고 목록이 없다. 이걸 따로 받아 합쳐야 신고 내역이 나온다. */
    @GET("danger-zones/{dangerZoneId}/reports")
    suspend fun getZoneReports(
        @Path("dangerZoneId") dangerZoneId: Long,
    ): Response<ApiEnvelope<List<EmergencyReportDto>>>

    /**
     * 공지 작성. 일반 글 경로로 우회하면 `isNotice=false` 로 저장돼 상단 공지에 뜨지 않는다.
     * 작성자는 백엔드가 토큰에서 읽는다.
     */
    @POST("posts/admin/notices")
    suspend fun createNotice(@Body body: NoticeCreateRequest): Response<ApiEnvelope<JsonElement>>

    @Multipart
    @POST("posts/admin/notices/with-files")
    suspend fun createNoticeWithFiles(
        @Part("title") title: RequestBody,
        @Part("content") content: RequestBody,
        @Part files: List<MultipartBody.Part>,
    ): Response<ApiEnvelope<JsonElement>>
}

@Serializable
data class CctvDto(
    val cctvId: Long = 0,
    val cctvName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val purpose: String = "",
)

/**
 * 위험구역 하나. 목록 API 는 아직 만료되지 않은 활성 구역만 내려준다.
 *
 * [expiredAt] 은 긴급신고로 만들어질 때 '생성 + 24시간' 으로 박히므로 늘 '자동 해제 예정 시각'이다
 * (관리자가 직접 비활성화하면 그 시각으로 바뀌고 목록에서 빠진다).
 * 중심 좌표는 백엔드가 소수 3자리로 반올림해 내려준다.
 */
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
    val reportCount: Int = 0,
    val createdAt: String? = null,
    val expiredAt: String? = null,
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
 * [safetyScore] 는 개수가 아니라 **가중 점수**다(백엔드 RouteService.calculateWeightedSafetyScore).
 *   CCTV×3 + 편의점×3 + 보안등×1 + 치안시설×4
 * 시설 5곳인 경로가 15로 나오므로 화면에는 '안전 점수 n점'이라고 적는다.
 * (치안시설은 아직 백엔드가 항상 0을 준다.)
 *
 * [securityLightLocations] 는 가로등(보안등)이다 — 2026-08-21 백엔드 PR #16 로 합계에 들어왔다.
 * 가로등을 주는 곳은 이 응답뿐이라(/cctvs 에 해당하는 전용 엔드포인트가 없다) 지도 화면이 아니라
 * 경로 화면에서만 그릴 수 있다.
 */
@Serializable
data class RouteDto(
    val routeId: Int = 0,
    val path: List<LocationDto> = emptyList(),
    val safetyScore: Int = 0,
    val description: String = "",
    val cctvLocations: List<LocationDto> = emptyList(),
    val storeLocations: List<LocationDto> = emptyList(),
    val securityLightLocations: List<LocationDto> = emptyList(),
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

/**
 * 프로필 조회 응답. 백엔드가 record 가 아니라 Map 으로 만들어 보내는 자리라
 * 키 이름은 `UserService.getProfile` 을 그대로 옮긴 것이다.
 */
@Serializable
data class UserProfileDto(
    val userId: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val email: String? = null,
    val phone: String? = null,
    /** USER · ADMIN */
    val role: String = "USER",
    val falseReportCount: Int = 0,
    val isBlacklisted: Boolean = false,
    val createdAt: String = "",
)

/**
 * 프로필 수정. 바뀐 것만 담아 보낸다 — null 인 필드는 아예 직렬화되지 않는다.
 * 백엔드는 값이 오면 그 필드를 덮어쓰므로, 안 바뀐 이메일을 같이 보내면 중복검사에 걸릴 여지가 있다.
 */
@Serializable
data class ProfileUpdateRequest(
    val nickname: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val password: String? = null,
)

@Serializable
data class ProfileUpdatedDto(
    val userId: Long = 0,
    val nickname: String = "",
    val email: String? = null,
    val phone: String? = null,
)

/**
 * 긴급 신고 한 건. 백엔드 EmergencyReportResponse.
 *
 * [reportStatus] 는 RECEIVED · RESOLVED · FALSE 뿐이다(PROCESSING 은 없다).
 * 허위 여부는 상태가 아니라 [isFalseReport] 로 따로 온다.
 */
@Serializable
data class EmergencyReportDto(
    val reportId: Long = 0,
    val userId: Long = 0,
    val nickname: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val reportStatus: String = "RECEIVED",
    val description: String? = null,
    val isFalseReport: Boolean = false,
    val dangerZoneId: Long? = null,
    /** 신고가 속한 위험구역의 등급 — HIGH · MEDIUM · LOW */
    val dangerLevel: String? = null,
    val nearestCctv: CctvDto? = null,
    val reportedAt: String = "",
)

/** 긴급 신고 접수. 위치는 필수이고 [description] 은 웹과 같이 '긴급 신고' 한 마디를 보낸다. */
@Serializable
data class EmergencyReportCreateRequest(
    val latitude: Double,
    val longitude: Double,
    val description: String? = null,
)

/** `{ unreadCount }` 한 줄짜리 응답. 쪽지·알림이 같은 모양이다. */
@Serializable
data class UnreadCountDto(
    val unreadCount: Int = 0,
)

/**
 * 친구 한 명. 긴급 위치 공유는 **방향이 둘**이라 값도 둘이다.
 *  - [isEmergencyAllowed] 내가 이 친구에게 내 위치를 보여주는지 (내가 바꿀 수 있는 값)
 *  - [isEmergencyAllowedByFriend] 이 친구가 나에게 자기 위치를 보여주는지 (읽기 전용)
 *
 * 뒤쪽이 꺼져 있으면 그 친구의 긴급 위치를 열어도 403 이다.
 */
@Serializable
data class FriendDto(
    val friendsId: Long = 0,
    val friendUserId: Long = 0,
    val friendNickname: String = "",
    val isEmergencyAllowed: Boolean = false,
    val isEmergencyAllowedByFriend: Boolean = false,
)

@Serializable
data class ReceivedRequestDto(
    val requestId: Long = 0,
    val senderId: Long = 0,
    val senderNickname: String = "",
)

@Serializable
data class SentRequestDto(
    val requestId: Long = 0,
    val receiverId: Long = 0,
    val receiverNickname: String = "",
)

/**
 * 친구 요청. 아이디(username)로만 보낸다 — 숫자 userId 는 사용자가 알 방법이 없다.
 * 두 필드를 같이 실으면 백엔드가 400 으로 거절하므로 [targetUserId] 는 늘 null 로 둔다.
 */
@Serializable
data class FriendRequestBody(
    val targetUsername: String,
    val targetUserId: Long? = null,
)

@Serializable
data class EmergencyAllowRequest(
    val isEmergencyAllowed: Boolean,
)

@Serializable
data class ReceivedMessageDto(
    val messageId: Long = 0,
    val senderId: Long = 0,
    val senderNickname: String = "",
    val content: String = "",
    val isRead: Boolean = false,
    val createdAt: String = "",
)

@Serializable
data class SentMessageDto(
    val messageId: Long = 0,
    val receiverId: Long = 0,
    val receiverNickname: String = "",
    val content: String = "",
    val isRead: Boolean = false,
    val createdAt: String = "",
)

@Serializable
data class MessageSendRequest(
    val content: String,
)

/** 친구의 긴급신고 알림. 백엔드 notifications 테이블은 신고에만 걸려 있어 쪽지 알림은 담기지 않는다. */
@Serializable
data class NotificationDto(
    val notificationId: Long = 0,
    val notificationType: String = "",
    val title: String = "",
    val message: String = "",
    val isRead: Boolean = false,
    val reportId: Long? = null,
    val reporterUserId: Long? = null,
    val reporterNickname: String = "",
    val createdAt: String = "",
)

@Serializable
data class SharedLocationDto(
    val reportId: Long = 0,
    val reporterUserId: Long = 0,
    val reporterNickname: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val description: String? = null,
    val reportStatus: String = "",
    val dangerZoneId: Long? = null,
    val dangerLevel: String? = null,
    val reportedAt: String = "",
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

// ── 관리자 ────────────────────────────────────────────────────────────────────

/** 게시글 집계. 사용자·구역 수는 각 목록 길이로도 세지만 게시글은 이것 말고는 셀 방법이 없다. */
@Serializable
data class DashboardSummaryDto(
    val totalPosts: Long = 0,
    val todayPosts: Long = 0,
    val totalUsers: Long = 0,
)

/** 관리자 신고 목록 한 장. [totalElements] 는 페이지와 무관한 전체 건수다. */
@Serializable
data class AdminReportPageDto(
    val reports: List<EmergencyReportDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val first: Boolean = true,
    val last: Boolean = true,
)

@Serializable
data class ReportStatusRequest(
    /** RECEIVED · RESOLVED */
    val reportStatus: String,
)

/** 권한과 블랙리스트를 한 경로로 바꾼다. 바꿀 쪽만 담고 나머지는 null 로 둔다. */
@Serializable
data class UserStatusRequest(
    val role: String? = null,
    val isBlacklisted: Boolean? = null,
)

/** [requiresRelogin] 이 true 면 대상자가 다시 로그인해야 토큰에 권한이 반영된다. */
@Serializable
data class UserStatusResultDto(
    val userId: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val role: String = "USER",
    val isBlacklisted: Boolean = false,
    val falseReportCount: Int = 0,
    val requiresRelogin: Boolean = false,
)

@Serializable
data class DangerLevelRequest(
    /** HIGH · MEDIUM · LOW */
    val dangerLevel: String,
)

@Serializable
data class NoticeCreateRequest(
    val title: String,
    val content: String,
)
