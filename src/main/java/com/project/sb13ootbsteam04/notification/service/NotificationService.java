package com.project.sb13ootbsteam04.notification.service;

import com.project.sb13ootbsteam04.notification.dto.NotificationDto;
import com.project.sb13ootbsteam04.notification.dto.NotificationDtoCursorResponse;
import com.project.sb13ootbsteam04.notification.entity.Notification;
import com.project.sb13ootbsteam04.notification.entity.NotificationLevel;
import com.project.sb13ootbsteam04.notification.event.NotificationCreatedEvent;
import com.project.sb13ootbsteam04.notification.exception.NotificationException;
import com.project.sb13ootbsteam04.notification.repository.NotificationRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

  private static final String SORT_BY = "createdAt";
  private static final String SORT_DIRECTION = "DESCENDING";

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  // 알림 목록 조회 (최신순 커서 페이지)
  public NotificationDtoCursorResponse getNotifications(
      UUID receiverId,
      String cursor,
      UUID idAfter,
      int limit
  ) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
    }

    // 다음 페이지 여부 확인을 위해 한 건 더 조회
    PageRequest pageable = PageRequest.of(0, limit + 1);
    List<Notification> notifications = hasCursor(cursor)
        ? notificationRepository.findNextPage(receiverId, parseCursor(cursor), requireIdAfter(idAfter), pageable)
        : notificationRepository.findFirstPage(receiverId, pageable);

    boolean hasNext = notifications.size() > limit;
    List<Notification> page = hasNext ? notifications.subList(0, limit) : notifications;
    Notification last = hasNext ? page.get(page.size() - 1) : null;

    return new NotificationDtoCursorResponse(
        page.stream().map(NotificationDto::from).toList(),
        last == null ? null : last.getCreatedAt().toString(),
        last == null ? null : last.getId(),
        hasNext,
        notificationRepository.countByReceiver_Id(receiverId),
        SORT_BY,
        SORT_DIRECTION
    );
  }

  // 알림 삭제 (읽음 처리)
  @Transactional
  public void delete(UUID notificationId, UUID receiverId) {
    Notification notification = notificationRepository.findById(notificationId)
        .orElseThrow(() -> NotificationException.notFound(notificationId));

    if (!notification.getReceiver().getId().equals(receiverId)) {
      throw NotificationException.forbidden(notificationId);
    }

    notificationRepository.delete(notification);
  }

  // 알림 저장 후 실시간 전송을 위한 생성 이벤트 발행
  @Transactional
  public NotificationDto create(UUID receiverId, String title, String content, NotificationLevel level) {
    Notification saved = notificationRepository.save(
        new Notification(userRepository.getReferenceById(receiverId), title, content, level));
    NotificationDto dto = NotificationDto.from(saved);
    eventPublisher.publishEvent(new NotificationCreatedEvent(dto));
    return dto;
  }

  // 같은 내용의 알림을 여러 수신자에게 저장
  @Transactional
  public List<NotificationDto> createAll(
      Collection<UUID> receiverIds,
      String title,
      String content,
      NotificationLevel level
  ) {
    List<Notification> notifications = receiverIds.stream()
        .map(receiverId -> new Notification(userRepository.getReferenceById(receiverId), title, content, level))
        .toList();
    List<NotificationDto> dtos = notificationRepository.saveAll(notifications).stream()
        .map(NotificationDto::from)
        .toList();
    dtos.forEach(dto -> eventPublisher.publishEvent(new NotificationCreatedEvent(dto)));
    return dtos;
  }

  private boolean hasCursor(String cursor) {
    return cursor != null && !cursor.isBlank();
  }

  private Instant parseCursor(String cursor) {
    try {
      return Instant.parse(cursor);
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다.");
    }
  }

  private UUID requireIdAfter(UUID idAfter) {
    if (idAfter == null) {
      throw new IllegalArgumentException("cursor와 idAfter는 함께 전달해야 합니다.");
    }
    return idAfter;
  }
}
