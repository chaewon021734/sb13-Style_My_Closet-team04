package com.project.sb13ootbsteam04.notification.controller;

import com.project.sb13ootbsteam04.global.exception.GlobalExceptionHandler;
import com.project.sb13ootbsteam04.global.security.JwtUserIdResolver;
import com.project.sb13ootbsteam04.notification.dto.NotificationDto;
import com.project.sb13ootbsteam04.notification.dto.NotificationDtoCursorResponse;
import com.project.sb13ootbsteam04.notification.entity.NotificationLevel;
import com.project.sb13ootbsteam04.notification.exception.NotificationException;
import com.project.sb13ootbsteam04.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("알림 컨트롤러")
class NotificationControllerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final JwtUserIdResolver jwtUserIdResolver = mock(JwtUserIdResolver.class);
    private final UUID userId = UUID.randomUUID();
    private final Authentication authentication = new JwtAuthenticationToken(
            Jwt.withTokenValue("token").header("alg", "HS256").subject(userId.toString()).build(), List.of());
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(jwtUserIdResolver.resolve(any())).thenReturn(userId);
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService, jwtUserIdResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("알림 목록을 명세의 커서 응답 7필드로 반환한다")
    void returnsCursorResponse() throws Exception {
        var notificationId = UUID.randomUUID();
        var idAfter = UUID.randomUUID();
        var dto = new NotificationDto(notificationId, Instant.parse("2026-11-28T00:00:00Z"), userId,
                "새 팔로워", "홍길동님이 회원님을 팔로우했습니다.", NotificationLevel.INFO);
        when(notificationService.getNotifications(userId, "2026-11-28T01:00:00Z", idAfter, 20))
                .thenReturn(new NotificationDtoCursorResponse(List.of(dto), null, null, false, 1L,
                        "createdAt", "DESCENDING"));

        mvc.perform(get("/api/notifications")
                        .param("cursor", "2026-11-28T01:00:00Z")
                        .param("idAfter", idAfter.toString())
                        .param("limit", "20")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-11-28T00:00:00Z"))
                .andExpect(jsonPath("$.data[0].receiverId").value(userId.toString()))
                .andExpect(jsonPath("$.data[0].title").value("새 팔로워"))
                .andExpect(jsonPath("$.data[0].content").value("홍길동님이 회원님을 팔로우했습니다."))
                .andExpect(jsonPath("$.data[0].level").value("INFO"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.sortBy").value("createdAt"))
                .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));
    }

    @Test
    @DisplayName("limit이 없으면 400")
    void rejectsMissingLimit() throws Exception {
        mvc.perform(get("/api/notifications").principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("알림을 삭제하면 204")
    void deletesNotification() throws Exception {
        var notificationId = UUID.randomUUID();

        mvc.perform(delete("/api/notifications/{notificationId}", notificationId).principal(authentication))
                .andExpect(status().isNoContent());

        verify(notificationService).delete(notificationId, userId);
    }

    @Test
    @DisplayName("없는 알림 404, 다른 사용자의 알림 403을 오류 본문과 함께 반환한다")
    void returnsErrorStatus() throws Exception {
        var missing = UUID.randomUUID();
        var foreign = UUID.randomUUID();
        doThrow(NotificationException.notFound(missing)).when(notificationService).delete(missing, userId);
        doThrow(NotificationException.forbidden(foreign)).when(notificationService).delete(foreign, userId);

        mvc.perform(delete("/api/notifications/{notificationId}", missing).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 알림입니다."));
        mvc.perform(delete("/api/notifications/{notificationId}", foreign).principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 알림만 처리할 수 있습니다."));
    }
}
