package com.project.sb13ootbsteam04.notification.service;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.notification.dto.NotificationDto;
import com.project.sb13ootbsteam04.notification.entity.Notification;
import com.project.sb13ootbsteam04.notification.entity.NotificationLevel;
import com.project.sb13ootbsteam04.notification.exception.NotificationException;
import com.project.sb13ootbsteam04.notification.repository.NotificationRepository;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 서비스")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationService notificationService;

    private final UUID receiverId = UUID.randomUUID();

    @Test
    @DisplayName("다음 페이지가 있으면 마지막 알림으로 커서를 만들고 7필드로 응답한다")
    void returnsFirstPageWithNextCursor() {
        var receiver = user(receiverId);
        var first = notification(receiver, "2026-11-28T02:00:00Z");
        var second = notification(receiver, "2026-11-28T01:00:00Z");
        var extra = notification(receiver, "2026-11-28T00:00:00Z");
        when(notificationRepository.findFirstPage(eq(receiverId), any(Pageable.class)))
                .thenReturn(List.of(first, second, extra));
        when(notificationRepository.countByReceiver_Id(receiverId)).thenReturn(3L);

        var response = notificationService.getNotifications(receiverId, null, null, 2);

        assertThat(response.data()).extracting(NotificationDto::id).containsExactly(first.getId(), second.getId());
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo("2026-11-28T01:00:00Z");
        assertThat(response.nextIdAfter()).isEqualTo(second.getId());
        assertThat(response.totalCount()).isEqualTo(3L);
        assertThat(response.sortBy()).isEqualTo("createdAt");
        assertThat(response.sortDirection()).isEqualTo("DESCENDING");
        assertThat(response.data().get(0).receiverId()).isEqualTo(receiverId);
    }

    @Test
    @DisplayName("마지막 페이지면 다음 커서를 비운다")
    void returnsLastPageWithoutCursor() {
        var receiver = user(receiverId);
        var only = notification(receiver, "2026-11-28T00:00:00Z");
        var cursor = Instant.parse("2026-11-28T01:00:00Z");
        var idAfter = UUID.randomUUID();
        when(notificationRepository.findNextPage(eq(receiverId), eq(cursor), eq(idAfter), any(Pageable.class)))
                .thenReturn(List.of(only));
        when(notificationRepository.countByReceiver_Id(receiverId)).thenReturn(5L);

        var response = notificationService.getNotifications(receiverId, cursor.toString(), idAfter, 20);

        assertThat(response.data()).hasSize(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.nextIdAfter()).isNull();
    }

    @Test
    @DisplayName("잘못된 커서나 limit은 거부한다")
    void rejectsInvalidPagingParameters() {
        assertThatThrownBy(() -> notificationService.getNotifications(receiverId, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notificationService.getNotifications(receiverId, "not-a-time", UUID.randomUUID(), 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notificationService.getNotifications(receiverId, "2026-11-28T00:00:00Z", null, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("없는 알림을 삭제하면 404")
    void rejectsMissingNotification() {
        var missingId = UUID.randomUUID();
        when(notificationRepository.findById(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> notificationService.delete(missingId, receiverId))
                .isInstanceOfSatisfying(NotificationException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND));
    }

    private User user(UUID id) {
        var user = new User("사용자", id + "@test.io", "encoded");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Notification notification(User receiver, String createdAt) {
        var notification = new Notification(receiver, "제목", "내용", NotificationLevel.INFO);
        ReflectionTestUtils.setField(notification, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(notification, "createdAt", Instant.parse(createdAt));
        return notification;
    }
}
