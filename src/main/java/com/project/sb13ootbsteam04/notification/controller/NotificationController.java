package com.project.sb13ootbsteam04.notification.controller;

import com.project.sb13ootbsteam04.global.security.JwtUserIdResolver;
import com.project.sb13ootbsteam04.notification.dto.NotificationDtoCursorResponse;
import com.project.sb13ootbsteam04.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;
  private final JwtUserIdResolver jwtUserIdResolver;

  // 내 알림 목록 조회
  @GetMapping
  public ResponseEntity<NotificationDtoCursorResponse> getNotifications(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam int limit,
      Authentication authentication
  ) {
    UUID currentUserId = jwtUserIdResolver.resolve(authentication);

    return ResponseEntity.ok(
        notificationService.getNotifications(currentUserId, cursor, idAfter, limit)
    );
  }

  // 알림 삭제 (프론트에서 읽음 처리로 사용)
  @DeleteMapping("/{notificationId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID notificationId,
      Authentication authentication
  ) {
    UUID currentUserId = jwtUserIdResolver.resolve(authentication);

    notificationService.delete(notificationId, currentUserId);

    return ResponseEntity.noContent().build();
  }
}
