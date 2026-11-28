package com.project.sb13ootbsteam04.notification.sse;

import com.project.sb13ootbsteam04.global.security.JwtUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SseController {

  private final SseService sseService;
  private final JwtUserIdResolver jwtUserIdResolver;

  // 실시간 알림 구독. 재연결 시 마지막으로 받은 알림 ID를 함께 보낸다.
  @GetMapping(value = "/api/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(
      @RequestParam(name = "lastEventId", required = false) String lastEventId,
      @RequestParam(name = "LastEventId", required = false) String swaggerLastEventId,
      @RequestHeader(name = "Last-Event-ID", required = false) String lastEventIdHeader,
      Authentication authentication
  ) {
    UUID currentUserId = jwtUserIdResolver.resolve(authentication);

    return sseService.connect(currentUserId, firstValidId(lastEventId, swaggerLastEventId, lastEventIdHeader));
  }

  // 사용자가 연결을 끊은 경우: 응답을 보낼 수 없으므로 오류로 처리하지 않고 끝낸다
  @ExceptionHandler(AsyncRequestNotUsableException.class)
  public void handleDisconnected() {
  }

  // 프론트 폴리필(lastEventId), Swagger(LastEventId), 표준 헤더(Last-Event-ID) 순으로 사용
  private UUID firstValidId(String... candidates) {
    for (String candidate : candidates) {
      if (candidate == null || candidate.isBlank()) {
        continue;
      }
      try {
        return UUID.fromString(candidate.trim());
      } catch (IllegalArgumentException ignored) {
        // 형식이 맞지 않으면 다음 값을 사용
      }
    }
    return null;
  }
}
