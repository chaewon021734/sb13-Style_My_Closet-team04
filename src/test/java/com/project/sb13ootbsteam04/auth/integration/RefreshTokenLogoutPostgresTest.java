package com.project.sb13ootbsteam04.auth.integration;

import com.project.sb13ootbsteam04.auth.dto.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.LoginResult;
import com.project.sb13ootbsteam04.auth.entity.RefreshToken;
import com.project.sb13ootbsteam04.auth.repository.RefreshTokenRepository;
import com.project.sb13ootbsteam04.auth.service.AuthService;
import com.project.sb13ootbsteam04.auth.service.RefreshTokenGenerator;
import com.project.sb13ootbsteam04.auth.service.RefreshTokenService;
import com.project.sb13ootbsteam04.integration.PostgresTestDatabaseConfig;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("postgres-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(
        named = "OTBOO_POSTGRES_TEST",
        matches = "true"
)
class RefreshTokenLogoutPostgresTest {

    private static final String TEST_SCHEMA =
            "otboo_logout_test_"
                    + UUID.randomUUID()
                    .toString()
                    .replace("-", "");

    private static final String PASSWORD =
            "TestPassword123!";

    private static final String INVALID_TOKEN_MESSAGE =
            "유효하지 않은 Refresh Token입니다.";

    @DynamicPropertySource
    static void configurePostgres(
            DynamicPropertyRegistry registry
    ) {
        PostgresTestDatabaseConfig.register(
                registry,
                TEST_SCHEMA
        );

        registry.add(
                "weather.batch.schedule.enabled",
                () -> false
        );
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenGenerator refreshTokenGenerator;

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    private User user;

    @BeforeEach
    void setUp() {

        String email = "refresh-test-"
                + UUID.randomUUID()
                + "@example.com";

        user = userRepository.saveAndFlush(
                new User(
                        "테스트사용자",
                        email,
                        passwordEncoder.encode(PASSWORD)
                )
        );
    }

    @AfterEach
    void tearDown() {

        // FK 관계를 고려하여 Refresh Token 먼저 삭제
        refreshTokenRepository.deleteAllInBatch();

        if (user != null) {
            userRepository.deleteAllByIdInBatch(java.util.List.of(user.getId()));
        }
    }

    @Test
    void shouldPersistRevocationAfterLogout() {
        LoginResult result = login();
        String raw = result.refreshToken().rawToken();
        refreshTokenService.logout(raw);
        assertThat(findToken(raw).isRevoked()).isTrue();
        assertThat(findToken(raw).getRevokedAt()).isNotNull();
    }

    @Test
    void shouldIncreaseLoginVersionAfterLogout() {
        LoginResult result = login();
        long before = version();
        refreshTokenService.logout(result.refreshToken().rawToken());
        assertThat(version()).isEqualTo(before + 1);
    }

    @Test
    void shouldRejectAccessTokenAfterLogout() {
        LoginResult result = login();
        String access = result.response().accessToken();
        assertThat(jwtDecoder.decode(access).getSubject())
                .isEqualTo(user.getId().toString());
        refreshTokenService.logout(result.refreshToken().rawToken());
        assertThatThrownBy(() -> jwtDecoder.decode(access))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectRefreshTokenAfterLogout() {
        String raw = login().refreshToken().rawToken();
        refreshTokenService.logout(raw);
        assertInvalidToken(() -> refreshTokenService.rotate(raw));
    }

    @Test
    void shouldNotIncreaseVersionTwiceForRepeatedLogout() {
        String raw = login().refreshToken().rawToken();
        long before = version();
        refreshTokenService.logout(raw);
        Instant revokedAt = findToken(raw).getRevokedAt();
        refreshTokenService.logout(raw);
        assertThat(version()).isEqualTo(before + 1);
        assertThat(findToken(raw).getRevokedAt()).isEqualTo(revokedAt);
    }

    @Test
    void shouldPreserveNewLoginWhenOldTokenLogsOut() {
        String old = login().refreshToken().rawToken();
        LoginResult current = login();
        long before = version();
        refreshTokenService.logout(old);
        assertThat(version()).isEqualTo(before);
        assertThat(jwtDecoder.decode(current.response().accessToken())).isNotNull();
        assertThat(refreshTokenService.rotate(current.refreshToken().rawToken())
                .hasRefreshToken()).isTrue();
    }

    @Test
    void shouldIgnoreExpiredRefreshToken() {
        LoginResult result = login();
        User saved = userRepository.findById(user.getId()).orElseThrow();
        String raw = refreshTokenGenerator.generate();
        Instant now = Instant.now();
        refreshTokenRepository.saveAndFlush(new RefreshToken(saved,
                refreshTokenGenerator.hash(raw), now.minusSeconds(120),
                now.minusSeconds(60)));
        long before = version();
        refreshTokenService.logout(raw);
        assertThat(version()).isEqualTo(before);
        assertThat(findToken(raw).isRevoked()).isFalse();
        assertThat(jwtDecoder.decode(result.response().accessToken())).isNotNull();
    }

    @Test
    void shouldIgnoreUnknownRefreshToken() {
        LoginResult result = login();
        long before = version();
        refreshTokenService.logout(refreshTokenGenerator.generate());
        assertThat(version()).isEqualTo(before);
        assertThat(findToken(result.refreshToken().rawToken()).isRevoked()).isFalse();
    }

    @Test
    void shouldIgnoreMissingAndMalformedRefreshToken() {
        LoginResult result = login();
        long before = version();
        refreshTokenService.logout(null);
        refreshTokenService.logout("");
        refreshTokenService.logout("invalid-token");
        assertThat(version()).isEqualTo(before);
        assertThat(findToken(result.refreshToken().rawToken()).isRevoked()).isFalse();
    }

    @Test
    void shouldIncreaseVersionOnlyOnceForConcurrentLogouts() throws Exception {
        String raw = login().refreshToken().rawToken();
        long before = version();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Callable<Void> task = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                refreshTokenService.logout(raw);
                return null;
            };
            Future<Void> first = executor.submit(task);
            Future<Void> second = executor.submit(task);
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
            assertThat(version()).isEqualTo(before + 1);
            assertThat(findToken(raw).isRevoked()).isTrue();
            assertInvalidToken(() -> refreshTokenService.rotate(raw));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private long version() {
        return userRepository.findById(user.getId()).orElseThrow().getLoginVersion();
    }

    private LoginResult login() {

        return authService.login(
                new LoginRequest(
                        user.getEmail(),
                        PASSWORD
                )
        );
    }

    private RefreshToken findToken(String rawToken) {

        String tokenHash = refreshTokenGenerator.hash(
                rawToken
        );

        return refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow();
    }

    private void assertInvalidToken(Runnable action) {

        assertThatThrownBy(action::run)
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(INVALID_TOKEN_MESSAGE);
    }
}