package com.project.sb13ootbsteam04.directmessage.websocket;

import com.project.sb13ootbsteam04.auth.jwt.JwtAccountStatusValidator;
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
import java.util.Map;

// STOMP 연결 시 Authorization 헤더의 JWT로 사용자를 확인하고,
// 구독은 자신이 참여한 대화 채널만, 전송은 /pub 경로만 허용한다.
// 전송할 때마다 계정 상태(잠금·권한 변경·로그아웃)를 다시 확인한다.
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String APPLICATION_PREFIX = "/pub/";
  private static final String JWT_ATTRIBUTE = "jwt";

  private final JwtDecoder jwtDecoder;
  private final JwtAccountStatusValidator accountStatusValidator;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }
    StompCommand command = accessor.getCommand();
    if (command == StompCommand.CONNECT) {
      Jwt jwt = authenticate(accessor.getFirstNativeHeader("Authorization"));
      sessionAttributes(accessor).put(JWT_ATTRIBUTE, jwt);
      accessor.setUser(new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, List.of()));
    } else if (command == StompCommand.SUBSCRIBE) {
      checkSubscription(accessor.getUser(), accessor.getDestination());
    } else if (command == StompCommand.SEND) {
      checkSend(accessor);
    }
    return message;
  }

  // 구독 경로(/sub)로 직접 보내면 서버를 거치지 않고 구독자에게 전달되므로 막는다
  private void checkSend(StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    if (destination == null || !destination.startsWith(APPLICATION_PREFIX)) {
      throw new MessageDeliveryException("전송할 수 없는 경로입니다.");
    }
    if (!(sessionAttributes(accessor).get(JWT_ATTRIBUTE) instanceof Jwt jwt)
        || accountStatusValidator.validate(jwt).hasErrors()) {
      throw new MessageDeliveryException("인증 정보가 더 이상 유효하지 않습니다.");
    }
  }

  private Map<String, Object> sessionAttributes(StompHeaderAccessor accessor) {
    Map<String, Object> attributes = accessor.getSessionAttributes();
    if (attributes == null) {
      throw new MessageDeliveryException("세션 정보가 없습니다.");
    }
    return attributes;
  }

  private Jwt authenticate(String authorization) {
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
    return jwt;
  }

  private void checkSubscription(Principal user, String destination) {
    if (user == null || !DirectMessageRedisChannel.isParticipant(destination, user.getName())) {
      throw new MessageDeliveryException("구독할 수 없는 채널입니다.");
    }
  }
}
