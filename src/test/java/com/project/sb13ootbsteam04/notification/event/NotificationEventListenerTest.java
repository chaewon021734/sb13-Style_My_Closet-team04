package com.project.sb13ootbsteam04.notification.event;

import com.project.sb13ootbsteam04.clothes.event.ClothesAttributeDefCreatedEvent;
import com.project.sb13ootbsteam04.feed.event.FeedCommentedEvent;
import com.project.sb13ootbsteam04.feed.event.FeedCreatedEvent;
import com.project.sb13ootbsteam04.feed.event.FeedLikedEvent;
import com.project.sb13ootbsteam04.follow.entity.Follow;
import com.project.sb13ootbsteam04.follow.event.FollowCreatedEvent;
import com.project.sb13ootbsteam04.follow.repository.FollowRepository;
import com.project.sb13ootbsteam04.notification.entity.NotificationLevel;
import com.project.sb13ootbsteam04.notification.repository.NotificationRepository;
import com.project.sb13ootbsteam04.notification.service.NotificationService;
import com.project.sb13ootbsteam04.user.entity.Profile;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.event.UserRoleChangedEvent;
import com.project.sb13ootbsteam04.user.repository.ProfileRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocation;
import com.project.sb13ootbsteam04.weather.event.WeatherChangeDetectedEvent;
import com.project.sb13ootbsteam04.weather.event.WeatherChangeType;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 생성 이벤트 리스너")
class NotificationEventListenerTest {

    @Mock private NotificationService notificationService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private FollowRepository followRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private WeatherLocationRepository weatherLocationRepository;

    @InjectMocks
    private NotificationEventListener listener;

    private final UUID actorId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();

    @Test
    @DisplayName("팔로우하면 팔로우 대상에게 알림")
    void notifiesFollowee() {
        givenName(actorId, "홍길동");

        listener.handle(new FollowCreatedEvent(actorId, receiverId));

        verify(notificationService).create(receiverId, "새 팔로워",
                "홍길동님이 회원님을 팔로우했습니다.", NotificationLevel.INFO);
    }

    @Test
    @DisplayName("다른 사용자가 내 피드에 좋아요하면 피드 작성자에게 알림, 본인 좋아요는 제외")
    void notifiesFeedAuthorOnLike() {
        givenName(actorId, "홍길동");

        listener.handle(new FeedLikedEvent(UUID.randomUUID(), receiverId, actorId));
        listener.handle(new FeedLikedEvent(UUID.randomUUID(), actorId, actorId));

        verify(notificationService).create(receiverId, "새 좋아요",
                "홍길동님이 회원님의 피드를 좋아합니다.", NotificationLevel.INFO);
        verify(notificationService, never()).create(eq(actorId), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("댓글은 앞 30자까지 보여 주고 본인 댓글은 제외")
    void notifiesFeedAuthorOnComment() {
        givenName(actorId, "홍길동");
        var longContent = "가".repeat(31);

        listener.handle(new FeedCommentedEvent(UUID.randomUUID(), receiverId, actorId, longContent));
        listener.handle(new FeedCommentedEvent(UUID.randomUUID(), actorId, actorId, "내 댓글"));

        verify(notificationService).create(receiverId, "새 댓글",
                "홍길동님: " + "가".repeat(30) + "…", NotificationLevel.INFO);
        verify(notificationService, never()).create(eq(actorId), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("피드를 올리면 작성자의 팔로워 전원에게 알림")
    void notifiesFollowersOnNewFeed() {
        givenName(actorId, "홍길동");
        var follower1 = new Follow(UUID.randomUUID(), actorId);
        var follower2 = new Follow(UUID.randomUUID(), actorId);
        when(followRepository.findByFollowingId(eq(actorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(follower1, follower2)));

        listener.handle(new FeedCreatedEvent(UUID.randomUUID(), actorId));

        verify(notificationService).createAll(List.of(follower1.getFollowerId(), follower2.getFollowerId()),
                "새 피드", "홍길동님이 새 피드를 올렸습니다.", NotificationLevel.INFO);
    }

    @Test
    @DisplayName("팔로워가 없으면 피드 알림을 만들지 않는다")
    void skipsNewFeedWithoutFollowers() {
        when(followRepository.findByFollowingId(eq(actorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        listener.handle(new FeedCreatedEvent(UUID.randomUUID(), actorId));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("권한이 바뀌면 대상 사용자에게 알림")
    void notifiesRoleChange() {
        listener.handle(new UserRoleChangedEvent(receiverId, UserRole.ADMIN));
        listener.handle(new UserRoleChangedEvent(actorId, UserRole.USER));

        verify(notificationService).create(receiverId, "권한 변경",
                "회원님의 권한이 관리자(ADMIN)로 변경되었습니다.", NotificationLevel.INFO);
        verify(notificationService).create(actorId, "권한 변경",
                "회원님의 권한이 일반 사용자(USER)로 변경되었습니다.", NotificationLevel.INFO);
    }

    @Test
    @DisplayName("의상 속성이 추가되면 전체 사용자에게 알림")
    void notifiesAllUsersOnNewAttribute() {
        var user1 = user(UUID.randomUUID());
        var user2 = user(UUID.randomUUID());
        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        listener.handle(new ClothesAttributeDefCreatedEvent(UUID.randomUUID(), "두께"));

        verify(notificationService).createAll(List.of(user1.getId(), user2.getId()), "새 의상 속성",
                "'두께' 속성이 추가되었습니다.", NotificationLevel.INFO);
    }

    @Test
    @DisplayName("날씨 변화는 프로필 위치 격자가 같은 사용자에게 변화 종류별로 하루 한 번 알림")
    void notifiesUsersInSameGridOnWeatherChange() {
        var locationId = UUID.randomUUID();
        when(weatherLocationRepository.findById(locationId))
                .thenReturn(Optional.of(new WeatherLocation(37.5665, 126.978, 60, 127)));
        var sameGrid = profile(receiverId, Map.of("x", 60, "y", 127));
        var otherGrid = profile(UUID.randomUUID(), Map.of("x", 55, "y", 124));
        var noLocation = profile(UUID.randomUUID(), null);
        when(profileRepository.findAll()).thenReturn(List.of(sameGrid, otherGrid, noLocation));
        var rainContent = "11월 29일 비 또는 눈 소식이 있어요. 옷차림에 참고하세요.";
        var windContent = "11월 29일 강한 바람이 예보됐어요. 옷차림에 참고하세요.";
        when(notificationRepository.existsByReceiver_IdAndContent(receiverId, rainContent)).thenReturn(true);
        when(notificationRepository.existsByReceiver_IdAndContent(receiverId, windContent)).thenReturn(false);

        // 2026-11-29 09:00 KST
        listener.handle(new WeatherChangeDetectedEvent(locationId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-11-29T00:00:00Z"),
                Set.of(WeatherChangeType.PRECIPITATION_STARTED, WeatherChangeType.STRONG_WIND_STARTED)));

        verify(notificationService).create(receiverId, "날씨 변화", windContent, NotificationLevel.WARNING);
        verify(notificationService, never()).create(receiverId, "날씨 변화", rainContent, NotificationLevel.WARNING);
        verify(notificationService, never()).create(eq(otherGrid.getUserId()), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("알림 저장에 실패해도 예외를 밖으로 던지지 않는다")
    void swallowsNotificationFailure() {
        givenName(actorId, "홍길동");
        when(notificationService.create(any(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("DB 오류"));

        assertThatCode(() -> listener.handle(new FollowCreatedEvent(actorId, receiverId)))
                .doesNotThrowAnyException();
    }

    private void givenName(UUID userId, String name) {
        var user = new User(name, userId + "@test.io", "encoded");
        ReflectionTestUtils.setField(user, "id", userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private User user(UUID id) {
        var user = new User("사용자", id + "@test.io", "encoded");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Profile profile(UUID userId, Map<String, Object> location) {
        var profile = mock(Profile.class);
        lenient().when(profile.getUserId()).thenReturn(userId);
        lenient().when(profile.getLocation()).thenReturn(location);
        return profile;
    }
}
