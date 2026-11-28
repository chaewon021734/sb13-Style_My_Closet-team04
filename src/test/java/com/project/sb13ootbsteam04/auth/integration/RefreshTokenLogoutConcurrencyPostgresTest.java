package com.project.sb13ootbsteam04.auth.integration;

import com.project.sb13ootbsteam04.auth.dto.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.LoginResult;
import com.project.sb13ootbsteam04.auth.entity.RefreshToken;
import com.project.sb13ootbsteam04.auth.entity.RefreshTokenRevocationReason;
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
import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("postgres-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(
        named = "OTBOO_POSTGRES_TEST",
        matches = "true"
)
class RefreshTokenLogoutConcurrencyPostgresTest {

    private static final String TEST_SCHEMA =
            "otboo_logout_race_"
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
    void shouldInvalidateNewSessionTokensWhenRotationFinishesFirst() {
        LoginResult original = login();
        long before = version();
        LoginResult rotated = refreshTokenService.rotate(raw(original));
        refreshTokenService.logout(raw(original));
        assertThat(version()).isEqualTo(before + 1);
        assertInvalidSession(rotated);
    }

    @Test
    void shouldRejectRotationWhenLogoutFinishesFirst() {
        LoginResult original = login();
        long before = version();
        refreshTokenService.logout(raw(original));
        assertInvalidSession(original);
        assertThat(version()).isEqualTo(before + 1);
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldInvalidateSessionWhenRotationAndLogoutRace() throws Exception {
        assertConcurrentLogout(1);
    }

    @Test
    void shouldRejectRotatedAccessTokenAfterLogout() {
        LoginResult original = login();
        LoginResult rotated = refreshTokenService.rotate(raw(original));
        assertThat(jwtDecoder.decode(rotated.response().accessToken()).getSubject())
                .isEqualTo(user.getId().toString());
        refreshTokenService.logout(raw(original));
        assertThatThrownBy(() -> jwtDecoder.decode(rotated.response().accessToken()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectRotatedRefreshTokenAfterLogout() {
        LoginResult original = login();
        LoginResult rotated = refreshTokenService.rotate(raw(original));
        refreshTokenService.logout(raw(original));
        assertInvalidToken(raw(rotated));
        assertThat(findToken(raw(rotated)).getLoginVersion()).isLessThan(version());
    }

    @Test
    void shouldIgnoreRotationOutsideGracePeriod() {
        LoginResult current = login();
        long before = version();
        User savedUser = userRepository.findById(user.getId()).orElseThrow();
        String oldRaw = refreshTokenGenerator.generate();
        Instant now = Instant.now();
        RefreshToken old = new RefreshToken(savedUser, refreshTokenGenerator.hash(oldRaw),
                now.minusSeconds(120), now.plusSeconds(3600));
        old.revoke(now.minusSeconds(10), RefreshTokenRevocationReason.ROTATED);
        refreshTokenRepository.saveAndFlush(old);
        refreshTokenService.logout(oldRaw);
        assertThat(version()).isEqualTo(before);
        assertThat(jwtDecoder.decode(current.response().accessToken())).isNotNull();
        assertThat(refreshTokenService.rotate(raw(current)).hasRefreshToken()).isTrue();
    }

    @Test
    void shouldProtectNewLoginFromPreviouslyRotatedToken() {
        LoginResult original = login();
        refreshTokenService.rotate(raw(original));
        LoginResult current = login();
        long before = version();
        refreshTokenService.logout(raw(original));
        assertThat(version()).isEqualTo(before);
        assertThat(jwtDecoder.decode(current.response().accessToken())).isNotNull();
        assertThat(refreshTokenService.rotate(raw(current)).hasRefreshToken()).isTrue();
    }

    @Test
    void shouldIncrementVersionOnceWhenMultipleLogoutsRaceWithRotation() throws Exception {
        assertConcurrentLogout(5);
    }

    @Test
    void shouldPreserveRotationRevocationReasonAndTime() {
        LoginResult original = login();
        refreshTokenService.rotate(raw(original));
        RefreshToken before = findToken(raw(original));
        assertThat(before.getRevocationReason()).isEqualTo(RefreshTokenRevocationReason.ROTATED);
        refreshTokenService.logout(raw(original));
        RefreshToken after = findToken(raw(original));
        assertThat(after.getRevocationReason()).isEqualTo(RefreshTokenRevocationReason.ROTATED);
        assertThat(after.getRevokedAt()).isEqualTo(before.getRevokedAt());
        assertThat(version()).isEqualTo(before.getLoginVersion() + 1);
    }

    @Test
    void shouldPersistLogoutRevocationReason() {
        LoginResult original = login();
        refreshTokenService.logout(raw(original));
        RefreshToken saved = findToken(raw(original));
        assertThat(saved.getRevocationReason()).isEqualTo(RefreshTokenRevocationReason.LOGOUT);
        assertThat(saved.getRevokedAt()).isNotNull();
    }

    private void assertConcurrentLogout(int logoutCount) throws Exception {
        LoginResult original = login();
        String token = raw(original);
        long before = version();
        int count = logoutCount + 1;
        var executor = Executors.newFixedThreadPool(count);
        var barrier = new CyclicBarrier(count);
        LoginResult rotated;
        try {
            Future<LoginResult> rotation = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    return refreshTokenService.rotate(token);
                } catch (BadCredentialsException e) {
                    if (!INVALID_TOKEN_MESSAGE.equals(e.getMessage())) {
                        throw e;
                    }
                    return null;
                }
            });
            List<Future<?>> logouts = new ArrayList<>();
            for (int i = 0; i < logoutCount; i++) {
                logouts.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    refreshTokenService.logout(token);
                    return null;
                }));
            }
            rotated = rotation.get(30, TimeUnit.SECONDS);
            for (Future<?> logout : logouts) {
                logout.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 작업이 종료되지 않았습니다.");
            }
        }
        assertThat(version()).isEqualTo(before + 1);
        assertInvalidSession(original);
        RefreshToken saved = findToken(token);
        if (rotated != null) {
            assertThat(saved.getRevocationReason()).isEqualTo(RefreshTokenRevocationReason.ROTATED);
            assertInvalidSession(rotated);
            assertThat(refreshTokenRepository.count()).isEqualTo(2);
        } else {
            assertThat(saved.getRevocationReason()).isEqualTo(RefreshTokenRevocationReason.LOGOUT);
            assertThat(refreshTokenRepository.count()).isEqualTo(1);
        }
    }

    private LoginResult login() {
        return authService.login(new LoginRequest(user.getEmail(), PASSWORD));
    }

    private String raw(LoginResult result) {
        return result.refreshToken().rawToken();
    }

    private long version() {
        return userRepository.findById(user.getId()).orElseThrow().getLoginVersion();
    }

    private RefreshToken findToken(String rawToken) {
        return refreshTokenRepository.findByTokenHash(refreshTokenGenerator.hash(rawToken)).orElseThrow();
    }

    private void assertInvalidToken(String rawToken) {
        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(INVALID_TOKEN_MESSAGE);
    }

    private void assertInvalidSession(LoginResult result) {
        assertInvalidToken(raw(result));
        assertThatThrownBy(() -> jwtDecoder.decode(result.response().accessToken()))
                .isInstanceOf(JwtException.class);
    }
}
