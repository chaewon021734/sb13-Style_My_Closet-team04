package com.project.sb13ootbsteam04.auth.integration;

import com.project.sb13ootbsteam04.auth.dto.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.LoginResult;
import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.auth.repository.RefreshTokenRepository;
import com.project.sb13ootbsteam04.auth.service.AuthService;
import com.project.sb13ootbsteam04.auth.service.PasswordResetService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("postgres-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "OTBOO_POSTGRES_TEST", matches = "true")
class PasswordResetRollbackPostgresTest {

    private static final String TEST_SCHEMA = "otboo_reset_rb_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String TEMP_PASSWORD = "TempPassword123!";
    private static final String NEW_PASSWORD = "NewPassword123!";
    private static final String FORCED_FAILURE = "비밀번호 변경 강제 롤백";

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        PostgresTestDatabaseConfig.register(registry, TEST_SCHEMA);
        registry.add("weather.batch.schedule.enabled", () -> false);
        registry.add("jwt.refresh-token-cleanup.enabled", () -> false);
    }

    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private AuthService authService;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManager entityManager;

    private UUID userId;
    private String email;
    private long version;
    private String oldHash;
    private String temporaryHash;
    private Instant temporaryExpiresAt;

    @BeforeEach
    void setUp() {
        email = "reset-rollback-" + UUID.randomUUID() + "@example.com";
        User user = new User("재설정테스트", email, passwordEncoder.encode(OLD_PASSWORD));
        user.issueTemporaryPassword(passwordEncoder.encode(TEMP_PASSWORD),
                Instant.now().plusSeconds(180));
        userId = userRepository.saveAndFlush(user).getId();
        // 실제 DB에서 다시 읽어 PostgreSQL 시간 정밀도를 반영한다.
        User saved = findUser();
        version = saved.getLoginVersion();
        oldHash = saved.getPassword();
        temporaryHash = saved.getTemporaryPasswordHash();
        temporaryExpiresAt = saved.getTemporaryPasswordExpiresAt();
    }

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAllInBatch();
        if (userId != null) {
            userRepository.deleteAllByIdInBatch(List.of(userId));
        }
    }

    @Test
    void shouldPreservePasswordAfterRollback() {
        forceRollback();
        assertThat(findUser().getPassword()).isEqualTo(oldHash);
        assertThat(passwordEncoder.matches(OLD_PASSWORD, findUser().getPassword())).isTrue();
    }

    @Test
    void shouldPreserveLoginVersionAfterRollback() {
        forceRollback();
        assertThat(findUser().getLoginVersion()).isEqualTo(version);
    }

    @Test
    void shouldPreserveTemporaryPasswordAfterRollback() {
        forceRollback();
        User saved = findUser();
        assertThat(saved.getTemporaryPasswordHash()).isEqualTo(temporaryHash);
        assertThat(saved.getTemporaryPasswordExpiresAt()).isEqualTo(temporaryExpiresAt);
        assertThat(saved.isTemporaryPasswordActive(Instant.now())).isTrue();
    }

    @Test
    void shouldAllowRetryAfterRollback() {
        String temporaryJwt = jwtTokenProvider.generateTemporaryAccessToken(findUser());
        forceRollback();
        assertThat(jwtDecoder.decode(temporaryJwt).getSubject()).isEqualTo(userId.toString());
        resetPassword();
        assertResetState();
    }

    @Test
    void shouldInvalidateNormalAccessTokenAfterReset() {
        // 같은 버전에서 발급해 로그인에 의한 무효화와 구분한다.
        String token = jwtTokenProvider.generateAccessToken(findUser());
        assertThat(jwtDecoder.decode(token).getSubject()).isEqualTo(userId.toString());
        resetPassword();
        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
        assertResetState();
    }

    @Test
    void shouldInvalidateTemporaryJwtAfterReset() {
        LoginResult login = authService.login(new LoginRequest(email, TEMP_PASSWORD));
        assertThat(login.hasRefreshToken()).isFalse();
        String token = login.response().accessToken();
        var jwt = jwtDecoder.decode(token);
        assertThat(jwt.getClaimAsString("authType")).isEqualTo("TEMPORARY");
        version = ((Number) jwt.getClaim("loginVersion")).longValue();
        resetPassword();
        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
        assertResetState();
    }

    @Test
    void shouldInvalidateRefreshTokenAfterReset() {
        var issued = refreshTokenService.issue(findUser());
        // 실제 갱신 성공으로 재설정 전 유효성을 입증한다.
        LoginResult rotated = refreshTokenService.rotate(issued.rawToken());
        assertThat(rotated.hasRefreshToken()).isTrue();
        String validToken = rotated.refreshToken().rawToken();
        assertThat(findUser().getLoginVersion()).isEqualTo(version);
        resetPassword();
        assertThatThrownBy(() -> refreshTokenService.rotate(validToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("유효하지 않은 Refresh Token입니다.");
        assertResetState();
    }

    @Test
    void shouldLoginWithNewPasswordAfterReset() {
        resetPassword();
        assertResetState();
        LoginResult login = authService.login(new LoginRequest(email, NEW_PASSWORD));
        assertThat(login.hasRefreshToken()).isTrue();
        var jwt = jwtDecoder.decode(login.response().accessToken());
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("authType")).isEqualTo("NORMAL");
    }

    @Test
    void shouldRejectOldPasswordAfterReset() {
        resetPassword();
        assertThatThrownBy(() -> authService.login(new LoginRequest(email, OLD_PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
        assertResetState();
    }

    @Test
    void shouldRejectUsedTemporaryPasswordAfterReset() {
        resetPassword();
        assertThatThrownBy(() -> authService.login(new LoginRequest(email, TEMP_PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
        assertResetState();
    }

    private User findUser() {
        return userRepository.findById(userId).orElseThrow();
    }

    private void resetPassword() {
        passwordResetService.resetPassword(userId, NEW_PASSWORD, version);
    }

    private void assertResetState() {
        User saved = findUser();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, saved.getPassword())).isTrue();
        assertThat(saved.getLoginVersion()).isEqualTo(version + 1);
        assertThat(saved.getTemporaryPasswordHash()).isNull();
        assertThat(saved.getTemporaryPasswordExpiresAt()).isNull();
    }

    private void forceRollback() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            resetPassword();
            // UPDATE를 실제 DB로 전송한 후 영속성 컨텍스트를 비워 재조회한다.
            entityManager.flush();
            entityManager.clear();
            assertResetState();
            throw new IllegalStateException(FORCED_FAILURE);
        })).isInstanceOf(IllegalStateException.class).hasMessage(FORCED_FAILURE);
    }
}
