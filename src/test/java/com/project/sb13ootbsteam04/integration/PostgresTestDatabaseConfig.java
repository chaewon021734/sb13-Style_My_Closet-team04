
package com.project.sb13ootbsteam04.integration;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.net.URI;

public final class PostgresTestDatabaseConfig {

    private PostgresTestDatabaseConfig() {
    }

    public static void register(
            DynamicPropertyRegistry registry,
            String testSchema
    ) {
        registry.add(
                "spring.datasource.url",
                PostgresTestDatabaseConfig::validatedUrl
        );

        registry.add(
                "spring.jpa.properties.hibernate.default_schema",
                () -> testSchema
        );

        registry.add(
                "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces",
                () -> true
        );

        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "create-drop"
        );

        registry.add(
                "spring.flyway.enabled",
                () -> false
        );
    }

    private static String validatedUrl() {

        String url = System.getenv("OTBOO_TEST_DB_URL");

        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "OTBOO_TEST_DB_URL 환경변수가 필요합니다."
            );
        }

        if (!url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException(
                    "PostgreSQL JDBC URL만 허용됩니다."
            );
        }

        try {
            URI uri = URI.create(url.substring(5));

            if (!"/ootb_test".equals(uri.getPath())
                    || uri.getHost() == null) {
                throw new IllegalStateException(
                        "PostgreSQL 테스트는 ootb_test DB에서만 실행할 수 있습니다."
                );
            }

        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "잘못된 PostgreSQL 테스트 DB URL입니다.",
                    e
            );
        }

        return url;
    }
}
