package com.project.sb13ootbsteam04.auth.integration;

import com.project.sb13ootbsteam04.auth.entity.RefreshToken;
import com.project.sb13ootbsteam04.auth.entity.RefreshTokenRevocationReason;
import com.project.sb13ootbsteam04.auth.repository.RefreshTokenRepository;
import com.project.sb13ootbsteam04.auth.service.RefreshTokenCleanupService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("postgres-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "OTBOO_POSTGRES_TEST", matches = "true")
class RefreshTokenCleanupPostgresTest {

    private static final String TEST_SCHEMA =
            "otboo_rt_cleanup_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        PostgresTestDatabaseConfig.register(registry, TEST_SCHEMA);
        registry.add("weather.batch.schedule.enabled", () -> false);
        registry.add("jwt.refresh-token-cleanup.enabled", () -> false);
    }

    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RefreshTokenCleanupService cleanupService;
    @Autowired private RefreshTokenGenerator generator;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private PlatformTransactionManager transactionManager;

    private User user;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        user = createUser();
    }

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void shouldDeleteExpiredToken() {
        String hash = saveToken(user, now.minusSeconds(60), null);
        assertThat(cleanupService.cleanupExpiredTokens()).isEqualTo(1);
        assertMissing(hash);
    }

    @Test
    void shouldPreserveUnexpiredToken() {
        String hash = saveToken(user, now.plusSeconds(3600), null);
        assertThat(cleanupService.cleanupExpiredTokens()).isZero();
        assertPresent(hash);
    }

    @Test
    void shouldDeleteExpiredRevokedTokens() {
        for (var reason : RefreshTokenRevocationReason.values()) {
            saveToken(user, now.minusSeconds(60), reason);
        }
        assertThat(cleanupService.cleanupExpiredTokens()).isEqualTo(3);
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void shouldPreserveUnexpiredRotatedToken() {
        assertPreservedReason(RefreshTokenRevocationReason.ROTATED);
    }

    @Test
    void shouldPreserveUnexpiredLogoutToken() {
        assertPreservedReason(RefreshTokenRevocationReason.LOGOUT);
    }

    @Test
    void shouldPreserveUnexpiredRevokedToken() {
        assertPreservedReason(RefreshTokenRevocationReason.REVOKED);
    }

    @Test
    void shouldDeleteAtExactExpiration() {
        String hash = saveToken(user, now, null);
        assertThat(refreshTokenRepository.deleteByExpiresAtLessThanEqual(now)).isEqualTo(1);
        assertMissing(hash);
    }

    @Test
    void shouldPreserveImmediatelyBeforeExpiration() {
        String hash = saveToken(user, now, null);
        // PostgreSQL timestamp의 마이크로초 정밀도를 고려한다.
        assertThat(refreshTokenRepository.deleteByExpiresAtLessThanEqual(
                now.minus(1, ChronoUnit.MICROS))).isZero();
        assertPresent(hash);
    }

    @Test
    void shouldDeleteExpiredTokensAcrossUsers() {
        User second = createUser();
        String expired1 = saveToken(user, now.minusSeconds(60), null);
        String expired2 = saveToken(second, now.minusSeconds(30), RefreshTokenRevocationReason.LOGOUT);
        String valid1 = saveToken(user, now.plusSeconds(3600), null);
        String valid2 = saveToken(second, now.plusSeconds(3600), null);
        assertThat(cleanupService.cleanupExpiredTokens()).isEqualTo(2);
        assertMissing(expired1);
        assertMissing(expired2);
        assertPresent(valid1);
        assertPresent(valid2);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
        assertThat(userRepository.existsById(user.getId())).isTrue();
        assertThat(userRepository.existsById(second.getId())).isTrue();
    }

    @Test
    void shouldReturnZeroWhenNoTokensExist() {
        assertThat(refreshTokenRepository.count()).isZero();
        assertThat(cleanupService.cleanupExpiredTokens()).isZero();
    }

    @Test
    void shouldNotDeleteSameTokenTwice() {
        saveToken(user, now.minusSeconds(60), null);
        String valid = saveToken(user, now.plusSeconds(3600), null);
        assertThat(cleanupService.cleanupExpiredTokens()).isEqualTo(1);
        assertThat(cleanupService.cleanupExpiredTokens()).isZero();
        assertPresent(valid);
    }

    @Test
    void shouldRollbackBulkDeletionOnFailure() {
        String expired = saveToken(user, now.minusSeconds(60), null);
        String valid = saveToken(user, now.plusSeconds(3600), null);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            assertThat(cleanupService.cleanupExpiredTokens()).isEqualTo(1);
            assertMissing(expired);
            assertPresent(valid);
            throw new IllegalStateException("삭제 후 강제 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("삭제 후 강제 실패");
        assertPresent(expired);
        assertPresent(valid);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldAllowRotationAfterCleanup() {
        String expired = saveToken(user, now.minusSeconds(60), null);
        var issued = refreshTokenService.issue(user);
        assertThat(cleanupService.cleanupExpiredTokens()).isEqualTo(1);
        assertMissing(expired);
        var result = refreshTokenService.rotate(issued.rawToken());
        assertThat(result.hasRefreshToken()).isTrue();
        assertThat(result.response().accessToken()).isNotBlank();
        assertThat(findToken(generator.hash(issued.rawToken())).getRevocationReason())
                .isEqualTo(RefreshTokenRevocationReason.ROTATED);
        assertThat(findToken(generator.hash(result.refreshToken().rawToken())).isRevoked()).isFalse();
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    private User createUser() {
        return userRepository.saveAndFlush(new User(
                "정리테스트", "cleanup-" + UUID.randomUUID() + "@example.com", "encoded-password"));
    }

    private String saveToken(User owner, Instant expiresAt, RefreshTokenRevocationReason reason) {
        String hash = generator.hash(generator.generate());
        Instant createdAt = now.minusSeconds(7200);
        RefreshToken token = new RefreshToken(owner, hash, createdAt, expiresAt);
        if (reason != null) {
            token.revoke(createdAt.plusSeconds(1), reason);
        }
        refreshTokenRepository.saveAndFlush(token);
        return hash;
    }

    private void assertPreservedReason(RefreshTokenRevocationReason reason) {
        String hash = saveToken(user, now.plusSeconds(3600), reason);
        assertThat(cleanupService.cleanupExpiredTokens()).isZero();
        RefreshToken token = findToken(hash);
        assertThat(token.getRevocationReason()).isEqualTo(reason);
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(now.minusSeconds(7199));
    }

    private RefreshToken findToken(String hash) {
        return refreshTokenRepository.findByTokenHash(hash).orElseThrow();
    }

    private void assertPresent(String hash) {
        assertThat(refreshTokenRepository.findByTokenHash(hash)).isPresent();
    }

    private void assertMissing(String hash) {
        assertThat(refreshTokenRepository.findByTokenHash(hash)).isEmpty();
    }
}
