package com.project.sb13ootbsteam04.auth.integration;

import com.project.sb13ootbsteam04.auth.service.PasswordResetService;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("postgres-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "OTBOO_POSTGRES_TEST", matches = "true")
class PasswordResetConcurrencyPostgresTest {

    private static final String TEST_SCHEMA = "reset_concurrency_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "NewPassword123!";
    private static final String FAILURE = "비밀번호를 재설정할 수 없습니다.";
    private static final String EXPIRED = "임시 비밀번호가 만료되었거나 유효하지 않습니다.";

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        PostgresTestDatabaseConfig.register(registry, TEST_SCHEMA);
        registry.add("weather.batch.schedule.enabled", () -> false);
        registry.add("jwt.refresh-token-cleanup.enabled", () -> false);
    }

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;

    private UUID userId;
    private long tokenVersion;

    @BeforeEach
    void setUp() {
        User user = new User("재설정테스트", UUID.randomUUID() + "@example.com",
                passwordEncoder.encode(OLD_PASSWORD));
        user.issueTemporaryPassword(passwordEncoder.encode("Temporary123!"),
                Instant.now().plusSeconds(600));
        User saved = userRepository.saveAndFlush(user);
        userId = saved.getId();
        tokenVersion = saved.getLoginVersion();
    }

    @AfterEach
    void tearDown() {
        if (userId != null) {
            userRepository.deleteAllByIdInBatch(List.of(userId));
        }
    }

    @Test
    void shouldPersistSuccessfulReset() {
        passwordResetService.resetPassword(userId, NEW_PASSWORD, tokenVersion);
        assertResetState(NEW_PASSWORD);
    }

    @Test
    void shouldAllowOnlyOneOfTwoConcurrentRequests() throws Exception {
        assertSingleSuccessfulReset(2);
    }

    @Test
    void shouldAllowOnlyOneOfFiveConcurrentRequests() throws Exception {
        assertSingleSuccessfulReset(5);
    }

    @Test
    void shouldAllowOnlyOneOfTenConcurrentRequests() throws Exception {
        assertSingleSuccessfulReset(10);
    }

    @Test
    void shouldRejectStaleLoginVersionWhileTemporaryPasswordIsActive() {
        mutateUser(User::increaseLoginVersion);
        User before = loadUser();
        assertThat(before.isTemporaryPasswordActive(Instant.now())).isTrue();
        assertThat(before.getLoginVersion()).isGreaterThan(tokenVersion);
        assertRejectedWithoutChanges(tokenVersion, FAILURE);
    }

    @Test
    void shouldRejectOldVersionAfterTemporaryPasswordReissue() {
        mutateUser(user -> user.issueTemporaryPassword(
                passwordEncoder.encode("Replacement123!"), Instant.now().plusSeconds(600)));
        User before = loadUser();
        assertThat(before.isTemporaryPasswordActive(Instant.now())).isTrue();
        assertThat(before.getLoginVersion()).isGreaterThan(tokenVersion);
        assertRejectedWithoutChanges(tokenVersion, FAILURE);
    }

    @Test
    void shouldRejectLockedAccountEvenWithCurrentVersion() {
        mutateUser(user -> user.changeLocked(true));
        User before = loadUser();
        assertThat(before.isLocked()).isTrue();
        assertRejectedWithoutChanges(before.getLoginVersion(), FAILURE);
    }

    @Test
    void shouldRejectExpiredTemporaryPasswordEvenWithCurrentVersion() {
        mutateUser(user -> user.issueTemporaryPassword(
                passwordEncoder.encode("Expired123!"), Instant.now().minusSeconds(60)));
        User before = loadUser();
        assertThat(before.isTemporaryPasswordActive(Instant.now())).isFalse();
        assertRejectedWithoutChanges(before.getLoginVersion(), EXPIRED);
    }

    @Test
    void shouldRejectReplayAfterSuccessfulReset() {
        passwordResetService.resetPassword(userId, NEW_PASSWORD, tokenVersion);
        assertResetState(NEW_PASSWORD);
        assertRejectedWithoutChanges(tokenVersion, FAILURE);
    }

    private void assertSingleSuccessfulReset(int count) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        var barrier = new CyclicBarrier(count);
        List<Future<ResetResult>> futures = new ArrayList<>();
        List<ResetResult> results = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                String candidate = "ConcurrentPassword" + i + "!";
                futures.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try {
                        passwordResetService.resetPassword(userId, candidate, tokenVersion);
                        return new ResetResult(true, candidate);
                    } catch (BadCredentialsException exception) {
                        assertThat(exception).hasMessage(FAILURE);
                        return new ResetResult(false, candidate);
                    }
                }));
            }
            for (Future<ResetResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 작업이 종료되지 않았습니다.");
            }
        }
        List<ResetResult> successful = results.stream().filter(ResetResult::success).toList();
        assertThat(successful).hasSize(1);
        assertThat(results.stream().filter(result -> !result.success()).count())
                .isEqualTo(count - 1L);
        assertResetState(successful.get(0).password());
    }

    private void assertResetState(String expectedPassword) {
        User saved = loadUser();
        assertThat(passwordEncoder.matches(expectedPassword, saved.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, saved.getPassword())).isFalse();
        assertThat(saved.getTemporaryPasswordHash()).isNull();
        assertThat(saved.getTemporaryPasswordExpiresAt()).isNull();
        assertThat(saved.getLoginVersion()).isEqualTo(tokenVersion + 1);
    }

    private void assertRejectedWithoutChanges(long version, String message) {
        User before = loadUser();
        assertThatThrownBy(() -> passwordResetService.resetPassword(userId, NEW_PASSWORD, version))
                .isInstanceOf(BadCredentialsException.class).hasMessage(message);
        User after = loadUser();
        assertThat(after.getPassword()).isEqualTo(before.getPassword());
        assertThat(after.getTemporaryPasswordHash()).isEqualTo(before.getTemporaryPasswordHash());
        assertThat(after.getTemporaryPasswordExpiresAt()).isEqualTo(before.getTemporaryPasswordExpiresAt());
        assertThat(after.getLoginVersion()).isEqualTo(before.getLoginVersion());
        assertThat(after.isLocked()).isEqualTo(before.isLocked());
    }

    private User loadUser() {
        return userRepository.findById(userId).orElseThrow();
    }

    private void mutateUser(Consumer<User> mutation) {
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findByIdForUpdate(userId).orElseThrow();
            mutation.accept(user);
            userRepository.flush();
        });
    }

    private record ResetResult(boolean success, String password) {}
}
