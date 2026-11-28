
package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.feed.entity.Feed;
import com.project.sb13ootbsteam04.feed.entity.FeedLike;
import com.project.sb13ootbsteam04.feed.repository.FeedLikeRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedLikeServiceTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedLikeRepository feedLikeRepository;

    @InjectMocks
    private FeedLikeService feedLikeService;

    private final UUID feedId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    // ========================================
    // 좋아요 등록 테스트
    // ========================================

    @Test
    void 좋아요_등록_성공() {
        Feed feed = mock(Feed.class);

        when(feedRepository.findById(feedId))
                .thenReturn(Optional.of(feed));

        when(feedLikeRepository.existsByFeed_IdAndUserId(
                feedId, userId
        )).thenReturn(false);

        feedLikeService.likeFeed(feedId, userId);

        ArgumentCaptor<FeedLike> captor =
                ArgumentCaptor.forClass(FeedLike.class);

        verify(feedLikeRepository).save(captor.capture());

        FeedLike saved = captor.getValue();

        assertNotNull(saved);
        assertSame(feed, saved.getFeed());
        assertEquals(userId, saved.getUserId());
    }

    @Test
    void 존재하지_않는_피드에_좋아요를_누르면_예외() {
        when(feedRepository.findById(feedId))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> feedLikeService.likeFeed(feedId, userId)
        );

        assertEquals(
                "존재하지 않는 피드입니다.",
                exception.getMessage()
        );

        verifyNoInteractions(feedLikeRepository);
    }

    @Test
    void 사용자_ID가_없으면_좋아요_등록_예외() {
        Feed feed = mock(Feed.class);

        when(feedRepository.findById(feedId))
                .thenReturn(Optional.of(feed));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> feedLikeService.likeFeed(feedId, null)
        );

        assertEquals(
                "사용자 ID는 필수입니다.",
                exception.getMessage()
        );

        verifyNoInteractions(feedLikeRepository);
    }

    @Test
    void 이미_좋아요를_누른_피드이면_저장하지_않고_성공() {
        Feed feed = mock(Feed.class);

        when(feedRepository.findById(feedId))
                .thenReturn(Optional.of(feed));

        when(feedLikeRepository.existsByFeed_IdAndUserId(
                feedId, userId
        )).thenReturn(true);

        // 연속 요청에도 오류 없이 같은 결과 유지
        assertDoesNotThrow(
                () -> feedLikeService.likeFeed(feedId, userId)
        );

        verify(feedLikeRepository, never())
                .save(any(FeedLike.class));
    }

    // ========================================
    // 좋아요 취소 테스트
    // ========================================

    @Test
    void 좋아요_취소_성공() {
        FeedLike feedLike = mock(FeedLike.class);

        when(feedLikeRepository.findByFeed_IdAndUserId(
                feedId, userId
        )).thenReturn(Optional.of(feedLike));

        feedLikeService.unlikeFeed(feedId, userId);

        verify(feedLikeRepository).delete(feedLike);
        verifyNoInteractions(feedRepository);
    }

    @Test
    void 사용자_ID가_없으면_좋아요_취소_예외() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> feedLikeService.unlikeFeed(feedId, null)
        );

        assertEquals(
                "사용자 ID는 필수입니다.",
                exception.getMessage()
        );

        verifyNoInteractions(feedLikeRepository);
        verifyNoInteractions(feedRepository);
    }

    @Test
    void 좋아요_기록이_없으면_취소_예외() {
        when(feedLikeRepository.findByFeed_IdAndUserId(
                feedId, userId
        )).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> feedLikeService.unlikeFeed(feedId, userId)
        );

        assertEquals(
                "좋아요 기록이 존재하지 않습니다.",
                exception.getMessage()
        );

        verify(feedLikeRepository, never())
                .delete(any(FeedLike.class));
    }

    // ========================================
    // 좋아요 개수 조회 테스트
    // ========================================

    @Test
    void 좋아요_개수_조회_성공() {
        when(feedLikeRepository.countByFeed_Id(feedId))
                .thenReturn(5L);

        long result = feedLikeService.getLikeCount(feedId);

        assertEquals(5L, result);

        verify(feedLikeRepository).countByFeed_Id(feedId);
    }

    @Test
    void 좋아요가_없으면_0을_반환한다() {
        when(feedLikeRepository.countByFeed_Id(feedId))
                .thenReturn(0L);

        long result = feedLikeService.getLikeCount(feedId);

        assertEquals(0L, result);
    }

    // ========================================
    // 내가 좋아요를 눌렀는지 확인
    // ========================================

    @Test
    void 내가_좋아요를_누른_경우_true() {
        when(feedLikeRepository.existsByFeed_IdAndUserId(
                feedId, userId
        )).thenReturn(true);

        boolean result =
                feedLikeService.isLikedByMe(feedId, userId);

        assertTrue(result);

        verify(feedLikeRepository)
                .existsByFeed_IdAndUserId(feedId, userId);
    }

    @Test
    void 내가_좋아요를_누르지_않은_경우_false() {
        when(feedLikeRepository.existsByFeed_IdAndUserId(
                feedId, userId
        )).thenReturn(false);

        boolean result =
                feedLikeService.isLikedByMe(feedId, userId);

        assertFalse(result);
    }

    @Test
    void 로그인하지_않은_사용자는_false() {
        boolean result =
                feedLikeService.isLikedByMe(feedId, null);

        assertFalse(result);

        verifyNoInteractions(feedLikeRepository);
        verifyNoInteractions(feedRepository);
    }
}
