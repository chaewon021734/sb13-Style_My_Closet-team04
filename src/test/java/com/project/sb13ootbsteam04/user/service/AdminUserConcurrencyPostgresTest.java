package com.project.sb13ootbsteam04.user.service;

import com.project.sb13ootbsteam04.integration.PostgresTestDatabaseConfig;
import com.project.sb13ootbsteam04.user.entity.AdminOperationLock;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.repository.AdminOperationLockRepository;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("postgres-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(
        named = "OTBOO_POSTGRES_TEST",
        matches = "true"
)
class AdminUserConcurrencyPostgresTest {

    private static final String TEST_SCHEMA =
            "otboo_admin_test_"
                    + UUID.randomUUID()
                    .toString()
                    .replace("-", "");

    @DynamicPropertySource
    static void configurePostgres(
            DynamicPropertyRegistry registry
    ) {
        PostgresTestDatabaseConfig.register(
                registry,
                TEST_SCHEMA
        );
    }

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminOperationLockRepository lockRepository;

    // ========================================
    // 1. 테스트용 고정 잠금 행 생성
    // ========================================

    @BeforeEach
    void setUp() {

        // PostgreSQL 테스트에서는 Flyway가 비활성화됨
        // 따라서 고정 잠금 행을 직접 준비
        if (!lockRepository.existsById(
                AdminOperationLock.LOCK_ID
        )) {
            lockRepository.saveAndFlush(
                    new AdminOperationLock(
                            AdminOperationLock.LOCK_ID
                    )
            );
        }
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    // ========================================
    // 2. 두 관리자가 동시에 서로를 강등
    // ========================================

    @Test
    void concurrentDemotionsLeaveOneActiveAdmin()
            throws Exception {

        User adminA = createAdmin("admin-a");
        User adminB = createAdmin("admin-b");

        UUID adminAId = adminA.getId();
        UUID adminBId = adminB.getId();

        List<Result> results = runConcurrently(
                () -> {
                    adminUserService.changeRole(
                            adminBId,
                            UserRole.USER
                    );
                },
                () -> {
                    adminUserService.changeRole(
                            adminAId,
                            UserRole.USER
                    );
                }
        );

        // 한 요청 성공, 다른 요청 차단
        assertThat(results)
                .containsExactlyInAnyOrder(
                        Result.SUCCESS,
                        Result.BLOCKED
                );

        // 실제 DB에서 활성 관리자 수 확인
        assertThat(countActiveAdmins()).isEqualTo(1);

        // 관리자 계정은 정확히 한 개 남아야 함
        assertThat(
                userRepository.countByRole(UserRole.ADMIN)
        ).isEqualTo(1);
    }

    // ========================================
    // 3. 두 관리자가 동시에 서로를 잠금
    // ========================================

    @Test
    void concurrentLocksLeaveOneActiveAdmin()
            throws Exception {

        User adminA = createAdmin("lock-admin-a");
        User adminB = createAdmin("lock-admin-b");

        UUID adminAId = adminA.getId();
        UUID adminBId = adminB.getId();

        List<Result> results = runConcurrently(
                () -> {
                    adminUserService.changeLocked(
                            adminBId,
                            true,
                            adminAId
                    );
                },
                () -> {
                    adminUserService.changeLocked(
                            adminAId,
                            true,
                            adminBId
                    );
                }
        );

        assertThat(results)
                .containsExactlyInAnyOrder(
                        Result.SUCCESS,
                        Result.BLOCKED
                );

        // 실제 DB에서 활성 관리자 수 확인
        assertThat(countActiveAdmins()).isEqualTo(1);

        // 둘 다 ADMIN 역할이지만 한 명만 잠겨 있어야 함
        assertThat(
                userRepository.countByRole(UserRole.ADMIN)
        ).isEqualTo(2);
    }

    // ========================================
    // 4. 관리자 생성
    // ========================================

    private User createAdmin(String prefix) {

        String email = prefix
                + "-"
                + UUID.randomUUID()
                + "@example.com";

        User admin = User.createAdmin(
                "관리자",
                email,
                "test-encoded-password"
        );

        return userRepository.saveAndFlush(admin);
    }

    // ========================================
    // 5. 활성 관리자 수 조회
    // ========================================

    private long countActiveAdmins() {

        return userRepository.findAll()
                .stream()
                .filter(user ->
                        user.getRole() == UserRole.ADMIN
                                && !user.isLocked()
                )
                .count();
    }

    // ========================================
    // 6. 동시 요청 실행
    // ========================================

    private List<Result> runConcurrently(
            ThrowingAction firstAction,
            ThrowingAction secondAction
    ) throws Exception {

        var executor = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);

        try {
            Callable<Result> firstTask = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return executeAction(firstAction);
            };

            Callable<Result> secondTask = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return executeAction(secondAction);
            };

            var firstFuture = executor.submit(firstTask);
            var secondFuture = executor.submit(secondTask);

            return List.of(
                    firstFuture.get(20, TimeUnit.SECONDS),
                    secondFuture.get(20, TimeUnit.SECONDS)
            );

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
    // 7. 각 작업의 실행 결과
    // ========================================

    private Result executeAction(
            ThrowingAction action
    ) throws Exception {

        try {
            action.run();
            return Result.SUCCESS;

        } catch (IllegalStateException e) {

            // 마지막 활성 관리자 보호 예외만 정상 차단으로 인정
            if (e.getMessage() != null
                    && e.getMessage().startsWith(
                            "마지막 활성 관리자"
                    )) {
                return Result.BLOCKED;
            }

            throw e;
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private enum Result {
        SUCCESS,
        BLOCKED
    }
}
