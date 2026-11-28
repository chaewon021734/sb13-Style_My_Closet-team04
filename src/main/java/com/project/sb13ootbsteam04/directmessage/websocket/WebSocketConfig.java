package com.project.sb13ootbsteam04.directmessage.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// DM 실시간 송수신: SockJS /ws, 구독 /sub/..., 전송 /pub/...
// 구독자 목록은 서버마다 따로 있으므로 서버 간 전달은 Redis 채널로 한다(DirectMessageRelay).
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final StompAuthInterceptor stompAuthInterceptor;

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws").withSockJS();
    // 같은 연결에서 보낸 메시지를 보낸 순서대로 처리(빠른 연속 전송의 저장 순서 보장)
    registry.setPreserveReceiveOrder(true);
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/sub");
    registry.setApplicationDestinationPrefixes("/pub");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(stompAuthInterceptor);
  }
}
