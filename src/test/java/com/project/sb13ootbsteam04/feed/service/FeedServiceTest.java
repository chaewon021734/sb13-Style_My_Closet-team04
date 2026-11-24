
package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.clothes.repository.ClothesRepository;
import com.project.sb13ootbsteam04.feed.dto.CreateFeedRequest;
import com.project.sb13ootbsteam04.feed.dto.FeedPageResponse;
import com.project.sb13ootbsteam04.feed.dto.FeedResponse;
import com.project.sb13ootbsteam04.feed.dto.UpdateFeedRequest;
import com.project.sb13ootbsteam04.feed.entity.Feed;
import com.project.sb13ootbsteam04.feed.repository.CommentRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedClothesRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedLikeRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import com.project.sb13ootbsteam04.weather.entity.PrecipitationType;
import com.project.sb13ootbsteam04.weather.entity.SkyStatus;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedClothesRepository feedClothesRepository;

    @Mock
    private FeedLikeRepository feedLikeRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FeedResponseService feedResponseService;

    @Mock
    private ClothesRepository clothesRepository;

    @Mock
    private WeatherRepository weatherRepository;

    @InjectMocks
    private FeedService feedService;

    private final UUID userId = UUID.randomUUID();
    private final UUID feedId = UUID.randomUUID();
    private final UUID weatherId = UUID.randomUUID();

    // ========================================
    // 피드 생성 테스트
    // ========================================

    @Test
    void 피드_생성시_로그인하지_않으면_예외() {
        assertThrows(
                AccessDeniedException.class,
                () -> feedService.createFeed(null, null)
        );
    }

    @Test
    void 피드_생성시_요청이_null이면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.createFeed(null, userId)
        );
    }

    @Test
    void 피드_생성시_다른_사용자이면_예외() {
        CreateFeedRequest request = mock(CreateFeedRequest.class);

        when(request.authorId()).thenReturn(UUID.randomUUID());

        assertThrows(
                AccessDeniedException.class,
                () -> feedService.createFeed(request, userId)
        );
    }

    @Test
    void 피드_생성시_날씨가_없으면_예외() {
        CreateFeedRequest request = mock(CreateFeedRequest.class);

        when(request.authorId()).thenReturn(userId);

        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.createFeed(request, userId)
        );
    }

    @Test
    void 피드_생성시_존재하지_않는_날씨이면_예외() {
        CreateFeedRequest request = mock(CreateFeedRequest.class);

        when(request.authorId()).thenReturn(userId);
        when(request.weatherId()).thenReturn(weatherId);
        when(weatherRepository.existsById(weatherId)).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.createFeed(request, userId)
        );
    }

    @Test
    void 피드_생성시_의상목록이_없으면_예외() {
        CreateFeedRequest request = mock(CreateFeedRequest.class);

        when(request.authorId()).thenReturn(userId);
        when(request.weatherId()).thenReturn(weatherId);
        when(weatherRepository.existsById(weatherId)).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.createFeed(request, userId)
        );
    }

    @Test
    void 피드_생성시_내용이_비어있으면_예외() {
        CreateFeedRequest request = mock(CreateFeedRequest.class);

        when(request.authorId()).thenReturn(userId);
        when(request.weatherId()).thenReturn(weatherId);
        when(request.clothesIds()).thenReturn(List.of());
        when(request.content()).thenReturn(" ");

        when(weatherRepository.existsById(weatherId)).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.createFeed(request, userId)
        );
    }

    @Test
    void 피드_생성시_사용자가_존재하지_않으면_예외() {
        CreateFeedRequest request = mock(CreateFeedRequest.class);

        when(request.authorId()).thenReturn(userId);
        when(request.weatherId()).thenReturn(weatherId);
        when(request.clothesIds()).thenReturn(List.of());
        when(request.content()).thenReturn("테스트 피드");

        when(weatherRepository.existsById(weatherId)).thenReturn(true);
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.createFeed(request, userId)
        );
    }

    // ========================================
    // 피드 수정 테스트
    // ========================================

    @Test
    void 피드_수정_성공() {
        Feed feed = new Feed(userId, weatherId, "기존 내용");
        UpdateFeedRequest request = mock(UpdateFeedRequest.class);

        when(request.content()).thenReturn("수정된 내용");
        when(feedRepository.findById(feedId))
                .thenReturn(Optional.of(feed));

        feedService.updateFeed(feedId, request, userId);

        assertEquals("수정된 내용", feed.getContent());
    }

    @Test
    void 피드_수정시_로그인하지_않으면_예외() {
        assertThrows(
                AccessDeniedException.class,
                () -> feedService.updateFeed(feedId, null, null)
        );
    }

    @Test
    void 피드_수정시_존재하지_않는_피드이면_예외() {
        when(feedRepository.findById(feedId))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.updateFeed(feedId, null, userId)
        );
    }

    @Test
    void 피드_수정시_작성자가_아니면_예외() {
        Feed feed = new Feed(
                UUID.randomUUID(),
                weatherId,
                "기존 내용"
        );

        when(feedRepository.findById(feedId))
                .thenReturn(Optional.of(feed));

        assertThrows(
                AccessDeniedException.class,
                () -> feedService.updateFeed(feedId, null, userId)
        );
    }

    @Test
    void 피드_수정시_내용이_없으면_예외() {
        Feed feed = new Feed(userId, weatherId, "기존 내용");

        when(feedRepository.findById(feedId))
                .thenReturn(Optional.of(feed));

        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.updateFeed(feedId, null, userId)
        );
    }

    // ========================================
    // 피드 삭제 테스트
    // ========================================

    @Test
    void 피드_삭제_성공() {
        Feed feed = new Feed(userId, weatherId, "삭제할 피드");

        when(feedRepository.findById(feedId))
                .thenReturn(Optional.of(feed));

        feedService.deleteFeed(feedId, userId);

        verify(feedClothesRepository).deleteByFeed_Id(feedId);
        verify(feedLikeRepository).deleteAllByFeedId(feedId);
        verify(commentRepository).deleteAllByFeedId(feedId);
        verify(feedRepository).delete(feed);
    }

    @Test
    void 피드_삭제시_로그인하지_않으면_예외() {
        assertThrows(
                AccessDeniedException.class,
                () -> feedService.deleteFeed(feedId, null)
        );
    }

    @Test
    void 피드_삭제시_존재하지_않으면_예외() {
        when(feedRepository.findById(feedId))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.deleteFeed(feedId, userId)
        );
    }

    @Test
    void 피드_삭제시_작성자가_아니면_예외() {
        Feed feed = new Feed(
                UUID.randomUUID(),
                weatherId,
                "다른 사람의 피드"
        );

        when(feedRepository.findById(feedId))
                .thenReturn(Optional.of(feed));

        assertThrows(
                AccessDeniedException.class,
                () -> feedService.deleteFeed(feedId, userId)
        );

        verify(feedRepository, never()).delete(any(Feed.class));
    }

    // ========================================
    // 목록 조회 파라미터 검증
    // ========================================

    @Test
    void 조회시_limit이_0이면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.getFeedPage(
                        null, null, 0,
                        "createdAt", "DESCENDING",
                        null, null
                )
        );
    }

    @Test
    void 조회시_limit이_101이면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.getFeedPage(
                        null, null, 101,
                        "createdAt", "DESCENDING",
                        null, null
                )
        );
    }

    @Test
    void 조회시_정렬기준이_잘못되면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.getFeedPage(
                        null, null, 10,
                        "invalid", "DESCENDING",
                        null, null
                )
        );
    }

    @Test
    void 조회시_정렬방향이_잘못되면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.getFeedPage(
                        null, null, 10,
                        "createdAt", "INVALID",
                        null, null
                )
        );
    }

    @Test
    void 조회시_cursor만_전달하면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.getFeedPage(
                        Instant.now().toString(), null, 10,
                        "createdAt", "DESCENDING",
                        null, null
                )
        );
    }

    @Test
    void 조회시_idAfter만_전달하면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.getFeedPage(
                        null, feedId, 10,
                        "createdAt", "DESCENDING",
                        null, null
                )
        );
    }

    @Test
    void 조회시_날씨상태가_잘못되면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.getFeedPage(
                        null, null, 10,
                        "createdAt", "DESCENDING",
                        null, "INVALID", null,
                        null, userId
                )
        );
    }

    @Test
    void 조회시_강수형태가_잘못되면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.getFeedPage(
                        null, null, 10,
                        "createdAt", "DESCENDING",
                        null, "CLEAR", "INVALID",
                        null, userId
                )
        );
    }

    @Test
    void 조회시_날짜커서가_잘못되면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.getFeedPage(
                        "invalid", feedId, 10,
                        "createdAt", "DESCENDING",
                        null, null
                )
        );
    }

    @Test
    void 조회시_좋아요커서가_잘못되면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.getFeedPage(
                        "invalid", feedId, 10,
                        "likeCount", "DESCENDING",
                        null, null
                )
        );
    }

    @Test
    void 조회시_음수_좋아요커서이면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.getFeedPage(
                        "-1|2026-11-23T01:24:44Z",
                        feedId, 10,
                        "likeCount", "ASCENDING",
                        null, null
                )
        );
    }

    // ========================================
    // 목록 조회 성공 테스트
    // ========================================

    @Test
    void 작성일_내림차순_첫페이지_조회() {
        when(feedRepository.findFilteredPageDescending(
                isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), any(Pageable.class)
        )).thenReturn(List.of());

        when(feedRepository.countFilteredFeeds(
                isNull(), isNull(), isNull(), isNull()
        )).thenReturn(0L);

        FeedPageResponse result = feedService.getFeedPage(
                null, null, 10,
                "createdAt", "DESCENDING",
                null, null
        );

        assertNotNull(result);
        assertTrue(result.data().isEmpty());
        assertFalse(result.hasNext());
        assertEquals(0, result.totalCount());
    }

    @Test
    void 작성일_오름차순_첫페이지_조회() {
        when(feedRepository.findFilteredPageAscending(
                isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), any(Pageable.class)
        )).thenReturn(List.of());

        FeedPageResponse result = feedService.getFeedPage(
                null, null, 10,
                "createdAt", "ASCENDING",
                null, null
        );

        assertNotNull(result);
        assertEquals("ASCENDING", result.sortDirection());
        assertFalse(result.hasNext());
    }

    @Test
    void 좋아요_내림차순_첫페이지_조회() {
        when(feedRepository.findByLikeCountDescending(
                isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class)
        )).thenReturn(List.of());

        FeedPageResponse result = feedService.getFeedPage(
                null, null, 10,
                "likeCount", "DESCENDING",
                null, null
        );

        assertNotNull(result);
        assertEquals("likeCount", result.sortBy());
        assertFalse(result.hasNext());
    }

    @Test
    void 좋아요_오름차순_첫페이지_조회() {
        when(feedRepository.findByLikeCountAscending(
                isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class)
        )).thenReturn(List.of());

        FeedPageResponse result = feedService.getFeedPage(
                null, null, 10,
                "likeCount", "ASCENDING",
                null, null
        );

        assertNotNull(result);
        assertEquals("ASCENDING", result.sortDirection());
        assertFalse(result.hasNext());
    }

    @Test
    void 작성일_내림차순_다음페이지_조회() {
        Instant cursor = Instant.parse("2026-11-23T01:24:44Z");

        when(feedRepository.findFilteredPageDescending(
                isNull(), isNull(), isNull(), isNull(),
                eq(cursor), eq(feedId), any(Pageable.class)
        )).thenReturn(List.of());

        FeedPageResponse result = feedService.getFeedPage(
                cursor.toString(), feedId, 10,
                "createdAt", "DESCENDING",
                null, null
        );

        assertNotNull(result);
        assertFalse(result.hasNext());
    }

    @Test
    void 작성일_오름차순_다음페이지_조회() {
        Instant cursor = Instant.parse("2026-11-23T01:24:44Z");

        when(feedRepository.findFilteredPageAscending(
                isNull(), isNull(), isNull(), isNull(),
                eq(cursor), eq(feedId), any(Pageable.class)
        )).thenReturn(List.of());

        FeedPageResponse result = feedService.getFeedPage(
                cursor.toString(), feedId, 10,
                "createdAt", "ASCENDING",
                null, null
        );

        assertNotNull(result);
        assertFalse(result.hasNext());
    }

    @Test
    void 좋아요_내림차순_다음페이지_조회() {
        Instant cursorTime =
                Instant.parse("2026-11-23T01:24:44Z");

        when(feedRepository.findNextPageByLikeCountDescending(
                isNull(), isNull(), isNull(), isNull(),
                eq(1L), eq(cursorTime), eq(feedId),
                any(Pageable.class)
        )).thenReturn(List.of());

        FeedPageResponse result = feedService.getFeedPage(
                "1|2026-11-23T01:24:44Z",
                feedId, 10,
                "likeCount", "DESCENDING",
                null, null
        );

        assertNotNull(result);
        assertFalse(result.hasNext());
    }

    @Test
    void 좋아요_오름차순_다음페이지_조회() {
        Instant cursorTime =
                Instant.parse("2026-11-23T01:24:44Z");

        when(feedRepository.findNextPageByLikeCountAscending(
                isNull(), isNull(), isNull(), isNull(),
                eq(0L), eq(cursorTime), eq(feedId),
                any(Pageable.class)
        )).thenReturn(List.of());

        FeedPageResponse result = feedService.getFeedPage(
                "0|2026-11-23T01:24:44Z",
                feedId, 10,
                "likeCount", "ASCENDING",
                null, null
        );

        assertNotNull(result);
        assertFalse(result.hasNext());
    }

    // ========================================
    // 단건 조회 테스트
    // ========================================

    @Test
    void 피드_단건조회_성공() {
        Feed feed = new Feed(userId, weatherId, "테스트");

        when(feedRepository.findById(feedId))
                .thenReturn(Optional.of(feed));

        FeedResponse expected = mock(FeedResponse.class);

        when(feedResponseService.toResponse(feed, userId))
                .thenReturn(expected);

        FeedResponse result =
                feedService.getFeedResponse(feedId, userId);

        assertSame(expected, result);
    }

    @Test
    void 피드_단건조회시_존재하지_않으면_예외() {
        when(feedRepository.findById(feedId))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> feedService.getFeedResponse(feedId, userId)
        );
    }
}
