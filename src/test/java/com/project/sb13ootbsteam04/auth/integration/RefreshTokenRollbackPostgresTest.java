package com.project.sb13ootbsteam04.auth.integration;

import com.project.sb13ootbsteam04.auth.dto.LoginResult;
import com.project.sb13ootbsteam04.auth.entity.RefreshToken;
import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.auth.repository.RefreshTokenRepository;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("postgres-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(
        named = "OTBOO_POSTGRES_TEST",
        matches = "true"
)
class RefreshTokenRollbackPostgresTest {

    private static final String TEST_SCHEMA =
            "otboo_refresh_rollback_"
                    + UUID.randomUUID()
                    .toString()
                    .replace("-", "");

    private static final String FORCED_FAILURE_MESSAGE =
            "Access Token 발급 강제 실패";

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

    @MockitoSpyBean
    private JwtTokenProvider jwtTokenProvider;

    private User user;

    @BeforeEach
    void setUp() {

        user = userRepository.saveAndFlush(
                new User(
                        "롤백테스트",
                        "refresh-rollback-"
                                + UUID.randomUUID()
                                + "@example.com",
                        "test-encoded-password"
                )
        );
    }

    @AfterEach
    void tearDown() {

        refreshTokenRepository.deleteAllInBatch();

        if (user != null) {
            userRepository.deleteAllByIdInBatch(
                    List.of(user.getId())
            );
        }
    }

    // ========================================
    // 1. 갱신 실패 시 기존 토큰 폐기 롤백
    // ========================================

    @Test
    void shouldRollbackOriginalTokenRevocation() {

        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user);

        forceAccessTokenFailure();

        assertThatThrownBy(
                () -> refreshTokenService.rotate(
                        issued.rawToken()
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(FORCED_FAILURE_MESSAGE);

        RefreshToken originalToken =
                findToken(issued.rawToken());

        // 기존 토큰의 폐기 처리가 롤백되어야 함
        assertThat(originalToken.isRevoked())
                .isFalse();

        assertThat(originalToken.getRevokedAt())
                .isNull();

        assertThat(refreshTokenRepository.count())
                .isEqualTo(1);

        verify(jwtTokenProvider)
                .generateAccessToken(any(User.class));
    }

    // ========================================
    // 2. 갱신 실패 시 신규 토큰 저장 롤백
    // ========================================

    @Test
    void shouldRollbackNewTokenPersistence() {

        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user);

        String originalHash =
                refreshTokenGenerator.hash(
                        issued.rawToken()
                );

        forceAccessTokenFailure();

        assertThatThrownBy(
                () -> refreshTokenService.rotate(
                        issued.rawToken()
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(FORCED_FAILURE_MESSAGE);

        List<RefreshToken> savedTokens =
                refreshTokenRepository.findAll();

        // 실패한 갱신에서 생성된 신규 토큰은
        // DB에 남아 있으면 안 됨
        assertThat(savedTokens)
                .hasSize(1);

        assertThat(savedTokens.get(0).getTokenHash())
                .isEqualTo(originalHash);

        assertThat(savedTokens.get(0).isRevoked())
                .isFalse();
    }

    // ========================================
    // 3. 롤백 이후 기존 토큰으로 정상 갱신
    // ========================================

    @Test
    void shouldAllowRetryAfterRotationRollback() {

        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user);

        // 첫 번째 호출에서는 강제 예외,
        // 두 번째 호출에서는 실제 JWT 발급
        doThrow(
                new IllegalStateException(
                        FORCED_FAILURE_MESSAGE
                )
        )
                .doCallRealMethod()
                .when(jwtTokenProvider)
                .generateAccessToken(any(User.class));

        assertThatThrownBy(
                () -> refreshTokenService.rotate(
                        issued.rawToken()
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(FORCED_FAILURE_MESSAGE);

        assertThat(refreshTokenRepository.count())
                .isEqualTo(1);

        assertThat(findToken(issued.rawToken()).isRevoked())
                .isFalse();

        // 동일한 기존 토큰으로 다시 갱신 요청
        LoginResult result =
                refreshTokenService.rotate(
                        issued.rawToken()
                );

        assertThat(result.hasRefreshToken())
                .isTrue();

        assertThat(result.response().accessToken())
                .isNotBlank();

        RefreshToken originalToken =
                findToken(issued.rawToken());

        RefreshToken newToken =
                findToken(
                        result.refreshToken().rawToken()
                );

        assertThat(originalToken.isRevoked())
                .isTrue();

        assertThat(newToken.isRevoked())
                .isFalse();

        assertThat(newToken.getExpiresAt())
                .isEqualTo(originalToken.getExpiresAt());

        assertThat(refreshTokenRepository.count())
                .isEqualTo(2);

        verify(jwtTokenProvider, times(2))
                .generateAccessToken(any(User.class));
    }

    // ========================================
    // 4. 갱신 실패 시 loginVersion 유지
    // ========================================

    @Test
    void shouldPreserveLoginVersionAfterRollback() {

        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user);

        long previousVersion =
                user.getLoginVersion();

        forceAccessTokenFailure();

        assertThatThrownBy(
                () -> refreshTokenService.rotate(
                        issued.rawToken()
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(FORCED_FAILURE_MESSAGE);

        User savedUser = userRepository
                .findById(user.getId())
                .orElseThrow();

        RefreshToken originalToken =
                findToken(issued.rawToken());

        assertThat(savedUser.getLoginVersion())
                .isEqualTo(previousVersion);

        assertThat(originalToken.getLoginVersion())
                .isEqualTo(previousVersion);

        assertThat(originalToken.isRevoked())
                .isFalse();
    }

    // ========================================
    // 공통 테스트 헬퍼
    // ========================================

    private void forceAccessTokenFailure() {

        doThrow(
                new IllegalStateException(
                        FORCED_FAILURE_MESSAGE
                )
        )
                .when(jwtTokenProvider)
                .generateAccessToken(any(User.class));
    }

    private RefreshToken findToken(String rawToken) {

        String tokenHash =
                refreshTokenGenerator.hash(rawToken);

        return refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow();
    }
}