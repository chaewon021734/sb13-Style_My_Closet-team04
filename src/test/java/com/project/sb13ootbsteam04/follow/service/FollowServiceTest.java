
package com.project.sb13ootbsteam04.follow.service;

import com.project.sb13ootbsteam04.follow.event.FollowCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.project.sb13ootbsteam04.follow.dto.CreateFollowRequest;
import com.project.sb13ootbsteam04.follow.dto.FollowPageResponse;
import com.project.sb13ootbsteam04.follow.dto.FollowResponse;
import com.project.sb13ootbsteam04.follow.dto.FollowSummaryResponse;
import com.project.sb13ootbsteam04.follow.entity.Follow;
import com.project.sb13ootbsteam04.follow.repository.FollowRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FollowResponseService followResponseService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FollowService followService;

    private final UUID followerId = UUID.randomUUID();
    private final UUID followeeId = UUID.randomUUID();
    private final UUID followId = UUID.randomUUID();

    // ========================================
    // 팔로우 생성 테스트
    // ========================================

    @Test
    void 팔로우_요청이_null이면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> followService.createFollow(null)
        );
    }

    @Test
    void 팔로워_ID가_없으면_예외() {
        CreateFollowRequest request =
                createRequest(null, followeeId);

        assertThrows(
                IllegalArgumentException.class,
                () -> followService.createFollow(request)
        );
    }

    @Test
    void 팔로우_대상_ID가_없으면_예외() {
        CreateFollowRequest request =
                createRequest(followerId, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> followService.createFollow(request)
        );
    }

    @Test
    void 자기_자신을_팔로우하면_예외() {
        CreateFollowRequest request =
                createRequest(followerId, followerId);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> followService.createFollow(request)
        );

        assertEquals(
                "자기 자신을 팔로우할 수 없습니다.",
                exception.getMessage()
        );
    }

    @Test
    void 존재하지_않는_팔로워이면_예외() {
        CreateFollowRequest request =
                createRequest(followerId, followeeId);

        when(userRepository.existsById(followerId))
                .thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> followService.createFollow(request)
        );

        assertEquals(
                "존재하지 않는 사용자입니다: " + followerId,
                exception.getMessage()
        );

        verifyNoInteractions(followRepository);
    }

    @Test
    void 존재하지_않는_팔로우_대상이면_예외() {
        CreateFollowRequest request =
                createRequest(followerId, followeeId);

        when(userRepository.existsById(followerId))
                .thenReturn(true);

        when(userRepository.existsById(followeeId))
                .thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> followService.createFollow(request)
        );

        assertEquals(
                "존재하지 않는 사용자입니다: " + followeeId,
                exception.getMessage()
        );

        verifyNoInteractions(followRepository);
    }

    @Test
    void 이미_팔로우한_사용자이면_예외() {
        CreateFollowRequest request =
                createRequest(followerId, followeeId);

        mockBothUsersExist();

        when(followRepository.existsByFollowerIdAndFollowingId(
                followerId, followeeId
        )).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> followService.createFollow(request)
        );

        assertEquals(
                "이미 팔로우한 사용자입니다.",
                exception.getMessage()
        );

        verify(followRepository, never())
                .saveAndFlush(any(Follow.class));
    }

    @Test
    void 팔로우_생성_성공() {
        CreateFollowRequest request =
                createRequest(followerId, followeeId);

        mockBothUsersExist();

        Follow saved = mock(Follow.class);

        when(saved.getId()).thenReturn(followId);

        when(followRepository.saveAndFlush(any(Follow.class)))
                .thenReturn(saved);

        UUID result = followService.createFollow(request);

        assertEquals(followId, result);

        ArgumentCaptor<Follow> captor =
                ArgumentCaptor.forClass(Follow.class);

        verify(followRepository)
                .saveAndFlush(captor.capture());

        Follow captured = captor.getValue();

        assertEquals(followerId, captured.getFollowerId());
        assertEquals(followeeId, captured.getFollowingId());

        // 팔로우 대상 알림을 위한 이벤트 발행
        verify(eventPublisher).publishEvent(new FollowCreatedEvent(followerId, followeeId));
    }

    @Test
    void 팔로우_생성후_응답_반환() {
        CreateFollowRequest request =
                createRequest(followerId, followeeId);

        mockBothUsersExist();

        Follow saved = mock(Follow.class);
        FollowResponse expected = mock(FollowResponse.class);

        when(saved.getId()).thenReturn(followId);

        when(followRepository.saveAndFlush(any(Follow.class)))
                .thenReturn(saved);

        when(followRepository.findById(followId))
                .thenReturn(Optional.of(saved));

        when(followResponseService.toResponse(saved))
                .thenReturn(expected);

        FollowResponse result =
                followService.createFollowResponse(request);

        assertSame(expected, result);
    }

    @Test
    void 생성후_팔로우를_찾을수_없으면_예외() {
        CreateFollowRequest request =
                createRequest(followerId, followeeId);

        mockBothUsersExist();

        Follow saved = mock(Follow.class);

        when(saved.getId()).thenReturn(followId);

        when(followRepository.saveAndFlush(any(Follow.class)))
                .thenReturn(saved);

        when(followRepository.findById(followId))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> followService.createFollowResponse(request)
        );
    }

    // ========================================
    // 팔로우 삭제 테스트
    // ========================================

    @Test
    void 팔로우_ID가_없으면_삭제_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> followService.deleteFollow(null, followerId)
        );
    }

    @Test
    void 로그인하지_않으면_삭제_예외() {
        assertThrows(
                AccessDeniedException.class,
                () -> followService.deleteFollow(followId, null)
        );
    }

    @Test
    void 존재하지_않는_팔로우_삭제시_예외() {
        when(followRepository.findById(followId))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> followService.deleteFollow(followId, followerId)
        );
    }

    @Test
    void 다른_사용자의_팔로우는_삭제할수_없음() {
        Follow follow = new Follow(
                UUID.randomUUID(), followeeId
        );

        when(followRepository.findById(followId))
                .thenReturn(Optional.of(follow));

        assertThrows(
                AccessDeniedException.class,
                () -> followService.deleteFollow(followId, followerId)
        );

        verify(followRepository, never())
                .delete(any(Follow.class));
    }

    @Test
    void 팔로우_삭제_성공() {
        Follow follow = new Follow(followerId, followeeId);

        when(followRepository.findById(followId))
                .thenReturn(Optional.of(follow));

        followService.deleteFollow(followId, followerId);

        verify(followRepository).delete(follow);
    }

    // ========================================
    // 팔로잉 / 팔로워 조회 테스트
    // ========================================

    @Test
    void 팔로잉_목록_조회_성공() {
        when(userRepository.existsById(followerId))
                .thenReturn(true);

        Page<Follow> expected = new PageImpl<>(List.of());

        when(followRepository.findByFollowerId(
                eq(followerId), any(Pageable.class)
        )).thenReturn(expected);

        Page<Follow> result = followService.getFollowings(
                followerId, Pageable.unpaged()
        );

        assertSame(expected, result);
    }

    @Test
    void 팔로워_목록_조회_성공() {
        when(userRepository.existsById(followeeId))
                .thenReturn(true);

        Page<Follow> expected = new PageImpl<>(List.of());

        when(followRepository.findByFollowingId(
                eq(followeeId), any(Pageable.class)
        )).thenReturn(expected);

        Page<Follow> result = followService.getFollowers(
                followeeId, Pageable.unpaged()
        );

        assertSame(expected, result);
    }

    @Test
    void 팔로워_수_조회_성공() {
        when(userRepository.existsById(followeeId))
                .thenReturn(true);

        when(followRepository.countByFollowingId(followeeId))
                .thenReturn(5L);

        assertEquals(
                5L,
                followService.getFollowerCount(followeeId)
        );
    }

    @Test
    void 팔로잉_수_조회_성공() {
        when(userRepository.existsById(followerId))
                .thenReturn(true);

        when(followRepository.countByFollowerId(followerId))
                .thenReturn(3L);

        assertEquals(
                3L,
                followService.getFollowingCount(followerId)
        );
    }

    @Test
    void 팔로우_여부_확인_성공() {
        when(followRepository.existsByFollowerIdAndFollowingId(
                followerId, followeeId
        )).thenReturn(true);

        assertTrue(
                followService.isFollowing(followerId, followeeId)
        );
    }

    @Test
    void 팔로우하지_않은_경우_false() {
        assertFalse(
                followService.isFollowing(followerId, followeeId)
        );

        verify(followRepository)
                .existsByFollowerIdAndFollowingId(
                        followerId, followeeId
                );
    }

    @Test
    void 팔로우_여부_확인시_ID가_없으면_false() {
        assertFalse(
                followService.isFollowing(null, followeeId)
        );

        assertFalse(
                followService.isFollowing(followerId, null)
        );

        verifyNoInteractions(followRepository);
    }

    // ========================================
    // 팔로우 요약 테스트
    // ========================================

    @Test
    void 팔로우_요약_비로그인_조회() {
        when(userRepository.existsById(followeeId))
                .thenReturn(true);

        when(followRepository.countByFollowingId(followeeId))
                .thenReturn(5L);

        when(followRepository.countByFollowerId(followeeId))
                .thenReturn(2L);

        FollowSummaryResponse result =
                followService.getFollowSummary(followeeId, null);

        assertNotNull(result);

        verify(followRepository, never())
                .findByFollowerIdAndFollowingId(any(), any());
    }

    @Test
    void 팔로우_요약_로그인_조회() {
        mockBothUsersExist();

        when(followRepository.findByFollowerIdAndFollowingId(
                followerId, followeeId
        )).thenReturn(Optional.empty());

        when(followRepository.existsByFollowerIdAndFollowingId(
                followeeId, followerId
        )).thenReturn(true);

        FollowSummaryResponse result =
                followService.getFollowSummary(
                        followeeId, followerId
                );

        assertNotNull(result);
    }

    @Test
    void 팔로우_요약_내가_팔로우한_경우() {
        mockBothUsersExist();

        Follow follow = mock(Follow.class);

        when(follow.getId()).thenReturn(followId);

        when(followRepository.findByFollowerIdAndFollowingId(
                followerId, followeeId
        )).thenReturn(Optional.of(follow));

        FollowSummaryResponse result =
                followService.getFollowSummary(
                        followeeId, followerId
                );

        assertNotNull(result);
        verify(follow).getId();
    }

    // ========================================
    // 커서 페이지네이션 테스트
    // ========================================

    @Test
    void 팔로잉_첫페이지_조회() {
        when(userRepository.existsById(followerId))
                .thenReturn(true);

        when(followRepository.findFirstFollowingPage(
                eq(followerId), isNull(), any(Pageable.class)
        )).thenReturn(List.of());

        FollowPageResponse result =
                followService.getFollowingPage(
                        followerId, null, null, 10, null
                );

        assertNotNull(result);
        assertFalse(result.hasNext());
        assertEquals(0L, result.totalCount());
    }

    @Test
    void 팔로워_첫페이지_조회() {
        when(userRepository.existsById(followeeId))
                .thenReturn(true);

        when(followRepository.findFirstFollowerPage(
                eq(followeeId), isNull(), any(Pageable.class)
        )).thenReturn(List.of());

        FollowPageResponse result =
                followService.getFollowerPage(
                        followeeId, null, null, 10, null
                );

        assertNotNull(result);
        assertFalse(result.hasNext());
    }

    @Test
    void 팔로잉_다음페이지_조회() {
        when(userRepository.existsById(followerId))
                .thenReturn(true);

        Instant cursor =
                Instant.parse("2026-11-23T01:24:44Z");

        when(followRepository.findNextFollowingPage(
                eq(followerId), isNull(),
                eq(cursor), eq(followId),
                any(Pageable.class)
        )).thenReturn(List.of());

        FollowPageResponse result =
                followService.getFollowingPage(
                        followerId,
                        cursor.toString(),
                        followId,
                        10,
                        null
                );

        assertNotNull(result);
        assertFalse(result.hasNext());
    }

    @Test
    void 팔로워_다음페이지_조회() {
        when(userRepository.existsById(followeeId))
                .thenReturn(true);

        Instant cursor =
                Instant.parse("2026-11-23T01:24:44Z");

        when(followRepository.findNextFollowerPage(
                eq(followeeId), isNull(),
                eq(cursor), eq(followId),
                any(Pageable.class)
        )).thenReturn(List.of());

        FollowPageResponse result =
                followService.getFollowerPage(
                        followeeId,
                        cursor.toString(),
                        followId,
                        10,
                        null
                );

        assertNotNull(result);
        assertFalse(result.hasNext());
    }

    @Test
    void 팔로잉_조회시_limit이_잘못되면_예외() {
        when(userRepository.existsById(followerId))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> followService.getFollowingPage(
                        followerId, null, null, 0, null
                )
        );
    }

    @Test
    void 팔로워_조회시_limit이_101이면_예외() {
        when(userRepository.existsById(followeeId))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> followService.getFollowerPage(
                        followeeId, null, null, 101, null
                )
        );
    }

    @Test
    void cursor만_전달하면_예외() {
        when(userRepository.existsById(followerId))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> followService.getFollowingPage(
                        followerId,
                        "2026-11-23T01:24:44Z",
                        null,
                        10,
                        null
                )
        );
    }

    @Test
    void idAfter만_전달하면_예외() {
        when(userRepository.existsById(followeeId))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> followService.getFollowerPage(
                        followeeId, null, followId, 10, null
                )
        );
    }

    @Test
    void 잘못된_날짜커서이면_예외() {
        when(userRepository.existsById(followerId))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> followService.getFollowingPage(
                        followerId,
                        "invalid",
                        followId,
                        10,
                        null
                )
        );
    }

    @Test
    void 존재하지_않는_사용자의_목록이면_예외() {
        when(userRepository.existsById(followerId))
                .thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> followService.getFollowingPage(
                        followerId, null, null, 10, null
                )
        );
    }

    @Test
    void 사용자_ID가_null이면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> followService.getFollowerCount(null)
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void 이름검색시_공백을_제거한다() {
        when(userRepository.existsById(followerId))
                .thenReturn(true);

        when(followRepository.findFirstFollowingPage(
                eq(followerId),
                eq("테스트"),
                any(Pageable.class)
        )).thenReturn(List.of());

        FollowPageResponse result =
                followService.getFollowingPage(
                        followerId, null, null, 10, "  테스트  "
                );

        assertFalse(result.hasNext());

        verify(followRepository)
                .countFollowingsByName(followerId, "테스트");
    }

    @Test
    void 팔로잉_다음페이지가_있으면_커서를_생성한다() {
        when(userRepository.existsById(followerId))
                .thenReturn(true);

        Follow first = mock(Follow.class);
        Follow second = mock(Follow.class);

        FollowResponse response = mock(FollowResponse.class);

        Instant createdAt =
                Instant.parse("2026-11-23T01:24:44Z");

        when(first.getId()).thenReturn(followId);
        when(first.getCreatedAt()).thenReturn(createdAt);

        when(followRepository.findFirstFollowingPage(
                eq(followerId),
                isNull(),
                any(Pageable.class)
        )).thenReturn(List.of(first, second));

        when(followRepository.countFollowingsByName(
                followerId, null
        )).thenReturn(2L);

        when(followResponseService.toResponse(first))
                .thenReturn(response);

        FollowPageResponse result =
                followService.getFollowingPage(
                        followerId, null, null, 1, null
                );

        assertTrue(result.hasNext());
        assertEquals(2L, result.totalCount());
        assertEquals(createdAt.toString(), result.nextCursor());
        assertEquals(followId, result.nextIdAfter());
        assertEquals(1, result.data().size());

        verify(followResponseService, never())
                .toResponse(second);
    }

    // ========================================
    // 공통 테스트 설정
    // ========================================

    private CreateFollowRequest createRequest(
            UUID follower,
            UUID followee
    ) {
        CreateFollowRequest request =
                mock(CreateFollowRequest.class);

        when(request.followerId()).thenReturn(follower);
        when(request.followeeId()).thenReturn(followee);

        return request;
    }

    private void mockBothUsersExist() {
        when(userRepository.existsById(followerId))
                .thenReturn(true);

        when(userRepository.existsById(followeeId))
                .thenReturn(true);
    }
}
