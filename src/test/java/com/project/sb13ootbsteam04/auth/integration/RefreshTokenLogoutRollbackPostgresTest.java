package com.project.sb13ootbsteam04.auth.integration;

import com.project.sb13ootbsteam04.auth.dto.LoginResult;
import com.project.sb13ootbsteam04.auth.entity.RefreshToken;
import com.project.sb13ootbsteam04.auth.entity.RefreshTokenRevocationReason;
import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.auth.repository.RefreshTokenRepository;
import com.project.sb13ootbsteam04.auth.service.RefreshTokenGenerator;
import com.project.sb13ootbsteam04.auth.service.RefreshTokenService;
import com.project.sb13ootbsteam04.integration.PostgresTestDatabaseConfig;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("postgres-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "OTBOO_POSTGRES_TEST", matches = "true")
class RefreshTokenLogoutRollbackPostgresTest {

    private static final String TEST_SCHEMA =
            "otboo_logout_rollback_" + UUID.randomUUID().toString().replace("-", "");
    private static final String FORCED_FAILURE_MESSAGE = "로그아웃 flush 이후 강제 실패";

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        PostgresTestDatabaseConfig.register(registry, TEST_SCHEMA);
        registry.add("weather.batch.schedule.enabled", () -> false);
    }

    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private RefreshTokenGenerator refreshTokenGenerator;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    private User user;
    private long originalVersion;
    private String rawToken;
    private String accessToken;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(new User(
                "롤백테스트",
                "logout-rollback-" + UUID.randomUUID() + "@example.com",
                "test-encoded-password"
        ));
        originalVersion = user.getLoginVersion();
        rawToken = refreshTokenService.issue(user).rawToken();
        accessToken = jwtTokenProvider.generateAccessToken(user);
    }

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAllInBatch();
        if (user != null) {
            userRepository.deleteAllByIdInBatch(List.of(user.getId()));
        }
    }

    // 1. 정상 로그아웃은 두 변경사항을 함께 커밋
    @Test
    void shouldCommitLogoutChanges() {
        refreshTokenService.logout(rawToken);
        RefreshToken token = findToken(rawToken);
        assertThat(token.getRevokedAt()).isNotNull();
        assertThat(token.getRevocationReason()).isEqualTo(RefreshTokenRevocationReason.LOGOUT);
        assertThat(currentVersion()).isEqualTo(originalVersion + 1);
    }

    // 2. flush 이후 실패해도 폐기 시간과 사유 모두 복구
    @Test
    void shouldRollbackTokenRevocationAfterFlush() {
        forceLogoutRollback(rawToken, RefreshTokenRevocationReason.LOGOUT);
        assertThat(findToken(rawToken).getRevokedAt()).isNull();
        assertThat(findToken(rawToken).getRevocationReason()).isNull();
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    // 3. flush 이후 실패해도 사용자 버전 복구
    @Test
    void shouldRollbackLoginVersionAfterFlush() {
        forceLogoutRollback(rawToken, RefreshTokenRevocationReason.LOGOUT);
        assertThat(currentVersion()).isEqualTo(originalVersion);
        assertThat(findToken(rawToken).getLoginVersion()).isEqualTo(originalVersion);
    }

    // 4. 롤백 이후 기존 Access Token 인증 유지
    @Test
    void shouldKeepAccessTokenValidAfterRollback() {
        assertThat(jwtDecoder.decode(accessToken).getSubject()).isEqualTo(user.getId().toString());
        forceLogoutRollback(rawToken, RefreshTokenRevocationReason.LOGOUT);
        assertThat(jwtDecoder.decode(accessToken).getSubject()).isEqualTo(user.getId().toString());
    }

    // 5. 롤백 이후 기존 Refresh Token 갱신 가능
    @Test
    void shouldAllowRefreshAfterRollback() {
        forceLogoutRollback(rawToken, RefreshTokenRevocationReason.LOGOUT);
        LoginResult result = refreshTokenService.rotate(rawToken);
        assertThat(result.hasRefreshToken()).isTrue();
        assertThat(findToken(rawToken).getRevocationReason()).isEqualTo(RefreshTokenRevocationReason.ROTATED);
        assertThat(findToken(result.refreshToken().rawToken()).isRevoked()).isFalse();
        assertThat(currentVersion()).isEqualTo(originalVersion);
    }

    // 6. 롤백 이후 로그아웃 재시도 및 인증 무효화
    @Test
    void shouldAllowLogoutRetryAfterRollback() {
        forceLogoutRollback(rawToken, RefreshTokenRevocationReason.LOGOUT);
        refreshTokenService.logout(rawToken);
        assertThat(currentVersion()).isEqualTo(originalVersion + 1);
        assertThat(findToken(rawToken).getRevocationReason()).isEqualTo(RefreshTokenRevocationReason.LOGOUT);
        assertThatThrownBy(() -> jwtDecoder.decode(accessToken)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken)).isInstanceOf(BadCredentialsException.class);
    }

    // 7. 최근 Rotation 토큰으로 로그아웃 실패 시 기존 Rotation 상태 보존
    @Test
    void shouldRollbackLogoutOfRecentlyRotatedToken() {
        LoginResult rotated = refreshTokenService.rotate(rawToken);
        RefreshToken before = findToken(rawToken);
        String newRawToken = rotated.refreshToken().rawToken();

        forceLogoutRollback(rawToken, RefreshTokenRevocationReason.ROTATED);

        RefreshToken after = findToken(rawToken);
        assertThat(after.getRevokedAt()).isEqualTo(before.getRevokedAt());
        assertThat(after.getRevocationReason()).isEqualTo(RefreshTokenRevocationReason.ROTATED);
        assertThat(currentVersion()).isEqualTo(originalVersion);
        assertThat(findToken(newRawToken).isRevoked()).isFalse();
        assertThat(jwtDecoder.decode(rotated.response().accessToken()).getSubject())
                .isEqualTo(user.getId().toString());
        assertThat(refreshTokenService.rotate(newRawToken).hasRefreshToken()).isTrue();
    }

    private void forceLogoutRollback(String token, RefreshTokenRevocationReason expectedReason) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            refreshTokenService.logout(token);

            // 실제 UPDATE SQL을 DB에 전송하고 1차 캐시 제거
            entityManager.flush();
            entityManager.clear();

            // 같은 트랜잭션에서 DB를 다시 읽어 변경 여부 확인
            assertThat(currentVersion()).isEqualTo(originalVersion + 1);
            RefreshToken flushedToken = findToken(token);
            assertThat(flushedToken.getRevokedAt()).isNotNull();
            assertThat(flushedToken.getRevocationReason()).isEqualTo(expectedReason);

            // 커밋 전에 강제 예외 발생
            throw new IllegalStateException(FORCED_FAILURE_MESSAGE);
        })).isInstanceOf(IllegalStateException.class).hasMessage(FORCED_FAILURE_MESSAGE);
    }

    private long currentVersion() {
        return userRepository.findById(user.getId()).orElseThrow().getLoginVersion();
    }

    private RefreshToken findToken(String token) {
        return refreshTokenRepository.findByTokenHash(refreshTokenGenerator.hash(token)).orElseThrow();
    }
}
