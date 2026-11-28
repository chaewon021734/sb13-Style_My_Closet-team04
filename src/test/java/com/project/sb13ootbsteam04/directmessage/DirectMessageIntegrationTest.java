package com.project.sb13ootbsteam04.directmessage;

import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.directmessage.repository.DirectMessageRepository;
import com.project.sb13ootbsteam04.directmessage.service.DirectMessageService;
import com.project.sb13ootbsteam04.directmessage.websocket.DirectMessageRedisChannel;
import com.project.sb13ootbsteam04.global.support.RedisTestContainer;
import com.project.sb13ootbsteam04.notification.entity.Notification;
import com.project.sb13ootbsteam04.notification.repository.NotificationRepository;
import com.project.sb13ootbsteam04.user.entity.Profile;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.ProfileRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

// 실제 서버에 SockJS·STOMP로 연결해 DM 송수신을 검증한다. Redis는 테스트용 컨테이너.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("DM 실시간 송수신")
class DirectMessageIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private DirectMessageRepository directMessageRepository;
    @Autowired private DirectMessageService directMessageService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ObjectMapper objectMapper;

    @MockitoSpyBean
    private StringRedisTemplate redisTemplate;

    private final List<StompSession> sessions = new ArrayList<>();

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
    void disconnect() {
        sessions.forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        });
    }

    @Test
    @DisplayName("보낸 메시지를 보낸 사람과 받는 사람이 모두 실시간으로 받고 DB에 저장된다")
    void deliversToBothParticipantsAndSaves() throws Exception {
        var sender = saveUser("보내는사람");
        var receiver = saveUser("받는사람");
        var senderInbox = subscribe(connect(sender), sender, receiver);
        var receiverInbox = subscribe(connect(receiver), sender, receiver);

        send(sessions.get(0), sender.getId(), receiver.getId(), "안녕하세요");

        for (var inbox : List.of(senderInbox, receiverInbox)) {
            var message = await(inbox);
            assertThat(message.get("sender").get("userId").asString()).isEqualTo(sender.getId().toString());
            assertThat(message.get("receiver").get("userId").asString()).isEqualTo(receiver.getId().toString());
            assertThat(message.get("content").asString()).isEqualTo("안녕하세요");
        }
        assertThat(directMessageRepository.countMessagesBetweenUsers(sender.getId(), receiver.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("본문의 보낸 사람을 바꿔도 연결한 사용자가 보낸 사람으로 저장된다")
    void usesConnectedUserAsSender() throws Exception {
        var actual = saveUser("실제");
        var forged = saveUser("위조");
        var inbox = subscribe(connect(actual), actual, forged);

        send(sessions.get(0), forged.getId(), forged.getId(), "위조 시도");

        var message = await(inbox);
        assertThat(message.get("sender").get("userId").asString()).isEqualTo(actual.getId().toString());
        assertThat(message.get("receiver").get("userId").asString()).isEqualTo(forged.getId().toString());
    }

    @Test
    @DisplayName("토큰이 없거나 잘못되면 연결을 거부한다")
    void rejectsInvalidToken() {
        assertThatThrownBy(() -> connectWithToken(null)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> connectWithToken("invalid-token")).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("임시 로그인 토큰으로는 연결할 수 없다")
    void rejectsTemporaryToken() {
        var user = saveUser("임시");

        assertThatThrownBy(() -> connectWithToken(jwtTokenProvider.generateTemporaryAccessToken(user)))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("다른 서버가 Redis로 발행한 메시지도 구독자가 받는다")
    void deliversMessagePublishedByAnotherInstance() throws Exception {
        var sender = saveUser("다른서버");
        var receiver = saveUser("이서버");
        var inbox = subscribe(connect(receiver), sender, receiver);
        var message = Map.of(
                "id", UUID.randomUUID(),
                "createdAt", Instant.now(),
                "sender", Map.of("userId", sender.getId(), "name", sender.getName()),
                "receiver", Map.of("userId", receiver.getId(), "name", receiver.getName()),
                "content", "다른 서버에서 보냄");

        redisTemplate.convertAndSend(DirectMessageRedisChannel.NAME, objectMapper.writeValueAsString(
                Map.of("dmKey", dmKey(sender, receiver), "message", message)));

        assertThat(await(inbox).get("content").asString()).isEqualTo("다른 서버에서 보냄");
    }

    @Test
    @DisplayName("Redis 발행이 실패해도 같은 서버 구독자는 메시지를 받는다")
    void deliversLocallyWhenRedisPublishFails() throws Exception {
        var sender = saveUser("장애보냄");
        var receiver = saveUser("장애받음");
        var inbox = subscribe(connect(receiver), sender, receiver);
        var senderSession = connect(sender);
        doThrow(new RedisConnectionFailureException("연결 실패"))
                .when(redisTemplate).convertAndSend(anyString(), anyString());

        send(senderSession, sender.getId(), receiver.getId(), "Redis 장애 중");

        assertThat(await(inbox).get("content").asString()).isEqualTo("Redis 장애 중");
    }

    @Test
    @DisplayName("메시지를 받으면 받는 사람에게 '새 메시지' 알림이 저장된다")
    void notifiesReceiver() throws Exception {
        var sender = saveUser("알림보냄");
        var receiver = saveUser("알림받음");
        var inbox = subscribe(connect(receiver), sender, receiver);

        send(connect(sender), sender.getId(), receiver.getId(), "알림 확인");
        await(inbox);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        List<Notification> notifications = List.of();
        while (notifications.isEmpty() && System.nanoTime() < deadline) {
            notifications = notificationRepository.findFirstPage(receiver.getId(), PageRequest.of(0, 10));
            Thread.sleep(100);
        }
        assertThat(notifications).extracting(Notification::getTitle, Notification::getContent)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("새 메시지", "알림보냄님: 알림 확인"));
    }

    @Test
    @DisplayName("대화 조회는 두 사람의 최신 메시지를 커서 7필드와 보낸 사람 요약으로 응답한다")
    void listsConversationWithUserSummary() throws Exception {
        var me = saveUser("나");
        var partner = saveUser("상대");
        var other = saveUser("다른대화");
        saveProfileImage(partner, "https://cdn.test/partner.png");
        directMessageService.send(me.getId(), partner.getId(), "첫째");
        directMessageService.send(partner.getId(), me.getId(), "둘째");
        directMessageService.send(me.getId(), partner.getId(), "셋째");
        directMessageService.send(me.getId(), other.getId(), "다른 대화");

        var request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/api/direct-messages?userId=" + partner.getId() + "&limit=2"))
                .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(me))
                .GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        var body = objectMapper.readTree(response.body());
        assertThat(body.get("data")).hasSize(2);
        assertThat(body.get("data").get(0).get("content").asString()).isEqualTo("셋째");
        assertThat(body.get("data").get(1).get("content").asString()).isEqualTo("둘째");
        var partnerSummary = body.get("data").get(1).get("sender");
        assertThat(partnerSummary.get("userId").asString()).isEqualTo(partner.getId().toString());
        assertThat(partnerSummary.get("name").asString()).isEqualTo("상대");
        assertThat(partnerSummary.get("profileImageUrl").asString()).isEqualTo("https://cdn.test/partner.png");
        assertThat(body.get("hasNext").asBoolean()).isTrue();
        assertThat(body.get("totalCount").asLong()).isEqualTo(3);
        assertThat(body.get("sortBy").asString()).isEqualTo("createdAt");
        assertThat(body.get("sortDirection").asString()).isEqualTo("DESCENDING");
        assertThat(body.has("nextCursor")).isTrue();
        assertThat(body.has("nextIdAfter")).isTrue();
    }

    @Test
    @DisplayName("대화 참여자가 아니면 그 대화 채널을 구독해도 메시지를 받지 못한다")
    void outsiderCannotSubscribeToConversation() throws Exception {
        var sender = saveUser("참여자1");
        var receiver = saveUser("참여자2");
        var outsider = saveUser("제3자");
        var outsiderInbox = subscribe(connect(outsider), sender, receiver);
        var receiverInbox = subscribe(connect(receiver), sender, receiver);

        send(connect(sender), sender.getId(), receiver.getId(), "둘만의 대화");

        await(receiverInbox);
        assertNothingReceived(outsiderInbox);
    }

    @Test
    @DisplayName("구독 채널로 직접 보낸 메시지는 구독자에게 전달되지 않는다")
    void rejectsSendToBrokerDestination() throws Exception {
        var sender = saveUser("위조대상1");
        var receiver = saveUser("위조대상2");
        var attacker = saveUser("위조자");
        var receiverInbox = subscribe(connect(receiver), sender, receiver);

        connect(attacker).send("/sub/direct-messages_" + dmKey(sender, receiver), Map.of(
                "sender", Map.of("userId", sender.getId(), "name", sender.getName()),
                "receiver", Map.of("userId", receiver.getId(), "name", receiver.getName()),
                "content", "가짜 메시지"));

        assertNothingReceived(receiverInbox);
    }

    @Test
    @DisplayName("연결 후 계정이 잠기면 메시지를 보낼 수 없다")
    void rejectsSendAfterAccountLocked() throws Exception {
        var sender = saveUser("잠길사용자");
        var receiver = saveUser("잠금받는사람");
        var receiverInbox = subscribe(connect(receiver), sender, receiver);
        var senderSession = connect(sender);
        sender.changeLocked(true);
        userRepository.saveAndFlush(sender);

        send(senderSession, sender.getId(), receiver.getId(), "잠긴 뒤 전송");

        assertNothingReceived(receiverInbox);
        assertThat(directMessageRepository.countMessagesBetweenUsers(sender.getId(), receiver.getId())).isZero();
    }

    private User saveUser(String name) {
        return userRepository.save(new User(name, UUID.randomUUID() + "@test.io", "encoded"));
    }

    private void saveProfileImage(User user, String imageUrl) {
        var profile = BeanUtils.instantiateClass(Profile.class);
        ReflectionTestUtils.setField(profile, "user", user);
        ReflectionTestUtils.setField(profile, "profileImageUrl", imageUrl);
        profileRepository.saveAndFlush(profile);
    }

    private String dmKey(User first, User second) {
        var a = first.getId().toString();
        var b = second.getId().toString();
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    private StompSession connect(User user) throws Exception {
        var session = connectWithToken(jwtTokenProvider.generateAccessToken(user));
        sessions.add(session);
        return session;
    }

    private StompSession connectWithToken(String accessToken) throws Exception {
        var client = new WebSocketStompClient(new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new JacksonJsonMessageConverter());
        var connectHeaders = new StompHeaders();
        if (accessToken != null) {
            connectHeaders.add("Authorization", "Bearer " + accessToken);
        }
        return client.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                        connectHeaders, new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<JsonNode> subscribe(StompSession session, User first, User second) throws Exception {
        BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
        session.subscribe("/sub/direct-messages_" + dmKey(first, second), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add(objectMapper.readTree((byte[]) payload));
            }
        });
        // 구독이 서버에 등록될 시간
        Thread.sleep(300);
        return inbox;
    }

    private void send(StompSession session, UUID senderId, UUID receiverId, String content) {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("senderId", senderId);
        payload.put("receiverId", receiverId);
        payload.put("content", content);
        session.send("/pub/direct-messages_send", payload);
    }

    private void assertNothingReceived(BlockingQueue<JsonNode> inbox) throws InterruptedException {
        assertThat(inbox.poll(2, TimeUnit.SECONDS)).as("전달되지 않아야 할 메시지").isNull();
    }

    private JsonNode await(BlockingQueue<JsonNode> inbox) throws InterruptedException {
        var message = inbox.poll(5, TimeUnit.SECONDS);
        assertThat(message).as("DM 수신").isNotNull();
        return message;
    }
}
