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
import com.project.sb13ootbsteam04.user.entity.UserRole;
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

import java.time.Instant;
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
class RefreshTokenPostgresIntegrationTest {

    private static final String TEST_SCHEMA =
            "otboo_refresh_test_"
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

        userRepository.deleteAllByIdInBatch(
                java.util.List.of(user.getId())
        );
    }

    // ========================================
    // Refresh Token 발급 및 저장 검증
    // ========================================

    // 1. Refresh Token 실제 PostgreSQL 저장
    @Test
    void shouldPersistRefreshTokenInPostgres() {

        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user);

        String tokenHash = refreshTokenGenerator.hash(
                issued.rawToken()
        );

        RefreshToken savedToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow();

        assertThat(savedToken.getId()).isNotNull();

        assertThat(savedToken.getTokenHash())
                .isEqualTo(tokenHash);

        assertThat(savedToken.getTokenHash())
                .isNotEqualTo(issued.rawToken());

        assertThat(savedToken.getTokenHash())
                .hasSize(64);

        assertThat(savedToken.getLoginVersion())
                .isEqualTo(user.getLoginVersion());

        assertThat(savedToken.getExpiresAt())
                .isCloseTo(
                        issued.expiresAt(),
                        org.assertj.core.api.Assertions.within(
                                1,
                                java.time.temporal.ChronoUnit.MICROS
                        )
                );

        assertThat(savedToken.getRevokedAt()).isNull();

        assertThat(
                refreshTokenRepository.findUserIdByTokenHash(
                        tokenHash
                )
        ).contains(user.getId());
    }

    // 2. 로그인 시 Refresh Token 실제 발급 및 저장
    @Test
    void shouldIssueRefreshTokenWhenLoginSucceeds() {

        LoginResult result = login();

        assertThat(result.response().accessToken())
                .isNotBlank();

        assertThat(result.hasRefreshToken()).isTrue();

        String tokenHash = refreshTokenGenerator.hash(
                result.refreshToken().rawToken()
        );

        RefreshToken savedToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow();

        User savedUser = userRepository
                .findById(user.getId())
                .orElseThrow();

        assertThat(savedUser.getLoginVersion())
                .isEqualTo(1L);

        assertThat(savedToken.getLoginVersion())
                .isEqualTo(savedUser.getLoginVersion());

        assertThat(savedToken.isRevoked()).isFalse();

        assertThat(refreshTokenRepository.count())
                .isEqualTo(1);
    }

    // ========================================
    // Refresh Token Rotation 검증
    // ========================================

    // 3. 기존 토큰 폐기 및 신규 토큰 저장
    @Test
    void shouldRotateRefreshTokenInPostgres() {

        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user);

        String oldHash = refreshTokenGenerator.hash(
                issued.rawToken()
        );

        LoginResult result = refreshTokenService.rotate(
                issued.rawToken()
        );

        String newHash = refreshTokenGenerator.hash(
                result.refreshToken().rawToken()
        );

        RefreshToken oldToken = refreshTokenRepository
                .findByTokenHash(oldHash)
                .orElseThrow();

        RefreshToken newToken = refreshTokenRepository
                .findByTokenHash(newHash)
                .orElseThrow();

        assertThat(oldToken.isRevoked()).isTrue();

        assertThat(oldToken.getRevokedAt())
                .isNotNull();

        assertThat(newToken.isRevoked()).isFalse();

        assertThat(newHash).isNotEqualTo(oldHash);

        assertThat(result.response().accessToken())
                .isNotBlank();

        // 최초 발급 시 설정한 만료 시간 유지
        assertThat(newToken.getExpiresAt())
                .isEqualTo(oldToken.getExpiresAt());

        // 갱신 시 loginVersion은 변경하지 않음
        assertThat(newToken.getLoginVersion())
                .isEqualTo(oldToken.getLoginVersion());

        assertThat(refreshTokenRepository.count())
                .isEqualTo(2);
    }

    // 4. 이미 사용된 Refresh Token 재사용 차단
    @Test
    void shouldRejectReuseOfRotatedRefreshToken() {

        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user);

        refreshTokenService.rotate(
                issued.rawToken()
        );

        assertInvalidToken(
                () -> refreshTokenService.rotate(
                        issued.rawToken()
                )
        );

        assertThat(refreshTokenRepository.count())
                .isEqualTo(2);
    }

    // 5. 여러 번 갱신하더라도 최초 만료 시간 유지
    @Test
    void shouldPreserveOriginalExpirationAfterMultipleRotations() {

        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user);

        RefreshToken originalToken = findToken(
                issued.rawToken()
        );

        LoginResult firstResult =
                refreshTokenService.rotate(
                        issued.rawToken()
                );

        LoginResult secondResult =
                refreshTokenService.rotate(
                        firstResult.refreshToken().rawToken()
                );

        RefreshToken latestToken = findToken(
                secondResult.refreshToken().rawToken()
        );

        assertThat(latestToken.getExpiresAt())
                .isEqualTo(originalToken.getExpiresAt());

        assertThat(latestToken.isRevoked())
                .isFalse();

        assertThat(
                refreshTokenRepository.findAll()
                        .stream()
                        .filter(token -> !token.isRevoked())
                        .count()
        ).isEqualTo(1L);

        assertThat(refreshTokenRepository.count())
                .isEqualTo(3);
    }

    // ========================================
    // 사용자 계정 상태 연동 검증
    // ========================================

    // 6. 재로그인 시 기존 Refresh Token 무효화
    @Test
    void shouldInvalidatePreviousRefreshTokenAfterRelogin() {

        LoginResult firstLogin = login();

        LoginResult secondLogin = login();

        assertInvalidToken(
                () -> refreshTokenService.rotate(
                        firstLogin.refreshToken().rawToken()
                )
        );

        LoginResult refreshed =
                refreshTokenService.rotate(
                        secondLogin.refreshToken().rawToken()
                );

        assertThat(refreshed.hasRefreshToken())
                .isTrue();

        User savedUser = userRepository
                .findById(user.getId())
                .orElseThrow();

        assertThat(savedUser.getLoginVersion())
                .isEqualTo(2L);
    }

    // 7. 계정 잠금 이후 Refresh Token 갱신 차단
    @Test
    void shouldRejectRefreshWhenAccountIsLocked() {

        LoginResult result = login();

        User savedUser = userRepository
                .findById(user.getId())
                .orElseThrow();

        savedUser.changeLocked(true);

        userRepository.saveAndFlush(savedUser);

        assertInvalidToken(
                () -> refreshTokenService.rotate(
                        result.refreshToken().rawToken()
                )
        );

        assertThat(refreshTokenRepository.count())
                .isEqualTo(1);
    }

    // 8. 계정 잠금 해제 이후에도 이전 토큰 무효 유지
    @Test
    void shouldRejectOldTokenAfterAccountIsUnlocked() {

        LoginResult result = login();

        User savedUser = userRepository
                .findById(user.getId())
                .orElseThrow();

        savedUser.changeLocked(true);
        userRepository.saveAndFlush(savedUser);

        savedUser.changeLocked(false);
        userRepository.saveAndFlush(savedUser);

        assertInvalidToken(
                () -> refreshTokenService.rotate(
                        result.refreshToken().rawToken()
                )
        );
    }

    // 9. 사용자 권한 변경 시 기존 Refresh Token 무효화
    @Test
    void shouldInvalidateRefreshTokenAfterRoleChange() {

        LoginResult result = login();

        User savedUser = userRepository
                .findById(user.getId())
                .orElseThrow();

        savedUser.changeRole(UserRole.ADMIN);

        userRepository.saveAndFlush(savedUser);

        assertInvalidToken(
                () -> refreshTokenService.rotate(
                        result.refreshToken().rawToken()
                )
        );
    }

    // 10. 비밀번호 변경 시 기존 Refresh Token 무효화
    @Test
    void shouldInvalidateRefreshTokenAfterPasswordChange() {

        LoginResult result = login();

        User savedUser = userRepository
                .findById(user.getId())
                .orElseThrow();

        savedUser.changePassword(
                passwordEncoder.encode("NewPassword123!")
        );

        userRepository.saveAndFlush(savedUser);

        assertInvalidToken(
                () -> refreshTokenService.rotate(
                        result.refreshToken().rawToken()
                )
        );
    }

    // ========================================
    // 토큰 만료 및 폐기 검증
    // ========================================

    // 11. 만료된 Refresh Token 갱신 거부
    @Test
    void shouldRejectExpiredRefreshToken() {

        String rawToken = refreshTokenGenerator.generate();

        String tokenHash = refreshTokenGenerator.hash(
                rawToken
        );

        Instant now = Instant.now();

        refreshTokenRepository.saveAndFlush(
                new RefreshToken(
                        user,
                        tokenHash,
                        now.minusSeconds(3600),
                        now.minusSeconds(60)
                )
        );

        assertInvalidToken(
                () -> refreshTokenService.rotate(rawToken)
        );

        assertThat(refreshTokenRepository.count())
                .isEqualTo(1);

        assertThat(findToken(rawToken).isRevoked())
                .isFalse();
    }

    // 12. Refresh Token 실제 DB 폐기
    @Test
    void shouldPersistRefreshTokenRevocation() {

        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user);

        refreshTokenService.revoke(
                issued.rawToken()
        );

        RefreshToken revokedToken = findToken(
                issued.rawToken()
        );

        assertThat(revokedToken.isRevoked())
                .isTrue();

        assertThat(revokedToken.getRevokedAt())
                .isNotNull();

        assertInvalidToken(
                () -> refreshTokenService.rotate(
                        issued.rawToken()
                )
        );
    }

    // ========================================
    // 임시 비밀번호 로그인 검증
    // ========================================

    // 13. 임시 비밀번호 로그인 시 Refresh Token 미발급
    @Test
    void shouldNotIssueRefreshTokenForTemporaryPasswordLogin() {

        String temporaryPassword = "Temporary123!";

        User savedUser = userRepository
                .findById(user.getId())
                .orElseThrow();

        savedUser.issueTemporaryPassword(
                passwordEncoder.encode(temporaryPassword),
                Instant.now().plusSeconds(180)
        );

        userRepository.saveAndFlush(savedUser);

        LoginResult result = authService.login(
                new LoginRequest(
                        user.getEmail(),
                        temporaryPassword
                )
        );

        assertThat(result.response().accessToken())
                .isNotBlank();

        assertThat(result.hasRefreshToken())
                .isFalse();

        assertThat(result.refreshToken()).isNull();

        assertThat(refreshTokenRepository.count())
                .isZero();
    }

    // ========================================
    // 공통 테스트 헬퍼
    // ========================================

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