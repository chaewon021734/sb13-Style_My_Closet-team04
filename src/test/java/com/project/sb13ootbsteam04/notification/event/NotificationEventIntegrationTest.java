package com.project.sb13ootbsteam04.notification.event;

import com.project.sb13ootbsteam04.clothes.service.ClothesAttributeDefService;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.feed.dto.CreateCommentRequest;
import com.project.sb13ootbsteam04.feed.dto.CreateFeedRequest;
import com.project.sb13ootbsteam04.feed.service.CommentService;
import com.project.sb13ootbsteam04.feed.service.FeedLikeService;
import com.project.sb13ootbsteam04.feed.service.FeedService;
import com.project.sb13ootbsteam04.follow.dto.CreateFollowRequest;
import com.project.sb13ootbsteam04.follow.service.FollowService;
import com.project.sb13ootbsteam04.notification.entity.Notification;
import com.project.sb13ootbsteam04.notification.entity.NotificationLevel;
import com.project.sb13ootbsteam04.notification.exception.NotificationException;
import com.project.sb13ootbsteam04.notification.repository.NotificationRepository;
import com.project.sb13ootbsteam04.notification.service.NotificationService;
import com.project.sb13ootbsteam04.user.entity.AdminOperationLock;
import com.project.sb13ootbsteam04.user.entity.Profile;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.repository.AdminOperationLockRepository;
import com.project.sb13ootbsteam04.user.repository.ProfileRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import com.project.sb13ootbsteam04.user.service.AdminUserService;
import com.project.sb13ootbsteam04.weather.dto.KmaForecastItem;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocation;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import com.project.sb13ootbsteam04.weather.service.WeatherForecastService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

// 실제 서비스 호출 → 커밋 → 알림 저장까지 거친 뒤 알림 테이블 상태로 검증한다.
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("알림 생성 통합")
class NotificationEventIntegrationTest {

    @Autowired private FollowService followService;
    @Autowired private FeedService feedService;
    @Autowired private FeedLikeService feedLikeService;
    @Autowired private CommentService commentService;
    @Autowired private AdminUserService adminUserService;
    @Autowired private ClothesAttributeDefService clothesAttributeDefService;
    @Autowired private WeatherForecastService weatherForecastService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private AdminOperationLockRepository adminOperationLockRepository;
    @Autowired private WeatherLocationRepository weatherLocationRepository;
    @Autowired private WeatherRepository weatherRepository;
    @Autowired private NotificationRepository notificationRepository;

    @MockitoSpyBean
    private NotificationService notificationService;

    @Test
    @DisplayName("팔로우하면 팔로우 대상에게 '새 팔로워' 알림이 저장된다")
    void savesNotificationAfterFollowCommitted() {
        var follower = saveUser("팔로워");
        var followee = saveUser("팔로우대상");

        followService.createFollow(new CreateFollowRequest(followee.getId(), follower.getId()));

        assertThat(notificationsOf(followee)).extracting(Notification::getContent)
                .containsExactly("팔로워님이 회원님을 팔로우했습니다.");
    }

    @Test
    @DisplayName("알림 저장이 실패해도 팔로우는 정상 처리된다")
    void followSucceedsWhenNotificationFails() {
        var follower = saveUser("팔로워2");
        var followee = saveUser("팔로우대상2");
        doThrow(new IllegalStateException("알림 저장 실패"))
                .when(notificationService).create(any(), anyString(), anyString(), any());

        var followId = followService.createFollow(new CreateFollowRequest(followee.getId(), follower.getId()));

        assertThat(followId).isNotNull();
        assertThat(notificationsOf(followee)).isEmpty();
    }

    @Test
    @DisplayName("내 피드에 내가 좋아요하면 알림이 생기지 않는다")
    void noNotificationForOwnLike() {
        var author = saveUser("작성자1");
        var feedId = createFeed(author);

        feedLikeService.likeFeed(feedId, author.getId());

        assertThat(notificationsOf(author)).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자가 좋아요를 두 번 요청해도 좋아요 알림은 한 건이다")
    void singleNotificationForRepeatedLike() {
        var author = saveUser("작성자2");
        var liker = saveUser("좋아요");
        var feedId = createFeed(author);

        feedLikeService.likeFeed(feedId, liker.getId());
        feedLikeService.likeFeed(feedId, liker.getId());

        assertThat(notificationsOf(author)).extracting(Notification::getContent)
                .containsExactly("좋아요님이 회원님의 게시물을 좋아합니다.");
    }

    @Test
    @DisplayName("31자 댓글은 앞 30자와 말줄임표로 알린다")
    void truncatesLongComment() {
        var author = saveUser("작성자3");
        var commenter = saveUser("댓글");
        var feedId = createFeed(author);

        commentService.createComment(feedId,
                new CreateCommentRequest(feedId, commenter.getId(), "가".repeat(31)), commenter.getId());

        assertThat(notificationsOf(author)).extracting(Notification::getContent)
                .containsExactly("댓글님: " + "가".repeat(30) + "…");
    }

    @Test
    @DisplayName("피드를 올리면 팔로워에게만 '새 피드' 알림이 간다")
    void notifiesOnlyFollowersOnNewFeed() {
        var author = saveUser("작성자4");
        var follower1 = saveUser("팔로워A");
        var follower2 = saveUser("팔로워B");
        var stranger = saveUser("남");
        followService.createFollow(new CreateFollowRequest(author.getId(), follower1.getId()));
        followService.createFollow(new CreateFollowRequest(author.getId(), follower2.getId()));

        createFeed(author);

        assertThat(notificationsOf(follower1)).extracting(Notification::getTitle).containsExactly("새 피드");
        assertThat(notificationsOf(follower2)).extracting(Notification::getTitle).containsExactly("새 피드");
        assertThat(notificationsOf(stranger)).isEmpty();
    }

    @Test
    @DisplayName("권한이 실제로 바뀔 때만 권한 변경 알림이 간다")
    void notifiesOnlyWhenRoleActuallyChanges() {
        ensureAdminOperationLock();
        var user = saveUser("권한대상");

        adminUserService.changeRole(user.getId(), UserRole.USER);
        assertThat(notificationsOf(user)).isEmpty();

        adminUserService.changeRole(user.getId(), UserRole.ADMIN);
        assertThat(notificationsOf(user)).extracting(Notification::getContent)
                .containsExactly("회원님의 권한이 관리자(ADMIN)로 변경되었습니다.");
    }

    @Test
    @DisplayName("의상 속성이 추가되면 모든 사용자에게 알림이 간다")
    void notifiesAllUsersOnNewAttribute() {
        var users = List.of(saveUser("전체1"), saveUser("전체2"), saveUser("전체3"));
        var name = "두께-" + UUID.randomUUID().toString().substring(0, 8);

        clothesAttributeDefService.create(new ClothesAttributeDefCreateCommand(name, List.of("얇음", "두꺼움")));

        users.forEach(user -> assertThat(notificationsOf(user)).extracting(Notification::getContent)
                .containsExactly("'" + name + "' 속성이 추가되었습니다."));
    }

    @Test
    @DisplayName("비 소식이 생기면 같은 격자 사용자에게만 하루 한 번 경고 알림이 간다")
    void notifiesSameGridUserOncePerDayOnWeatherChange() {
        var nearby = saveUser("같은격자");
        var faraway = saveUser("다른격자");
        saveProfile(nearby, 61, 128);
        saveProfile(faraway, 55, 124);
        var location = weatherLocationRepository.saveAndFlush(new WeatherLocation(37.6, 127.0, 61, 128));
        // 이전 발표: 비 없음 → 다음 발표: 같은 날 두 시각 모두 비
        weatherForecastService.saveForecast(location.getId(), forecast("0500", "1200", "0", 61, 128));
        var rainyItems = new java.util.ArrayList<>(forecast("0800", "1200", "1", 61, 128));
        rainyItems.addAll(forecast("0800", "1300", "1", 61, 128));
        weatherForecastService.saveForecast(location.getId(), rainyItems);

        assertThat(notificationsOf(nearby)).extracting(Notification::getLevel, Notification::getContent)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(NotificationLevel.WARNING,
                        "12월 1일 비 또는 눈 소식이 있어요. 옷차림에 참고하세요."));
        assertThat(notificationsOf(faraway)).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 알림은 삭제할 수 없고, 본인 알림은 삭제된다")
    void deletesOnlyOwnNotification() {
        var owner = saveUser("알림주인");
        var other = saveUser("다른사람");
        var notification = notificationService.create(owner.getId(), "제목", "내용", NotificationLevel.INFO);

        assertThatThrownBy(() -> notificationService.delete(notification.id(), other.getId()))
                .isInstanceOf(NotificationException.class);
        assertThat(notificationsOf(owner)).hasSize(1);

        notificationService.delete(notification.id(), owner.getId());
        assertThat(notificationsOf(owner)).isEmpty();
    }

    private User saveUser(String name) {
        return userRepository.save(new User(name, name + "-" + UUID.randomUUID() + "@test.io", "encoded"));
    }

    private List<Notification> notificationsOf(User user) {
        return notificationRepository.findFirstPage(user.getId(), PageRequest.of(0, 20));
    }

    // 피드 등록에 필요한 날씨 한 건을 만들고 의상 없이 피드를 올린다
    private UUID createFeed(User author) {
        var location = weatherLocationRepository.saveAndFlush(new WeatherLocation(35.0, 129.0, 98, 76));
        weatherForecastService.saveForecast(location.getId(), forecast("0500", "1200", "0", 98, 76));
        var weatherId = weatherRepository.findAll().stream()
                .filter(weather -> weather.getLocationId().equals(location.getId()))
                .findFirst().orElseThrow().getId();
        return feedService.createFeed(new CreateFeedRequest(author.getId(), weatherId, List.of(), "오늘 코디"),
                author.getId());
    }

    private void saveProfile(User user, int gridX, int gridY) {
        var profile = BeanUtils.instantiateClass(Profile.class);
        ReflectionTestUtils.setField(profile, "user", user);
        ReflectionTestUtils.setField(profile, "location", Map.of("x", gridX, "y", gridY));
        profileRepository.saveAndFlush(profile);
    }

    private void ensureAdminOperationLock() {
        if (!adminOperationLockRepository.existsById(AdminOperationLock.LOCK_ID)) {
            adminOperationLockRepository.saveAndFlush(new AdminOperationLock(AdminOperationLock.LOCK_ID));
        }
    }

    // 2026-12-01 예보(발표 시각·강수 형태만 다르게)
    private List<KmaForecastItem> forecast(String baseTime, String forecastTime, String precipitationType,
                                           int gridX, int gridY) {
        return Map.of("SKY", "1", "PTY", precipitationType, "PCP", "강수없음", "POP", "0", "REH", "50",
                        "TMP", "10", "WSD", "1.0")
                .entrySet().stream()
                .map(entry -> new KmaForecastItem("20261201", baseTime, "20261201", forecastTime,
                        entry.getKey(), entry.getValue(), gridX, gridY))
                .toList();
    }
}
