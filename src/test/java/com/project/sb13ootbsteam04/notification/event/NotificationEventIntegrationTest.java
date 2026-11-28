package com.project.sb13ootbsteam04.notification.event;

import com.project.sb13ootbsteam04.follow.dto.CreateFollowRequest;
import com.project.sb13ootbsteam04.follow.service.FollowService;
import com.project.sb13ootbsteam04.notification.repository.NotificationRepository;
import com.project.sb13ootbsteam04.notification.service.NotificationService;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("알림 생성 이벤트 통합")
class NotificationEventIntegrationTest {

    @Autowired
    private FollowService followService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoSpyBean
    private NotificationService notificationService;

    @Test
    @DisplayName("팔로우가 커밋된 뒤 팔로우 대상의 알림이 저장된다")
    void savesNotificationAfterFollowCommitted() {
        var follower = saveUser("팔로워");
        var followee = saveUser("팔로우대상");

        followService.createFollow(new CreateFollowRequest(followee.getId(), follower.getId()));

        var page = notificationRepository.findFirstPage(followee.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(page).hasSize(1);
        assertThat(page.get(0).getContent()).isEqualTo("팔로워님이 회원님을 팔로우했습니다.");
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
        assertThat(notificationRepository.countByReceiver_Id(followee.getId())).isZero();
    }

    private User saveUser(String name) {
        return userRepository.save(new User(name, name + "-" + UUID.randomUUID() + "@test.io", "encoded"));
    }
}
