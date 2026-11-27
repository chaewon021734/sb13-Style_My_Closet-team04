package com.project.sb13ootbsteam04.auth.integration;

import com.project.sb13ootbsteam04.auth.dto.LoginResult;
import com.project.sb13ootbsteam04.auth.entity.RefreshToken;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("postgres-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(
        named = "OTBOO_POSTGRES_TEST",
        matches = "true"
)
class RefreshTokenConcurrencyPostgresTest {

    private static final String TEST_SCHEMA =
            "otboo_refresh_concurrency_"
                    + UUID.randomUUID()
                    .toString()
                    .replace("-", "");

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

    private User user;

    @BeforeEach
    void setUp() {

        user = userRepository.saveAndFlush(
                new User(
                        "동시성테스트",
                        "refresh-concurrency-"
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
    // 동일 Refresh Token 동시 갱신 검증
    // ========================================

    // 1. 동일 토큰으로 2개 요청 시 하나만 성공
    @Test
    void shouldAllowOnlyOneOfTwoConcurrentRefreshRequests()
            throws Exception {

        assertSingleSuccessfulRotation(2);
    }

    // 2. 동일 토큰으로 5개 요청 시 하나만 성공
    @Test
    void shouldAllowOnlyOneOfFiveConcurrentRefreshRequests()
            throws Exception {

        assertSingleSuccessfulRotation(5);
    }

    // 3. 동일 토큰으로 10개 요청 시 하나만 성공
    @Test
    void shouldAllowOnlyOneOfTenConcurrentRefreshRequests()
            throws Exception {

        assertSingleSuccessfulRotation(10);
    }

    // ========================================
    // 공통 동시성 검증
    // ========================================

    private void assertSingleSuccessfulRotation(
            int requestCount
    ) throws Exception {

        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user);

        String originalRawToken = issued.rawToken();

        String originalHash =
                refreshTokenGenerator.hash(originalRawToken);

        List<RotationResult> results = runConcurrently(
                originalRawToken,
                requestCount
        );

        List<RotationResult> successfulResults =
                results.stream()
                        .filter(RotationResult::success)
                        .toList();

        long blockedCount = results.stream()
                .filter(result -> !result.success())
                .count();

        // 동시 요청 중 한 요청만 성공해야 함
        assertThat(successfulResults)
                .hasSize(1);

        // 나머지 요청은 모두 차단되어야 함
        assertThat(blockedCount)
                .isEqualTo(requestCount - 1L);

        LoginResult successfulLoginResult =
                successfulResults.get(0).loginResult();

        assertThat(successfulLoginResult)
                .isNotNull();

        assertThat(successfulLoginResult.hasRefreshToken())
                .isTrue();

        assertThat(
                successfulLoginResult.response()
                        .accessToken()
        ).isNotBlank();

        String newRawToken =
                successfulLoginResult.refreshToken()
                        .rawToken();

        String newHash =
                refreshTokenGenerator.hash(newRawToken);

        assertThat(newRawToken)
                .isNotEqualTo(originalRawToken);

        // 기존 토큰의 실제 DB 폐기 여부 확인
        RefreshToken originalToken =
                refreshTokenRepository
                        .findByTokenHash(originalHash)
                        .orElseThrow();

        assertThat(originalToken.isRevoked())
                .isTrue();

        assertThat(originalToken.getRevokedAt())
                .isNotNull();

        // 신규 토큰의 실제 DB 저장 여부 확인
        RefreshToken newToken =
                refreshTokenRepository
                        .findByTokenHash(newHash)
                        .orElseThrow();

        assertThat(newToken.isRevoked())
                .isFalse();

        // 신규 토큰도 최초 만료 시간 유지
        assertThat(newToken.getExpiresAt())
                .isEqualTo(originalToken.getExpiresAt());

        // 갱신 과정에서 loginVersion 변경 금지
        assertThat(newToken.getLoginVersion())
                .isEqualTo(originalToken.getLoginVersion());

        // 기존 토큰 1개 + 신규 토큰 1개
        assertThat(refreshTokenRepository.count())
                .isEqualTo(2);

        long nonRevokedTokenCount =
                refreshTokenRepository.findAll()
                        .stream()
                        .filter(token -> !token.isRevoked())
                        .count();

        // 폐기되지 않은 토큰은 정확히 1개
        assertThat(nonRevokedTokenCount)
                .isEqualTo(1);

        // 기존 토큰 재사용은 계속 차단
        assertThatThrownBy(
                () -> refreshTokenService.rotate(
                        originalRawToken
                )
        )
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(INVALID_TOKEN_MESSAGE);

        // 동시 갱신에서 발급된 신규 토큰은 정상 사용 가능
        LoginResult nextResult =
                refreshTokenService.rotate(newRawToken);

        assertThat(nextResult.hasRefreshToken())
                .isTrue();

        assertThat(refreshTokenRepository.count())
                .isEqualTo(3);

        // 반복 갱신 이후에도 폐기되지 않은 토큰은 1개
        assertThat(
                refreshTokenRepository.findAll()
                        .stream()
                        .filter(token -> !token.isRevoked())
                        .count()
        ).isEqualTo(1);
    }

    // ========================================
    // 동시 요청 실행
    // ========================================

    private List<RotationResult> runConcurrently(
            String rawToken,
            int requestCount
    ) throws Exception {

        var executor =
                Executors.newFixedThreadPool(requestCount);

        var barrier = new CyclicBarrier(requestCount);

        try {

            List<Future<RotationResult>> futures =
                    new ArrayList<>();

            for (int i = 0; i < requestCount; i++) {

                Callable<RotationResult> task = () -> {

                    // 모든 스레드가 준비되면 동시에 요청
                    barrier.await(
                            10,
                            TimeUnit.SECONDS
                    );

                    return executeRotation(rawToken);
                };

                futures.add(executor.submit(task));
            }

            List<RotationResult> results =
                    new ArrayList<>();

            for (Future<RotationResult> future : futures) {

                results.add(
                        future.get(
                                30,
                                TimeUnit.SECONDS
                        )
                );
            }

            return results;

        } finally {

            executor.shutdownNow();

            if (!executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
            )) {
                throw new IllegalStateException(
                        "동시성 테스트 작업이 종료되지 않았습니다."
                );
            }
        }
    }

    // ========================================
    // 개별 갱신 요청 결과 처리
    // ========================================

    private RotationResult executeRotation(
            String rawToken
    ) {

        try {

            LoginResult result =
                    refreshTokenService.rotate(rawToken);

            return new RotationResult(
                    true,
                    result
            );

        } catch (BadCredentialsException e) {

            // 예상된 토큰 인증 실패만 정상 차단으로 인정
            if (!INVALID_TOKEN_MESSAGE.equals(
                    e.getMessage()
            )) {
                throw e;
            }

            return new RotationResult(
                    false,
                    null
            );
        }
    }

    private record RotationResult(
            boolean success,
            LoginResult loginResult
    ) {
    }
}