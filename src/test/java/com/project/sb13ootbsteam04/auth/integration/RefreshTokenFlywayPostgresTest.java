package com.project.sb13ootbsteam04.auth.integration;

import org.flywaydb.core.Flyway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(
        named = "OTBOO_POSTGRES_TEST",
        matches = "true"
)
class RefreshTokenFlywayPostgresTest {

    private static final String TEST_SCHEMA =
            "otboo_refresh_flyway_"
                    + UUID.randomUUID()
                    .toString()
                    .replace("-", "");

    private static String dbUrl;
    private static String dbUsername;
    private static String dbPassword;

    private static Flyway flyway;
    private static boolean schemaCreated;

    // ========================================
    // 테스트 전용 Flyway 환경 초기화
    // ========================================

    @BeforeAll
    static void setUp() throws SQLException {

        dbUrl = System.getenv("OTBOO_TEST_DB_URL");
        dbUsername = System.getenv("OTBOO_TEST_DB_USERNAME");
        dbPassword = System.getenv("OTBOO_TEST_DB_PASSWORD");

        validateTestDatabase();

        // 독립적인 테스트 스키마 생성
        try (
                Connection connection = openConnection();
                Statement statement = connection.createStatement()
        ) {

            statement.execute(
                    "CREATE SCHEMA " + TEST_SCHEMA
            );

            schemaCreated = true;
        }

        flyway = Flyway.configure()
                .dataSource(
                        dbUrl,
                        dbUsername,
                        dbPassword
                )
                .schemas(TEST_SCHEMA)
                .defaultSchema(TEST_SCHEMA)
                .createSchemas(false)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();

        // 현재 존재하는 모든 마이그레이션 실행
        flyway.migrate();
    }

    @AfterAll
    static void tearDown() throws SQLException {

        if (!schemaCreated) {
            return;
        }

        validateTestDatabase();

        // 이번 테스트에서 생성한 스키마만 제거
        try (
                Connection connection = openConnection();
                Statement statement = connection.createStatement()
        ) {

            statement.execute(
                    "DROP SCHEMA "
                            + TEST_SCHEMA
                            + " CASCADE"
            );
        }
    }

    // ========================================
    // 1. Flyway 마이그레이션 정상 적용
    // ========================================

    @Test
    void shouldApplyRefreshTokenMigration() {

        flyway.validate();

        assertAppliedMigration("14");
    }

    // ========================================
    // 2. Refresh Token 테이블 생성
    // ========================================

    @Test
    void shouldCreateRefreshTokenTable()
            throws SQLException {

        String sql = """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_name = 'refresh_tokens'
                """;

        try (
                Connection connection = openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(1, TEST_SCHEMA);

            try (ResultSet result = statement.executeQuery()) {

                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(1);
            }
        }
    }

    // ========================================
    // 3. Refresh Token 컬럼 생성 검증
    // ========================================

    @Test
    void shouldCreateAllRefreshTokenColumns()
            throws SQLException {

        String sql = """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = 'refresh_tokens'
                """;

        try (
                Connection connection = openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(1, TEST_SCHEMA);

            java.util.List<String> columns =
                    new java.util.ArrayList<>();

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    columns.add(result.getString("column_name"));
                }
            }

            assertThat(columns)
                    .contains(
                            "id",
                            "user_id",
                            "token_hash",
                            "login_version",
                            "expires_at",
                            "created_at",
                            "revoked_at",
                            "revocation_reason"
                    );
        }
    }

    // ========================================
    // 4. 사용자 및 만료 시간 인덱스 검증
    // ========================================

    @Test
    void shouldCreateRefreshTokenIndexes()
            throws SQLException {

        String sql = """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = ?
                  AND tablename = 'refresh_tokens'
                """;

        try (
                Connection connection = openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(1, TEST_SCHEMA);

            java.util.List<String> indexes =
                    new java.util.ArrayList<>();

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    indexes.add(result.getString("indexname"));
                }
            }

            assertThat(indexes)
                    .contains(
                            "idx_refresh_tokens_user_id",
                            "idx_refresh_tokens_expires_at",
                            "uk_refresh_tokens_token_hash"
                    );
        }
    }

    // ========================================
    // 5. 정상 Refresh Token 저장
    // ========================================

    @Test
    void shouldInsertValidRefreshToken()
            throws SQLException {

        UUID userId = insertUser();

        String tokenHash = generateTestHash();

        insertRefreshToken(
                userId,
                tokenHash,
                false
        );

        assertThat(countTokensByHash(tokenHash))
                .isEqualTo(1);
    }

    // ========================================
    // 6. 중복 해시값 저장 방지
    // ========================================

    @Test
    void shouldRejectDuplicateTokenHash()
            throws SQLException {

        UUID userId = insertUser();

        String tokenHash = generateTestHash();

        insertRefreshToken(
                userId,
                tokenHash,
                false
        );

        assertThatThrownBy(
                () -> insertRefreshToken(
                        userId,
                        tokenHash,
                        false
                )
        )
                .isInstanceOfSatisfying(
                        SQLException.class,
                        exception -> {

                            // PostgreSQL UNIQUE 제약조건 위반
                            assertThat(exception.getSQLState())
                                    .isEqualTo("23505");

                            assertThat(exception.getMessage())
                                    .contains(
                                            "uk_refresh_tokens_token_hash"
                                    );
                        }
                );

        assertThat(countTokensByHash(tokenHash))
                .isEqualTo(1);
    }

    // ========================================
    // 7. 존재하지 않는 사용자 참조 차단
    // ========================================

    @Test
    void shouldRejectTokenForUnknownUser() {

        UUID unknownUserId = UUID.randomUUID();

        assertThatThrownBy(
                () -> insertRefreshToken(
                        unknownUserId,
                        generateTestHash(),
                        false
                )
        )
                .isInstanceOfSatisfying(
                        SQLException.class,
                        exception -> {

                            // PostgreSQL FOREIGN KEY 위반
                            assertThat(exception.getSQLState())
                                    .isEqualTo("23503");

                            assertThat(exception.getMessage())
                                    .contains("fk_refresh_tokens_user");
                        }
                );
    }

    // ========================================
    // 8. 잘못된 만료 시간 저장 차단
    // ========================================

    @Test
    void shouldRejectInvalidExpiration()
            throws SQLException {

        UUID userId = insertUser();

        assertThatThrownBy(
                () -> insertRefreshToken(
                        userId,
                        generateTestHash(),
                        true
                )
        )
                .isInstanceOfSatisfying(
                        SQLException.class,
                        exception -> {

                            // PostgreSQL CHECK 제약조건 위반
                            assertThat(exception.getSQLState())
                                    .isEqualTo("23514");

                            assertThat(exception.getMessage())
                                    .contains(
                                            "chk_refresh_tokens_expiration"
                                    );
                        }
                );
    }

    // ========================================
    // 9. 사용자 삭제 시 Refresh Token 자동 삭제
    // ========================================

    @Test
    void shouldCascadeDeleteTokensWhenUserIsDeleted()
            throws SQLException {

        UUID userId = insertUser();

        String firstHash = generateTestHash();
        String secondHash = generateTestHash();

        insertRefreshToken(
                userId,
                firstHash,
                false
        );

        insertRefreshToken(
                userId,
                secondHash,
                false
        );

        assertThat(countTokensByHash(firstHash))
                .isEqualTo(1);

        assertThat(countTokensByHash(secondHash))
                .isEqualTo(1);

        String sql = "DELETE FROM "
                + TEST_SCHEMA
                + ".users WHERE id = ?";

        try (
                Connection connection = openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setObject(1, userId);

            assertThat(statement.executeUpdate())
                    .isEqualTo(1);
        }

        // ON DELETE CASCADE 실제 동작 검증
        assertThat(countTokensByHash(firstHash))
                .isZero();

        assertThat(countTokensByHash(secondHash))
                .isZero();
    }

    // V15 migration and constraints
    @Test
    void shouldApplyRevocationReasonMigration() {
        flyway.validate();
        assertAppliedMigration("15");
    }

    @Test
    void shouldCreateRevocationReasonColumn() throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT column_name FROM information_schema.columns
                     WHERE table_schema = ? AND table_name = 'refresh_tokens'
                       AND column_name = 'revocation_reason'
                     """)) {
            statement.setString(1, TEST_SCHEMA);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("revocation_reason");
                assertThat(result.next()).isFalse();
            }
        }
    }

    @Test
    void shouldUseNullableVarchar20ForRevocationReason() throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT data_type, character_maximum_length, is_nullable
                     FROM information_schema.columns
                     WHERE table_schema = ? AND table_name = 'refresh_tokens'
                       AND column_name = 'revocation_reason'
                     """)) {
            statement.setString(1, TEST_SCHEMA);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("character varying");
                assertThat(result.getInt(2)).isEqualTo(20);
                assertThat(result.getString(3)).isEqualTo("YES");
            }
        }
    }

    @Test
    void shouldAllowNullRevocationStateForActiveToken() throws SQLException {
        assertAcceptedState(false, null);
    }

    @Test
    void shouldAllowRotatedReason() throws SQLException {
        assertAcceptedState(true, "ROTATED");
    }

    @Test
    void shouldAllowLogoutReason() throws SQLException {
        assertAcceptedState(true, "LOGOUT");
    }

    @Test
    void shouldAllowRevokedReason() throws SQLException {
        assertAcceptedState(true, "REVOKED");
    }

    @Test
    void shouldRejectUnknownRevocationReason() throws SQLException {
        assertRejectedState(true, "UNKNOWN", "chk_refresh_tokens_revocation_reason");
    }

    @Test
    void shouldRejectRevokedTimeWithoutReason() throws SQLException {
        assertRejectedState(true, null, "chk_refresh_tokens_revocation_state");
    }

    @Test
    void shouldRejectReasonWithoutRevokedTime() throws SQLException {
        assertRejectedState(false, "LOGOUT", "chk_refresh_tokens_revocation_state");
    }

    @Test
    void shouldBackfillExistingRevokedTokensWhenUpgradingFromV14() throws SQLException {
        String schema = "otboo_refresh_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        validateTestDatabase();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        try {
            migrationFor(schema, "14").migrate();
            UUID userId = insertUser(schema);
            String revokedHash = generateTestHash();
            String activeHash = generateTestHash();
            insertLegacyToken(schema, userId, revokedHash, true);
            insertLegacyToken(schema, userId, activeHash, false);
            java.time.OffsetDateTime originalRevokedAt = readRevokedAt(schema, revokedHash);
            assertThat(originalRevokedAt).isNotNull();

            Flyway upgrade = migrationFor(schema, "15");
            upgrade.migrate();
            upgrade.validate();
            assertStoredState(schema, revokedHash, true, "REVOKED");
            assertStoredState(schema, activeHash, false, null);
            assertThat(readRevokedAt(schema, revokedHash)).isEqualTo(originalRevokedAt);
        } finally {
            validateTestDatabase();
            try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private static Flyway migrationFor(String schema, String target) {
        return Flyway.configure()
                .dataSource(dbUrl, dbUsername, dbPassword)
                .schemas(schema).defaultSchema(schema)
                .createSchemas(false).locations("classpath:db/migration")
                .cleanDisabled(true).target(target).load();
    }

    private void assertAppliedMigration(String version) {
        assertThat(flyway.info().applied()).anySatisfy(migration -> {
            assertThat(migration.getVersion()).isNotNull();
            assertThat(migration.getVersion().getVersion()).isEqualTo(version);
            assertThat(migration.getState().name()).isEqualTo("SUCCESS");
        });
    }

    private void assertAcceptedState(boolean revoked, String reason) throws SQLException {
        UUID userId = insertUser();
        String hash = generateTestHash();
        insertTokenWithState(userId, hash, revoked, reason);
        assertStoredState(TEST_SCHEMA, hash, revoked, reason);
    }

    private void assertRejectedState(boolean revoked, String reason, String constraint) throws SQLException {
        UUID userId = insertUser();
        String hash = generateTestHash();
        assertThatThrownBy(() -> insertTokenWithState(userId, hash, revoked, reason))
                .isInstanceOfSatisfying(SQLException.class, exception -> {
                    assertThat(exception.getSQLState()).isEqualTo("23514");
                    assertThat(exception.getMessage()).contains(constraint);
                });
        assertThat(countTokensByHash(hash)).isZero();
    }

    private void insertTokenWithState(UUID userId, String hash, boolean revoked, String reason) throws SQLException {
        String sql = """
                INSERT INTO %s.refresh_tokens
                    (id, user_id, token_hash, login_version, created_at, expires_at, revoked_at, revocation_reason)
                VALUES (?, ?, ?, 0, CURRENT_TIMESTAMP - INTERVAL '1 minute',
                        CURRENT_TIMESTAMP + INTERVAL '7 days', %s, ?)
                """.formatted(TEST_SCHEMA, revoked ? "CURRENT_TIMESTAMP" : "NULL");
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setString(3, hash);
            statement.setString(4, reason);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void insertLegacyToken(String schema, UUID userId, String hash, boolean revoked) throws SQLException {
        String sql = """
                INSERT INTO %s.refresh_tokens
                    (id, user_id, token_hash, login_version, created_at, expires_at, revoked_at)
                VALUES (?, ?, ?, 0, CURRENT_TIMESTAMP - INTERVAL '1 minute',
                        CURRENT_TIMESTAMP + INTERVAL '7 days', %s)
                """.formatted(schema, revoked ? "CURRENT_TIMESTAMP" : "NULL");
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setString(3, hash);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void assertStoredState(String schema, String hash, boolean revoked, String reason) throws SQLException {
        String sql = "SELECT revoked_at, revocation_reason FROM " + schema + ".refresh_tokens WHERE token_hash = ?";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hash);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1) != null).isEqualTo(revoked);
                assertThat(result.getString(2)).isEqualTo(reason);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private java.time.OffsetDateTime readRevokedAt(String schema, String hash) throws SQLException {
        String sql = "SELECT revoked_at FROM " + schema + ".refresh_tokens WHERE token_hash = ?";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hash);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, java.time.OffsetDateTime.class);
            }
        }
    }

    // ========================================
    // 테스트용 사용자 생성
    // ========================================

    private UUID insertUser() throws SQLException {
        return insertUser(TEST_SCHEMA);
    }

    private UUID insertUser(String schema) throws SQLException {

        UUID userId = UUID.randomUUID();

        String sql = """
                INSERT INTO %s.users (
                    id,
                    email,
                    name,
                    password,
                    role,
                    locked,
                    login_version,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, 'USER', false, 0,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """.formatted(schema);

        try (
                Connection connection = openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setObject(1, userId);

            statement.setString(
                    2,
                    userId + "@example.com"
            );

            statement.setString(3, "Flyway테스트");

            statement.setString(
                    4,
                    "test-encoded-password"
            );

            statement.executeUpdate();
        }

        return userId;
    }

    // ========================================
    // 테스트용 Refresh Token 생성
    // ========================================

    private void insertRefreshToken(
            UUID userId,
            String tokenHash,
            boolean invalidExpiration
    ) throws SQLException {

        String expiration = invalidExpiration
                ? "CURRENT_TIMESTAMP - INTERVAL '1 day'"
                : "CURRENT_TIMESTAMP + INTERVAL '7 days'";

        String sql = """
                INSERT INTO %s.refresh_tokens (
                    id,
                    user_id,
                    token_hash,
                    login_version,
                    created_at,
                    expires_at
                )
                VALUES (
                    ?, ?, ?, 0,
                    CURRENT_TIMESTAMP,
                    %s
                )
                """.formatted(
                        TEST_SCHEMA,
                        expiration
                );

        try (
                Connection connection = openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setString(3, tokenHash);

            statement.executeUpdate();
        }
    }

    // ========================================
    // 저장된 토큰 개수 조회
    // ========================================

    private long countTokensByHash(
            String tokenHash
    ) throws SQLException {

        String sql = "SELECT COUNT(*) FROM "
                + TEST_SCHEMA
                + ".refresh_tokens WHERE token_hash = ?";

        try (
                Connection connection = openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(1, tokenHash);

            try (ResultSet result = statement.executeQuery()) {

                result.next();

                return result.getLong(1);
            }
        }
    }

    // ========================================
    // 테스트용 64자리 해시 문자열 생성
    // ========================================

    private String generateTestHash() {

        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                + UUID.randomUUID()
                .toString()
                .replace("-", "");
    }

    // ========================================
    // PostgreSQL 테스트 DB 연결
    // ========================================

    private static Connection openConnection()
            throws SQLException {

        return DriverManager.getConnection(
                dbUrl,
                dbUsername,
                dbPassword
        );
    }

    // ========================================
    // 실제 개발 DB 접근 방지
    // ========================================

    private static void validateTestDatabase() {

        if (dbUrl == null
                || !dbUrl.startsWith("jdbc:postgresql://")) {

            throw new IllegalStateException(
                    "PostgreSQL 테스트 DB URL이 필요합니다."
            );
        }

        URI uri = URI.create(dbUrl.substring(5));

        if (!"/ootb_test".equals(uri.getPath())
                || uri.getHost() == null) {

            throw new IllegalStateException(
                    "Flyway 테스트는 ootb_test DB에서만 실행할 수 있습니다."
            );
        }

        if (dbUsername == null
                || dbUsername.isBlank()
                || dbPassword == null) {

            throw new IllegalStateException(
                    "PostgreSQL 테스트 DB 인증 정보가 필요합니다."
            );
        }
    }
}