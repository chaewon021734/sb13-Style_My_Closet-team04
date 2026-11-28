package com.project.sb13ootbsteam04.notification.sse;

import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.global.support.RedisTestContainer;
import com.project.sb13ootbsteam04.notification.dto.NotificationDto;
import com.project.sb13ootbsteam04.notification.entity.NotificationLevel;
import com.project.sb13ootbsteam04.notification.service.NotificationService;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

// 실제 서버에 HTTP로 SSE 연결을 열고 스트림으로 받은 내용을 검증한다. Redis는 테스트용 컨테이너.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("실시간 알림(SSE)")
class SseIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @LocalServerPort private int port;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private NotificationService notificationService;
    @Autowired private SseService sseService;
    @Autowired private ObjectMapper objectMapper;

    @MockitoSpyBean
    private StringRedisTemplate redisTemplate;

    private final List<SseStream> streams = new ArrayList<>();

    @BeforeAll
    static void requireRedis() {
        RedisTestContainer.template();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisTestContainer::host);
        registry.add("spring.data.redis.port", RedisTestContainer::port);
    }

    @AfterEach
    void closeStreams() {
        streams.forEach(SseStream::close);
    }

    @Test
    @DisplayName("연결된 사용자에게 알림이 저장되면 notifications 이벤트로 받는다")
    void deliversNotificationToConnectedUser() throws Exception {
        var user = saveUser();
        var stream = connect(user, null);

        var notification = notificationService.create(user.getId(), "새 팔로워", "홍길동님이 회원님을 팔로우했습니다.",
                NotificationLevel.INFO);

        var event = stream.awaitEvent("notifications");
        assertThat(event.id()).isEqualTo(notification.id().toString());
        var data = objectMapper.readTree(event.data());
        assertThat(data.get("id").asString()).isEqualTo(notification.id().toString());
        assertThat(data.get("receiverId").asString()).isEqualTo(user.getId().toString());
        assertThat(data.get("title").asString()).isEqualTo("새 팔로워");
        assertThat(data.get("content").asString()).isEqualTo("홍길동님이 회원님을 팔로우했습니다.");
        assertThat(data.get("level").asString()).isEqualTo("INFO");
        assertThat(data.get("createdAt").isNull()).isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 알림은 받지 않는다")
    void doesNotDeliverOthersNotification() throws Exception {
        var user = saveUser();
        var other = saveUser();
        var stream = connect(user, null);

        notificationService.create(other.getId(), "새 팔로워", "다른 사람 알림", NotificationLevel.INFO);

        assertThat(stream.pollEvent("notifications", Duration.ofSeconds(2))).isNull();
    }

    @Test
    @DisplayName("다른 인스턴스가 Redis로 발행한 알림도 받는다")
    void deliversNotificationPublishedByAnotherInstance() throws Exception {
        var user = saveUser();
        var stream = connect(user, null);
        var notification = new NotificationDto(UUID.randomUUID(), Instant.now(), user.getId(),
                "새 댓글", "다른 서버에서 생긴 알림", NotificationLevel.INFO);

        // 다른 인스턴스가 알림을 저장한 뒤 발행한 메시지와 같은 형식
        redisTemplate.convertAndSend(SseNotificationChannel.NAME, objectMapper.writeValueAsString(
                Map.of("receiverId", user.getId(), "notification", notification)));

        var event = stream.awaitEvent("notifications");
        assertThat(event.id()).isEqualTo(notification.id().toString());
    }

    @Test
    @DisplayName("lastEventId로 다시 연결하면 그 이후 알림을 순서대로 다시 받는다")
    void resendsNotificationsAfterLastEventId() throws Exception {
        var user = saveUser();
        var first = notificationService.create(user.getId(), "알림", "첫 번째", NotificationLevel.INFO);
        var second = notificationService.create(user.getId(), "알림", "두 번째", NotificationLevel.INFO);
        var third = notificationService.create(user.getId(), "알림", "세 번째", NotificationLevel.INFO);

        var stream = connect(user, first.id());

        assertThat(stream.awaitEvent("notifications").id()).isEqualTo(second.id().toString());
        assertThat(stream.awaitEvent("notifications").id()).isEqualTo(third.id().toString());
        assertThat(stream.pollEvent("notifications", Duration.ofSeconds(1))).isNull();
    }

    @Test
    @DisplayName("같은 사용자의 연결 하나가 끊겨도 나머지 연결은 알림을 받는다")
    void deliversToRemainingConnectionWhenOneIsClosed() throws Exception {
        var user = saveUser();
        var closed = connect(user, null);
        var alive = connect(user, null);
        closed.close();

        notificationService.create(user.getId(), "알림", "끊김 이후", NotificationLevel.INFO);

        assertThat(alive.awaitEvent("notifications")).isNotNull();
    }

    @Test
    @DisplayName("Redis 발행이 실패해도 같은 서버에 연결된 사용자는 알림을 받는다")
    void deliversLocallyWhenRedisPublishFails() throws Exception {
        var user = saveUser();
        var stream = connect(user, null);
        doThrow(new RedisConnectionFailureException("연결 실패"))
                .when(redisTemplate).convertAndSend(anyString(), anyString());

        var notification = notificationService.create(user.getId(), "알림", "Redis 장애 중", NotificationLevel.INFO);

        assertThat(stream.awaitEvent("notifications").id()).isEqualTo(notification.id().toString());
    }

    @Test
    @DisplayName("하트비트를 보내면 연결된 스트림에 주석 줄이 온다")
    void sendsHeartbeatComment() throws Exception {
        var user = saveUser();
        var stream = connect(user, null);

        sseService.sendHeartbeat();

        assertThat(stream.awaitComment()).isTrue();
    }

    private User saveUser() {
        return userRepository.save(new User("사용자", UUID.randomUUID() + "@test.io", "encoded"));
    }

    private SseStream connect(User user, UUID lastEventId) throws Exception {
        var query = lastEventId == null ? "" : "?lastEventId=" + lastEventId;
        var stream = SseStream.open(URI.create("http://localhost:" + port + "/api/sse" + query),
                jwtTokenProvider.generateAccessToken(user));
        streams.add(stream);
        // 서버가 보낸 연결 확인 이벤트를 받아 연결이 등록된 것을 보장
        stream.awaitEvent("connect");
        return stream;
    }

    private record SseEvent(String id, String name, String data) {
    }

    // 서버가 보낸 SSE 줄을 읽어 이벤트 단위로 꺼내는 테스트용 클라이언트
    private static final class SseStream {

        private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        private final InputStream body;
        private final Thread reader;

        private SseStream(InputStream body) {
            this.body = body;
            this.reader = new Thread(() -> {
                try (var input = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = input.readLine()) != null) {
                        lines.add(line);
                    }
                } catch (Exception ignored) {
                    // 연결 종료
                }
            });
            this.reader.setDaemon(true);
            this.reader.start();
        }

        static SseStream open(URI uri, String accessToken) throws Exception {
            var request = HttpRequest.newBuilder(uri)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "text/event-stream")
                    .GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
            assertThat(response.statusCode()).isEqualTo(200);
            return new SseStream(response.body());
        }

        SseEvent awaitEvent(String name) throws InterruptedException {
            var event = pollEvent(name, WAIT);
            assertThat(event).as("%s 이벤트 수신", name).isNotNull();
            return event;
        }

        SseEvent pollEvent(String name, Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            String id = null;
            String eventName = null;
            StringBuilder data = new StringBuilder();
            while (System.nanoTime() < deadline) {
                var line = lines.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                if (line == null) {
                    return null;
                }
                if (line.isEmpty()) {
                    if (name.equals(eventName)) {
                        return new SseEvent(id, eventName, data.toString());
                    }
                    id = null;
                    eventName = null;
                    data.setLength(0);
                } else if (line.startsWith("id:")) {
                    id = line.substring(3).trim();
                } else if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).trim());
                }
            }
            return null;
        }

        boolean awaitComment() throws InterruptedException {
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (System.nanoTime() < deadline) {
                var line = lines.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                if (line != null && line.startsWith(":")) {
                    return true;
                }
            }
            return false;
        }

        void close() {
            try {
                body.close();
            } catch (Exception ignored) {
                // 이미 닫힘
            }
            reader.interrupt();
        }
    }
}
