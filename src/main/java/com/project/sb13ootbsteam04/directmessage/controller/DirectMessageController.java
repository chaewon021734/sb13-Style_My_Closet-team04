package com.project.sb13ootbsteam04.directmessage.controller;

import com.project.sb13ootbsteam04.directmessage.dto.response.DirectMessageCursorResponse;
import com.project.sb13ootbsteam04.directmessage.service.DirectMessageService;
import com.project.sb13ootbsteam04.global.security.JwtUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/direct-messages")
@RequiredArgsConstructor
public class DirectMessageController {

  private final DirectMessageService directMessageService;
  private final JwtUserIdResolver userIdResolver;

  // 나와 상대(userId)의 대화, 최신순 커서 조회
  @GetMapping
  public ResponseEntity<DirectMessageCursorResponse> getMessages(
      Authentication authentication,
      @RequestParam UUID userId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam int limit
  ) {
    return ResponseEntity.ok(directMessageService.getMessages(
        userIdResolver.resolve(authentication), userId, cursor, idAfter, limit));
  }
}
