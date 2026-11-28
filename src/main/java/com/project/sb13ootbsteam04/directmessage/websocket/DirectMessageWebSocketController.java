package com.project.sb13ootbsteam04.directmessage.websocket;

import com.project.sb13ootbsteam04.directmessage.dto.request.DirectMessageRequest;
import com.project.sb13ootbsteam04.directmessage.service.DirectMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DirectMessageWebSocketController {

  private final DirectMessageService directMessageService;

  // 보낸 사람은 요청 본문이 아니라 연결한 사용자로 정한다
  @MessageMapping("/direct-messages_send")
  public void send(DirectMessageRequest request, Principal principal) {
    directMessageService.send(UUID.fromString(principal.getName()), request.receiverId(), request.content());
  }

  // 잘못된 메시지(빈 내용·300자 초과·자기 자신 등)는 저장하지 않고 연결은 유지한다
  @MessageExceptionHandler
  public void handleInvalidMessage(RuntimeException exception) {
    log.warn("DM을 보내지 못했습니다. exceptionType={}, message={}",
        exception.getClass().getSimpleName(), exception.getMessage());
  }
}
