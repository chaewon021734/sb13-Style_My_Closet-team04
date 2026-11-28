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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// 원래 작업이 커밋된 뒤 새 트랜잭션에서 알림을 저장한다.
// 알림 저장에 실패해도 팔로우·좋아요 같은 원래 기능에는 영향을 주지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

  private static final int COMMENT_PREVIEW_LENGTH = 30;
  private static final ZoneId KMA_ZONE = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter WEATHER_DATE = DateTimeFormatter.ofPattern("M월 d일");

  private final NotificationService notificationService;
  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final FollowRepository followRepository;
  private final ProfileRepository profileRepository;
  private final WeatherLocationRepository weatherLocationRepository;

  // 다른 사용자가 나를 팔로우
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(FollowCreatedEvent event) {
    notifySafely("팔로우", () -> notificationService.create(
        event.followeeId(),
        "새 팔로워",
        nameOf(event.followerId()) + "님이 회원님을 팔로우했습니다.",
        NotificationLevel.INFO
    ));
  }

  // 내 피드에 좋아요
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(FeedLikedEvent event) {
    if (isSelfOrUnknown(event.feedAuthorId(), event.likerId())) {
      return;
    }
    notifySafely("좋아요", () -> notificationService.create(
        event.feedAuthorId(),
        "새 좋아요",
        nameOf(event.likerId()) + "님이 회원님의 게시물을 좋아합니다.",
        NotificationLevel.INFO
    ));
  }

  // 내 피드에 댓글
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(FeedCommentedEvent event) {
    if (isSelfOrUnknown(event.feedAuthorId(), event.commenterId())) {
      return;
    }
    notifySafely("댓글", () -> notificationService.create(
        event.feedAuthorId(),
        "새 댓글",
        nameOf(event.commenterId()) + "님: " + preview(event.content()),
        NotificationLevel.INFO
    ));
  }

  // 팔로우한 사용자가 피드 등록
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(FeedCreatedEvent event) {
    notifySafely("새 피드", () -> {
      List<UUID> followerIds = followRepository.findByFollowingId(event.authorId(), Pageable.unpaged())
          .map(Follow::getFollowerId)
          .toList();
      if (followerIds.isEmpty()) {
        return;
      }
      notificationService.createAll(
          followerIds,
          "새 피드",
          nameOf(event.authorId()) + "님이 새 피드를 올렸습니다.",
          NotificationLevel.INFO
      );
    });
  }

  // 권한 변경
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(UserRoleChangedEvent event) {
    notifySafely("권한 변경", () -> notificationService.create(
        event.userId(),
        "권한 변경",
        "회원님의 권한이 " + roleName(event.newRole()) + "로 변경되었습니다.",
        NotificationLevel.INFO
    ));
  }

  // 의상 속성 추가
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(ClothesAttributeDefCreatedEvent event) {
    notifySafely("의상 속성 추가", () -> notificationService.createAll(
        userRepository.findAll().stream().map(User::getId).toList(),
        "새 의상 속성",
        "'" + event.name() + "' 속성이 추가되었습니다.",
        NotificationLevel.INFO
    ));
  }

  // 특별한 날씨 변화: 프로필 위치 격자가 같은 사용자에게 변화 종류별로 하루 한 번
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(WeatherChangeDetectedEvent event) {
    notifySafely("날씨 변화", () -> {
      WeatherLocation location = weatherLocationRepository.findById(event.locationId()).orElse(null);
      if (location == null) {
        return;
      }
      String date = event.forecastAt().atZone(KMA_ZONE).format(WEATHER_DATE);
      List<UUID> receiverIds = profileRepository.findAll().stream()
          .filter(profile -> isSameGrid(profile, location))
          .map(Profile::getUserId)
          .toList();

      for (WeatherChangeType change : event.changes()) {
        String content = date + " " + describe(change) + " 옷차림에 참고하세요.";
        for (UUID receiverId : receiverIds) {
          if (!notificationRepository.existsByReceiver_IdAndContent(receiverId, content)) {
            notificationService.create(receiverId, "날씨 변화", content, NotificationLevel.WARNING);
          }
        }
      }
    });
  }

  private void notifySafely(String type, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException exception) {
      log.warn("{} 알림 생성 실패: exceptionType={}", type, exception.getClass().getSimpleName());
    }
  }

  private boolean isSelfOrUnknown(UUID receiverId, UUID actorId) {
    return receiverId == null || receiverId.equals(actorId);
  }

  private String nameOf(UUID userId) {
    return userRepository.findById(userId).map(User::getName).orElse("알 수 없는 사용자");
  }

  private String preview(String content) {
    if (content == null) {
      return "";
    }
    return content.length() > COMMENT_PREVIEW_LENGTH
        ? content.substring(0, COMMENT_PREVIEW_LENGTH) + "…"
        : content;
  }

  private String roleName(UserRole role) {
    return role == UserRole.ADMIN ? "관리자(ADMIN)" : "일반 사용자(USER)";
  }

  private boolean isSameGrid(Profile profile, WeatherLocation location) {
    Map<String, Object> profileLocation = profile.getLocation();
    if (profileLocation == null) {
      return false;
    }
    return gridValue(profileLocation.get("x")) == location.getGridX()
        && gridValue(profileLocation.get("y")) == location.getGridY();
  }

  private int gridValue(Object value) {
    return value instanceof Number number ? number.intValue() : Integer.MIN_VALUE;
  }

  private String describe(WeatherChangeType change) {
    return switch (Objects.requireNonNull(change)) {
      case PRECIPITATION_STARTED -> "비 또는 눈 소식이 있어요.";
      case STRONG_WIND_STARTED -> "강한 바람이 예보됐어요.";
      case TEMPERATURE_CHANGED_SIGNIFICANTLY -> "기온이 크게 변해요.";
    };
  }
}
