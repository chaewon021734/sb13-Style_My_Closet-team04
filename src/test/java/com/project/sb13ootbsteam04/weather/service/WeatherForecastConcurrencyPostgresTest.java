package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import com.project.sb13ootbsteam04.weather.dto.KmaForecastItem;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocation;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("postgres-test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        JpaAuditingConfig.class,
        WeatherForecastService.class,
        KmaForecastMapper.class
})
@EnabledIfEnvironmentVariable(
        named = "OTBOO_POSTGRES_TEST",
        matches = "true"
)
class WeatherForecastConcurrencyPostgresTest {

    // ========================================
    // 1. PostgreSQL 테스트 설정
    // ========================================

    private static final String TEST_SCHEMA =
            "otboo_test_" + UUID.randomUUID()
                    .toString()
                    .replace("-", "");

    private static final Instant ISSUE =
            Instant.parse("2026-11-09T02:00:00Z");

    private static final Map<String, String> DRY = Map.of(
            "SKY", "3",
            "PTY", "0",
            "PCP", "강수없음",
            "POP", "20",
            "REH", "60",
            "TMP", "12",
            "WSD", "1.2"
    );

    // ========================================
    // 2. 테스트 전용 PostgreSQL DB 설정
    // ========================================

    @DynamicPropertySource
    static void configurePostgres(
            DynamicPropertyRegistry registry
    ) {

        registry.add("spring.datasource.url", () -> {

            String url = System.getenv("OTBOO_TEST_DB_URL");

            // 개발용 DB 연결 방지
            // 전용 DB 이름은 ootb_test로 고정
            if (url == null ||
                    !url.matches(
                            "^jdbc:postgresql://[^/?]+/ootb_test(\\?.*)?$"
                    )) {

                throw new IllegalStateException(
                        "PostgreSQL 테스트 전용 DB가 필요합니다. " +
                                "OTBOO_TEST_DB_URL에 " +
                                "jdbc:postgresql://localhost:5432/ootb_test " +
                                "형식의 주소를 설정하세요."
                );
            }

            return url;
        });

        // 각 테스트 클래스의 독립적인 스키마
        registry.add(
                "spring.jpa.properties.hibernate.default_schema",
                () -> TEST_SCHEMA
        );

        // 테스트 스키마 자동 생성
        registry.add(
                "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces",
                () -> true
        );

        // 테스트 테이블 생성 및 제거
        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "create-drop"
        );

        // PostgreSQL 동시성 테스트에서는
        // Flyway 마이그레이션을 실행하지 않음
        registry.add(
                "spring.flyway.enabled",
                () -> false
        );
    }

    // ========================================
    // 3. 테스트 의존성
    // ========================================

    @Autowired
    private WeatherForecastService service;

    @Autowired
    private WeatherLocationRepository locations;

    @Autowired
    private WeatherRepository weathers;

    private UUID locationId;

    @BeforeEach
    void registerLocation() {

        locationId = locations.saveAndFlush(
                new WeatherLocation(
                        37.5665,
                        126.978,
                        60,
                        127
                )
        ).getId();
    }

    // ========================================
    // 4. 동일 위치 / 발표 시각 동시 저장 검증
    // ========================================

    @Test
    void concurrentSavesForTheSameLocationAndIssuePersistOneForecast()
            throws Exception {

        // 별도 스레드에서도 위치 데이터를 조회할 수 있도록
        // 준비 트랜잭션을 커밋한다.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        var items = slot("1100", "1400", DRY);

        var executor = Executors.newFixedThreadPool(2);
        var start = new CyclicBarrier(2);

        try {

            // 첫 번째 저장 요청
            var first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);

                return service.saveForecast(
                        locationId,
                        items
                );
            });

            // 두 번째 저장 요청
            var second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);

                return service.saveForecast(
                        locationId,
                        items
                );
            });

            // 한 요청만 신규 저장되고
            // 다른 요청은 중복으로 처리되어야 함
            assertThat(
                    List.of(
                            first.get(10, TimeUnit.SECONDS),
                            second.get(10, TimeUnit.SECONDS)
                    )
            ).containsExactlyInAnyOrder(1, 0);

            // 실제 DB에는 예보 데이터 1개만 존재해야 함
            assertThat(
                    weathers
                            .findAllByLocationIdAndForecastedAtOrderByForecastAtAsc(
                                    locationId,
                                    ISSUE
                            )
            ).hasSize(1);

        } finally {

            executor.shutdownNow();

            // 실행 중인 작업이 끝나도록 정리
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "동시성 테스트 작업이 종료되지 않았습니다."
                );
            }

            // 테스트 데이터 정리
            weathers.deleteAll(
                    weathers
                            .findAllByLocationIdAndForecastedAtOrderByForecastAtAsc(
                                    locationId,
                                    ISSUE
                            )
            );

            locations.deleteById(locationId);
        }
    }

    // ========================================
    // 5. 테스트용 예보 데이터 생성
    // ========================================

    private List<KmaForecastItem> slot(
            String baseTime,
            String forecastTime,
            Map<String, String> values
    ) {

        return values.entrySet()
                .stream()
                .map(entry -> new KmaForecastItem(
                        "20261109",
                        baseTime,
                        "20261109",
                        forecastTime,
                        entry.getKey(),
                        entry.getValue(),
                        60,
                        127
                ))
                .toList();
    }
}
