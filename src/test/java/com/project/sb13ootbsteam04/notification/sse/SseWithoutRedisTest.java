package com.project.sb13ootbsteam04.notification.sse;

import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.notification.entity.NotificationLevel;
import com.project.sb13ootbsteam04.notification.service.NotificationService;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Redis에 연결할 수 없는 상태로 서버가 시작되는 경우(장애·배포 직후)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.data.redis.port=1")
@ActiveProfiles("test")
@DisplayName("Redis 없이 시작한 서버의 실시간 알림")
class SseWithoutRedisTest {

    @LocalServerPort private int port;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private NotificationService notificationService;

    @Test
    @DisplayName("Redis가 없어도 서버는 시작되고, 같은 서버에 연결된 사용자는 알림을 받는다")
    void startsAndDeliversLocallyWithoutRedis() throws Exception {
        var user = userRepository.save(new User("사용자", UUID.randomUUID() + "@test.io", "encoded"));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/sse"))
                .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(user))
                .GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        var reader = new Thread(() -> {
            try (var input = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = input.readLine()) != null) {
                    lines.add(line);
                }
            } catch (Exception ignored) {
                // 연결 종료
            }
        });
        reader.setDaemon(true);
        reader.start();
        assertThat(awaitLine(lines, "event:connect")).isTrue();

        notificationService.create(user.getId(), "알림", "Redis 없이 전달", NotificationLevel.INFO);

        assertThat(awaitLine(lines, "event:notifications")).isTrue();
        response.body().close();
    }

    private boolean awaitLine(BlockingQueue<String> lines, String expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            var line = lines.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            if (line != null && line.replace(" ", "").equals(expected)) {
                return true;
            }
        }
        return false;
    }
}
