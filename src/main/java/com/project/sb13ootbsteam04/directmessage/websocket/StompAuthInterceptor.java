package com.project.sb13ootbsteam04.directmessage.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

// STOMP 연결 시 Authorization 헤더의 JWT로 사용자를 확인하고,
// 구독은 자신이 참여한 대화 채널만 허용한다.
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtDecoder jwtDecoder;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }
    StompCommand command = accessor.getCommand();
    if (command == StompCommand.CONNECT) {
      accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
    } else if (command == StompCommand.SUBSCRIBE) {
      checkSubscription(accessor.getUser(), accessor.getDestination());
    } else if (command == StompCommand.SEND && accessor.getUser() == null) {
      throw new MessageDeliveryException("인증되지 않은 연결입니다.");
    }
    return message;
  }

  private Principal authenticate(String authorization) {
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      throw new MessageDeliveryException("인증 토큰이 필요합니다.");
    }
    Jwt jwt;
    try {
      // 서명·만료·계정 잠금·로그인 버전까지 HTTP 요청과 같은 기준으로 검증
      jwt = jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length()));
    } catch (JwtException exception) {
      throw new MessageDeliveryException("유효하지 않은 인증 토큰입니다.");
    }
    // 임시 비밀번호 로그인은 비밀번호 변경만 허용한다
    if ("TEMPORARY".equals(jwt.getClaimAsString("authType"))) {
      throw new MessageDeliveryException("임시 로그인으로는 DM을 사용할 수 없습니다.");
    }
    return new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, List.of());
  }

  private void checkSubscription(Principal user, String destination) {
    if (user == null || !DirectMessageRedisChannel.isParticipant(destination, user.getName())) {
      throw new MessageDeliveryException("구독할 수 없는 채널입니다.");
    }
  }
}
