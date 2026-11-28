package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.weather.client.KakaoLocationClient;
import com.project.sb13ootbsteam04.weather.client.KmaForecastClient;
import com.project.sb13ootbsteam04.weather.dto.KmaForecastItem;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocation;
import com.project.sb13ootbsteam04.weather.event.WeatherChangeEventPublisher;
import com.project.sb13ootbsteam04.weather.exception.WeatherForecastException;
import com.project.sb13ootbsteam04.weather.exception.WeatherLocationException;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationNameRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import com.project.sb13ootbsteam04.weather.service.condition.WeatherLocationCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

// 실제 DB에 저장되는 위치·예보로 검증하고, 외부 API(기상청·카카오)만 대체한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, WeatherGridConverter.class, WeatherForecastService.class,
        KmaForecastMapper.class, WeatherChangeDetector.class})
@DisplayName("날씨 조회 전 예보 준비")
class WeatherForecastInitializerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final double LATITUDE = 37.5665;
    private static final double LONGITUDE = 126.978;
    // 2026-11-28 14:30(KST) 기준 최신 발표는 14:00
    private static final Clock NOW = Clock.fixed(
            ZonedDateTime.of(2026, 11, 28, 14, 30, 0, 0, SEOUL).toInstant(), SEOUL);

    @Autowired private WeatherGridConverter gridConverter;
    @Autowired private WeatherRepository weathers;
    @Autowired private WeatherLocationRepository locations;
    @Autowired private WeatherLocationNameRepository locationNames;
    @Autowired private WeatherForecastService forecastService;

    @MockitoBean private KmaForecastClient forecastClient;
    @MockitoBean private KakaoLocationClient locationClient;
    @MockitoBean private WeatherChangeEventPublisher changeEventPublisher;

    private final WeatherLocationCondition condition = new WeatherLocationCondition(LATITUDE, LONGITUDE);

    @Test
    @DisplayName("최신 발표 예보가 이미 있으면 위치와 예보를 새로 만들지 않는다")
    void keepsDataWhenLatestForecastExists() {
        var location = saveLocation();
        forecastService.saveForecast(location.getId(), items("20261128", "1400", "20261128", "1500"));

        initializer().ensureForecast(condition);

        assertThat(locations.count()).isEqualTo(1);
        assertThat(weathers.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("처음 조회하는 위치는 위치·행정구역 이름·최신 예보를 저장한다")
    void registersLocationAndForecast() {
        when(locationClient.getLocationNames(LATITUDE, LONGITUDE)).thenReturn(List.of("서울특별시", "중구"));
        when(forecastClient.getVillageForecast(LocalDate.of(2026, 11, 28), LocalTime.of(14, 0), 60, 127))
                .thenReturn(items("20261128", "1400", "20261128", "1500"));

        initializer().ensureForecast(condition);

        var location = locations.findAll().get(0);
        assertThat(locations.count()).isEqualTo(1);
        assertThat(locationNames.findAllByLocationIdOrderByDisplayOrderAsc(location.getId()))
                .extracting("name").containsExactly("서울특별시", "중구");
        assertThat(weathers.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("행정구역 이름을 받지 못하면 위치를 등록하지 않는다")
    void skipsRegistrationWhenLocationNameLookupFails() {
        when(locationClient.getLocationNames(anyDouble(), anyDouble()))
                .thenThrow(new WeatherLocationException(ErrorCode.WEATHER_LOCATION_SERVICE_UNAVAILABLE));

        assertThatCode(() -> initializer().ensureForecast(condition)).doesNotThrowAnyException();

        assertThat(locations.count()).isZero();
        assertThat(weathers.count()).isZero();
    }

    @Test
    @DisplayName("예보가 오래된 기존 위치는 다시 등록하지 않고 최신 예보만 추가한다")
    void refreshesStaleForecastWithoutDuplicatingLocation() {
        var location = saveLocation();
        forecastService.saveForecast(location.getId(), items("20261127", "2300", "20261128", "0000"));
        when(forecastClient.getVillageForecast(any(), any(), anyInt(), anyInt()))
                .thenReturn(items("20261128", "1400", "20261128", "1500"));

        initializer().ensureForecast(condition);

        assertThat(locations.count()).isEqualTo(1);
        var latest = weathers.findForecastSourcesByGrid(60, 127, PageRequest.of(0, 1)).get(0);
        assertThat(latest.getForecastedAt())
                .isEqualTo(ZonedDateTime.of(2026, 11, 28, 14, 0, 0, 0, SEOUL).toInstant());
    }

    @Test
    @DisplayName("기상청 조회가 실패해도 예외 없이 기존 예보를 유지한다")
    void keepsExistingForecastWhenKmaFails() {
        var location = saveLocation();
        forecastService.saveForecast(location.getId(), items("20261127", "2300", "20261128", "0000"));
        when(forecastClient.getVillageForecast(any(), any(), anyInt(), anyInt()))
                .thenThrow(new WeatherForecastException(ErrorCode.WEATHER_FORECAST_SERVICE_UNAVAILABLE, "점검 중"));

        assertThatCode(() -> initializer().ensureForecast(condition)).doesNotThrowAnyException();

        assertThat(weathers.count()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0} 기준 최신 발표 시각은 {1}")
    @CsvSource({
            "2026-11-28T02:09, 2026-11-27T23:00",
            "2026-11-28T02:10, 2026-11-28T02:00",
            "2026-11-28T14:30, 2026-11-28T14:00"
    })
    @DisplayName("기상청 단기예보 발표 시각과 제공 지연 10분을 반영해 최신 발표 시각을 계산한다")
    void calculatesLatestIssueTime(LocalDateTime now, LocalDateTime expectedIssue) {
        var initializer = initializer(Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL));

        assertThat(initializer.latestIssueTime()).isEqualTo(expectedIssue.atZone(SEOUL));
    }

    private WeatherForecastInitializer initializer() {
        return initializer(NOW);
    }

    private WeatherForecastInitializer initializer(Clock clock) {
        return new WeatherForecastInitializer(gridConverter, weathers, locations, locationNames,
                forecastClient, locationClient, forecastService, clock);
    }

    private WeatherLocation saveLocation() {
        return locations.saveAndFlush(new WeatherLocation(LATITUDE, LONGITUDE, 60, 127));
    }

    private List<KmaForecastItem> items(String baseDate, String baseTime, String forecastDate, String forecastTime) {
        return Map.of("SKY", "1", "PTY", "0", "PCP", "강수없음", "POP", "0", "REH", "50", "TMP", "10", "WSD", "1.0")
                .entrySet().stream()
                .map(entry -> new KmaForecastItem(baseDate, baseTime, forecastDate, forecastTime,
                        entry.getKey(), entry.getValue(), 60, 127))
                .toList();
    }
}
