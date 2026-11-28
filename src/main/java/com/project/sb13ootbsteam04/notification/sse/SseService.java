package com.project.sb13ootbsteam04.notification.sse;

import com.project.sb13ootbsteam04.notification.dto.NotificationDto;
import com.project.sb13ootbsteam04.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseService {

  private static final String EVENT_NAME = "notifications";
  private final SseEmitterRegistry registry;
  private final NotificationRepository notificationRepository;

  // 연결 유지 시간. 끝나면 프론트가 다시 연결한다.
  @Value("${otboo.sse.timeout:30m}")
  private Duration timeout = Duration.ofMinutes(30);

  // 연결을 등록하고, 끊긴 사이에 저장된 알림이 있으면 다시 보낸다
  @Transactional(readOnly = true)
  public SseEmitter connect(UUID userId, UUID lastEventId) {
    SseEmitter emitter = new SseEmitter(timeout.toMillis());
    registry.add(userId, emitter);

    // 연결 직후 한 번 보내 응답을 시작한다
    if (!send(userId, emitter, SseEmitter.event().name("connect").data("connected"))) {
      return emitter;
    }

    if (lastEventId != null) {
      missedNotifications(userId, lastEventId)
          .forEach(notification -> send(userId, emitter, notificationEvent(notification)));
    }
    return emitter;
  }

  // 이 인스턴스에 연결된 수신자에게만 전송
  public void send(NotificationDto notification) {
    for (SseEmitter emitter : registry.emittersOf(notification.receiverId())) {
      send(notification.receiverId(), emitter, notificationEvent(notification));
    }
  }

  // 프론트(45초)와 로드 밸런서의 유휴 연결 종료를 막기 위한 주기 전송
  @Scheduled(fixedRate = 30_000)
  public void sendHeartbeat() {
    registry.all().forEach((userId, emitters) ->
        emitters.forEach(emitter -> send(userId, emitter, SseEmitter.event().comment("heartbeat"))));
  }

  private List<NotificationDto> missedNotifications(UUID userId, UUID lastEventId) {
    return notificationRepository.findById(lastEventId)
        .filter(last -> last.getReceiver().getId().equals(userId))
        .map(last -> notificationRepository.findAfter(userId, last.getCreatedAt(), last.getId()))
        .orElse(List.of())
        .stream()
        .map(NotificationDto::from)
        .toList();
  }

  private SseEmitter.SseEventBuilder notificationEvent(NotificationDto notification) {
    return SseEmitter.event()
        .id(notification.id().toString())
        .name(EVENT_NAME)
        .data(notification);
  }

  // 전송에 실패한 연결은 정리한다
  private boolean send(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
    try {
      emitter.send(event);
      return true;
    } catch (IOException | IllegalStateException exception) {
      registry.remove(userId, emitter);
      log.debug("SSE 전송 실패로 연결을 정리합니다. userId={}", userId);
      return false;
    }
  }
}
