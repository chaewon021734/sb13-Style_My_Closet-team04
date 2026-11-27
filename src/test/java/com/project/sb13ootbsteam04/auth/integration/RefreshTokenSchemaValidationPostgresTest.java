package com.project.sb13ootbsteam04.auth.integration;

import com.project.sb13ootbsteam04.auth.entity.RefreshToken;
import com.project.sb13ootbsteam04.auth.entity.RefreshTokenRevocationReason;
import com.project.sb13ootbsteam04.auth.repository.RefreshTokenRepository;
import com.project.sb13ootbsteam04.auth.service.RefreshTokenGenerator;
import com.project.sb13ootbsteam04.auth.service.RefreshTokenService;
import com.project.sb13ootbsteam04.integration.PostgresTestDatabaseConfig;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import jakarta.persistence.EntityManagerFactory;

import org.flywaydb.core.Flyway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.time.Instant;
import java.util.Arrays;
import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("postgres-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(
        named = "OTBOO_POSTGRES_TEST",
        matches = "true"
)
class RefreshTokenSchemaValidationPostgresTest {

    private static final String TEST_SCHEMA =
            "otboo_refresh_validate_"
                    + UUID.randomUUID()
                    .toString()
                    .replace("-", "");

    @DynamicPropertySource
    static void configurePostgres(
            DynamicPropertyRegistry registry
    ) {

        // 기존 PostgreSQL 테스트 DB 안전장치 재사용
        PostgresTestDatabaseConfig.register(
                registry,
                TEST_SCHEMA
        );

        // Hibernate 자동 테이블 생성 금지
        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "validate"
        );

        registry.add(
                "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces",
                () -> false
        );

        // 실제 Flyway 마이그레이션 활성화
        registry.add(
                "spring.flyway.enabled",
                () -> true
        );

        registry.add(
                "spring.flyway.schemas",
                () -> TEST_SCHEMA
        );

        registry.add(
                "spring.flyway.default-schema",
                () -> TEST_SCHEMA
        );

        registry.add(
                "spring.flyway.create-schemas",
                () -> true
        );

        registry.add(
                "spring.flyway.clean-disabled",
                () -> true
        );

        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration"
        );

        // 테스트 중 불필요한 배치 실행 방지
        registry.add(
                "spring.batch.jdbc.initialize-schema",
                () -> "never"
        );

        registry.add(
                "spring.batch.job.enabled",
                () -> false
        );

        registry.add(
                "weather.batch.schedule.enabled",
                () -> false
        );
    }

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenGenerator refreshTokenGenerator;

    // ========================================
    // 1. Flyway 마이그레이션 이후 Hibernate 검증
    // ========================================

    @Test
    void shouldValidateFlywaySchemaWithHibernate() {

        assertThat(entityManagerFactory.isOpen())
                .isTrue();

        assertThat(
                entityManagerFactory.getProperties()
                        .get("hibernate.hbm2ddl.auto")
        ).isEqualTo("validate");

        assertThat(
                entityManagerFactory.getMetamodel()
                        .entity(RefreshToken.class)
        ).isNotNull();

        flyway.validate();

        assertAppliedMigration("14");
        assertAppliedMigration("15");
    }

    // ========================================
    // 2. Flyway 생성 테이블에 실제 JPA 데이터 저장
    // ========================================

    @Test
    void shouldPersistRefreshTokenInFlywayCreatedTable() {

        User user = userRepository.saveAndFlush(
                new User(
                        "매핑검증",
                        UUID.randomUUID() + "@example.com",
                        "test-encoded-password"
                )
        );

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

        assertThat(savedToken.getLoginVersion())
                .isEqualTo(user.getLoginVersion());

        assertThat(savedToken.isRevoked())
                .isFalse();

        assertThat(
                refreshTokenRepository
                        .findUserIdByTokenHash(tokenHash)
        ).contains(user.getId());
    }

    // ========================================
    // 3. Flyway 생성 테이블의 CASCADE 동작 검증
    // ========================================

    @Test
    void shouldCascadeDeleteRefreshToken()
            throws Exception {

        User user = userRepository.saveAndFlush(
                new User(
                        "삭제검증",
                        UUID.randomUUID() + "@example.com",
                        "test-encoded-password"
                )
        );

        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user);

        String tokenHash = refreshTokenGenerator.hash(
                issued.rawToken()
        );

        assertThat(
                refreshTokenRepository.findByTokenHash(
                        tokenHash
                )
        ).isPresent();

        userRepository.deleteById(user.getId());

        assertThat(
                refreshTokenRepository.findByTokenHash(
                        tokenHash
                )
        ).isEmpty();
    }

    // ========================================
    // 4. 실제 테스트 DB 연결 확인
    // ========================================

    @Test
    void shouldUseOnlyDedicatedTestDatabase()
            throws Exception {

        try (
                Connection connection =
                        dataSource.getConnection();

                Statement statement =
                        connection.createStatement();

                ResultSet result =
                        statement.executeQuery(
                                "SELECT current_database()"
                        )
        ) {

            assertThat(result.next()).isTrue();

            assertThat(result.getString(1))
                    .isEqualTo("ootb_test");
        }
    }

    // ========================================
    // V15 폐기 사유 JPA 매핑 검증
    // ========================================

    @Test
    void shouldPersistNullRevocationReasonForActiveToken() throws SQLException {
        RefreshToken token = createMappingTestToken();
        assertThat(token.getRevokedAt()).isNull();
        assertThat(token.getRevocationReason()).isNull();
        assertStoredRevocationState(token.getId(), null);
    }

    @Test
    void shouldPersistRotatedReasonAsString() throws SQLException {
        assertRevocationMapping(RefreshTokenRevocationReason.ROTATED);
    }

    @Test
    void shouldPersistLogoutReasonAsString() throws SQLException {
        assertRevocationMapping(RefreshTokenRevocationReason.LOGOUT);
    }

    @Test
    void shouldPersistRevokedReasonAsString() throws SQLException {
        assertRevocationMapping(RefreshTokenRevocationReason.REVOKED);
    }

    @Test
    void shouldPreserveFirstRevocationReasonAndTime() throws SQLException {
        RefreshToken token = createMappingTestToken();
        token.revoke(Instant.now(), RefreshTokenRevocationReason.ROTATED);
        refreshTokenRepository.saveAndFlush(token);

        RefreshToken saved = refreshTokenRepository.findById(token.getId())
                .orElseThrow();
        Instant firstRevokedAt = saved.getRevokedAt();
        saved.revoke(firstRevokedAt.plusSeconds(1), RefreshTokenRevocationReason.LOGOUT);
        refreshTokenRepository.saveAndFlush(saved);

        RefreshToken reloaded = refreshTokenRepository.findById(token.getId())
                .orElseThrow();
        assertThat(reloaded.getRevokedAt()).isEqualTo(firstRevokedAt);
        assertThat(reloaded.getRevocationReason())
                .isEqualTo(RefreshTokenRevocationReason.ROTATED);
        assertStoredRevocationState(token.getId(), "ROTATED");
    }

    private void assertAppliedMigration(String version) {
        assertThat(Arrays.stream(flyway.info().applied())
                .anyMatch(migration -> migration.getVersion() != null
                        && version.equals(migration.getVersion().getVersion())
                        && "SUCCESS".equals(migration.getState().name())))
                .as("Applied Flyway migration V%s", version)
                .isTrue();
    }

    private RefreshToken createMappingTestToken() {
        User user = userRepository.saveAndFlush(new User(
                "폐기사유검증", UUID.randomUUID() + "@example.com",
                "test-encoded-password"));
        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issue(user);
        return refreshTokenRepository.findByTokenHash(
                refreshTokenGenerator.hash(issued.rawToken())).orElseThrow();
    }

    private void assertRevocationMapping(RefreshTokenRevocationReason reason)
            throws SQLException {
        RefreshToken token = createMappingTestToken();
        token.revoke(Instant.now(), reason);
        refreshTokenRepository.saveAndFlush(token);

        RefreshToken reloaded = refreshTokenRepository.findById(token.getId())
                .orElseThrow();
        assertThat(reloaded.isRevoked()).isTrue();
        assertThat(reloaded.getRevokedAt()).isNotNull();
        assertThat(reloaded.getRevocationReason()).isEqualTo(reason);
        assertStoredRevocationState(token.getId(), reason.name());
    }

    private void assertStoredRevocationState(UUID tokenId, String expectedReason)
            throws SQLException {
        String sql = "SELECT revocation_reason, revoked_at FROM "
                + TEST_SCHEMA + ".refresh_tokens WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tokenId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("revocation_reason")).isEqualTo(expectedReason);
                if (expectedReason == null) {
                    assertThat(result.getTimestamp("revoked_at")).isNull();
                } else {
                    assertThat(result.getTimestamp("revoked_at")).isNotNull();
                }
            }
        }
    }

    // ========================================
    // 테스트 종료 후 독립 테스트 스키마 정리
    // ========================================

    @AfterAll
    static void cleanUpTestSchema() throws SQLException {

        String dbUrl =
                System.getenv("OTBOO_TEST_DB_URL");

        String dbUsername =
                System.getenv("OTBOO_TEST_DB_USERNAME");

        String dbPassword =
                System.getenv("OTBOO_TEST_DB_PASSWORD");

        // PostgreSQL 테스트 DB URL 검증
        if (dbUrl == null
                || !dbUrl.startsWith("jdbc:postgresql://")) {

            throw new IllegalStateException(
                    "PostgreSQL 테스트 DB URL이 필요합니다."
            );
        }

        URI uri = URI.create(dbUrl.substring(5));

        // 실제 개발 DB 접근 방지
        if (!"/ootb_test".equals(uri.getPath())
                || uri.getHost() == null) {

            throw new IllegalStateException(
                    "테스트 스키마 정리는 ootb_test DB에서만 가능합니다."
            );
        }

        // 테스트 DB 인증 정보 검증
        if (dbUsername == null
                || dbUsername.isBlank()
                || dbPassword == null) {

            throw new IllegalStateException(
                    "PostgreSQL 테스트 DB 인증 정보가 필요합니다."
            );
        }

        // 이번 테스트에서 생성한 스키마 이름만 허용
        if (!TEST_SCHEMA.matches(
                "otboo_refresh_validate_[0-9a-f]{32}"
        )) {

            throw new IllegalStateException(
                    "허용되지 않은 테스트 스키마입니다."
            );
        }

        try (
                Connection connection =
                        DriverManager.getConnection(
                                dbUrl,
                                dbUsername,
                                dbPassword
                        )
        ) {

            // 실제 연결된 데이터베이스 재검증
            try (
                    Statement statement =
                            connection.createStatement();

                    ResultSet result =
                            statement.executeQuery(
                                    "SELECT current_database()"
                            )
            ) {

                if (!result.next()
                        || !"ootb_test".equals(
                                result.getString(1)
                        )) {

                    throw new IllegalStateException(
                            "테스트 전용 DB가 아닙니다."
                    );
                }
            }

            // 이번 테스트에서 생성한 독립 스키마만 삭제
            try (
                    Statement cleanup =
                            connection.createStatement()
            ) {

                cleanup.execute(
                        "DROP SCHEMA IF EXISTS "
                                + TEST_SCHEMA
                                + " CASCADE"
                );
            }
        }
    }
}