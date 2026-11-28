package com.project.sb13ootbsteam04.notification.repository;

import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import com.project.sb13ootbsteam04.notification.entity.Notification;
import com.project.sb13ootbsteam04.notification.entity.NotificationLevel;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@DisplayName("알림 저장소")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User receiver;
    private User other;

    @BeforeEach
    void setUp() {
        receiver = userRepository.save(new User("수신자", "receiver@test.io", "encoded"));
        other = userRepository.save(new User("다른 사용자", "other@test.io", "encoded"));
    }

    @Test
    @DisplayName("수신자 알림을 최신순으로 조회하고 다른 사용자 알림은 제외한다")
    void findsReceiverNotificationsNewestFirst() {
        var oldest = save(receiver, "첫 알림", "2026-11-28T00:00:00Z");
        var newest = save(receiver, "셋째 알림", "2026-11-28T02:00:00Z");
        var middle = save(receiver, "둘째 알림", "2026-11-28T01:00:00Z");
        save(other, "다른 사람 알림", "2026-11-28T03:00:00Z");

        var page = notificationRepository.findFirstPage(receiver.getId(), PageRequest.of(0, 10));

        assertThat(page).extracting(Notification::getId)
                .containsExactly(newest.getId(), middle.getId(), oldest.getId());
        assertThat(notificationRepository.countByReceiver_Id(receiver.getId())).isEqualTo(3);
    }

    @Test
    @DisplayName("커서 다음 페이지는 같은 시각이면 id가 작은 알림부터 이어서 조회한다")
    void findsNextPageAfterCursor() {
        var first = save(receiver, "알림1", "2026-11-28T02:00:00Z");
        save(receiver, "알림2", "2026-11-28T01:00:00Z");
        save(receiver, "알림3", "2026-11-28T01:00:00Z");
        var last = save(receiver, "알림4", "2026-11-28T00:00:00Z");
        // 같은 시각의 순서는 DB의 UUID 비교 기준을 따르므로 첫 페이지 결과로 정한다
        // (Java UUID.compareTo는 부호 있는 비교라 DB 정렬과 다를 수 있다)
        var firstPage = notificationRepository.findFirstPage(receiver.getId(), PageRequest.of(0, 2));
        var cursorItem = firstPage.get(1);
        var allInOrder = notificationRepository.findFirstPage(receiver.getId(), PageRequest.of(0, 10));

        var next = notificationRepository.findNextPage(receiver.getId(),
                cursorItem.getCreatedAt(), cursorItem.getId(), PageRequest.of(0, 10));

        // 첫 페이지 2건 뒤의 나머지를 같은 순서로 이어서 조회한다
        assertThat(firstPage.get(0).getId()).isEqualTo(first.getId());
        assertThat(next).extracting(Notification::getId)
                .containsExactlyElementsOf(allInOrder.subList(2, 4).stream().map(Notification::getId).toList());
        assertThat(next.get(next.size() - 1).getId()).isEqualTo(last.getId());
        assertThat(next).extracting(Notification::getId).doesNotContain(first.getId(), cursorItem.getId());
    }

    private Notification save(User user, String title, String createdAt) {
        var notification = notificationRepository.saveAndFlush(
                new Notification(user, title, title + " 내용", NotificationLevel.INFO));
        // 생성 시각은 감사 기능이 채우므로 정렬 검증을 위해 직접 지정한다
        entityManager.createNativeQuery("update notifications set created_at = ? where id = ?")
                .setParameter(1, Instant.parse(createdAt))
                .setParameter(2, notification.getId())
                .executeUpdate();
        entityManager.clear();
        return notificationRepository.findById(notification.getId()).orElseThrow();
    }
}
