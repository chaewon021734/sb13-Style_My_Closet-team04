package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.weather.client.KakaoLocationClient;
import com.project.sb13ootbsteam04.weather.client.KmaForecastClient;
import com.project.sb13ootbsteam04.weather.dto.KmaForecastItem;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocation;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocationName;
import com.project.sb13ootbsteam04.weather.exception.WeatherForecastException;
import com.project.sb13ootbsteam04.weather.exception.WeatherLocationException;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationNameRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository.ForecastSource;
import com.project.sb13ootbsteam04.weather.service.condition.WeatherLocationCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("날씨 조회 전 예보 준비")
class WeatherForecastInitializerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final double LATITUDE = 37.5665;
    private static final double LONGITUDE = 126.978;
    private static final int GRID_X = 60;
    private static final int GRID_Y = 127;

    private final WeatherGridConverter gridConverter = mock(WeatherGridConverter.class);
    private final WeatherRepository weathers = mock(WeatherRepository.class);
    private final WeatherLocationRepository locations = mock(WeatherLocationRepository.class);
    private final WeatherLocationNameRepository locationNames = mock(WeatherLocationNameRepository.class);
    private final KmaForecastClient forecastClient = mock(KmaForecastClient.class);
    private final KakaoLocationClient locationClient = mock(KakaoLocationClient.class);
    private final WeatherForecastService forecastService = mock(WeatherForecastService.class);

    private final WeatherLocationCondition condition = new WeatherLocationCondition(LATITUDE, LONGITUDE);

    @Test
    @DisplayName("최신 발표 예보가 이미 있으면 외부 API를 호출하지 않는다")
    void skipsWhenLatestForecastExists() {
        var initializer = initializerAt(2026, 11, 28, 14, 30);
        var location = location(UUID.randomUUID());
        givenLatestForecast(location, issueAt(2026, 11, 28, 14));

        initializer.ensureForecast(condition);

        verifyNoInteractions(forecastClient, locationClient, forecastService);
        verify(locations, never()).save(any());
    }

    @Test
    @DisplayName("위치가 없으면 위치와 행정구역 이름을 저장하고 예보를 수집한다")
    void registersLocationAndCollectsForecast() {
        var initializer = initializerAt(2026, 11, 28, 14, 30);
        var locationId = UUID.randomUUID();
        givenNoForecast();
        when(locations.save(any(WeatherLocation.class))).thenAnswer(invocation -> {
            WeatherLocation saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", locationId);
            return saved;
        });
        when(locationClient.getLocationNames(LATITUDE, LONGITUDE)).thenReturn(List.of("서울특별시", "중구"));
        var items = List.<KmaForecastItem>of();
        when(forecastClient.getVillageForecast(LocalDate.of(2026, 11, 28), LocalTime.of(14, 0), GRID_X, GRID_Y))
                .thenReturn(items);

        initializer.ensureForecast(condition);

        verify(locations).save(argThat(location -> location.getGridX() == GRID_X
                && location.getGridY() == GRID_Y && location.getLatitude() == LATITUDE));
        verify(locationNames).saveAll(argThat(names -> {
            var list = (List<WeatherLocationName>) names;
            return list.size() == 2 && list.get(0).getName().equals("서울특별시")
                    && list.get(0).getDisplayOrder() == 0 && list.get(1).getLocationId().equals(locationId);
        }));
        verify(forecastService).saveForecast(locationId, items);
    }

    @Test
    @DisplayName("위치는 있지만 예보가 오래됐으면 기존 위치로 다시 수집한다")
    void refreshesStaleForecastForExistingLocation() {
        var initializer = initializerAt(2026, 11, 28, 14, 30);
        var locationId = UUID.randomUUID();
        givenLatestForecast(location(locationId), issueAt(2026, 11, 27, 23));

        initializer.ensureForecast(condition);

        verify(locations, never()).save(any());
        verifyNoInteractions(locationClient);
        verify(forecastClient).getVillageForecast(LocalDate.of(2026, 11, 28), LocalTime.of(14, 0), GRID_X, GRID_Y);
        verify(forecastService).saveForecast(eq(locationId), any());
    }

    @Test
    @DisplayName("행정구역 이름 조회에 실패해도 예보는 수집한다")
    void collectsForecastWhenLocationNameLookupFails() {
        var initializer = initializerAt(2026, 11, 28, 14, 30);
        var locationId = UUID.randomUUID();
        givenNoForecast();
        when(locations.save(any(WeatherLocation.class))).thenAnswer(invocation -> {
            WeatherLocation saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", locationId);
            return saved;
        });
        when(locationClient.getLocationNames(anyDouble(), anyDouble()))
                .thenThrow(new WeatherLocationException(ErrorCode.WEATHER_LOCATION_SERVICE_UNAVAILABLE));

        initializer.ensureForecast(condition);

        verify(locationNames, never()).saveAll(any());
        verify(forecastService).saveForecast(eq(locationId), any());
    }

    @Test
    @DisplayName("기상청 예보 조회에 실패하면 예외 없이 반환한다")
    void ignoresForecastLookupFailure() {
        var initializer = initializerAt(2026, 11, 28, 14, 30);
        givenLatestForecast(location(UUID.randomUUID()), issueAt(2026, 11, 27, 23));
        when(forecastClient.getVillageForecast(any(), any(), anyInt(), anyInt()))
                .thenThrow(new WeatherForecastException(ErrorCode.WEATHER_FORECAST_SERVICE_UNAVAILABLE, "점검 중"));

        assertThatCode(() -> initializer.ensureForecast(condition)).doesNotThrowAnyException();

        verifyNoInteractions(forecastService);
    }

    @ParameterizedTest(name = "{0} 기준 최신 발표 시각은 {1}")
    @CsvSource({
            "2026-11-28T01:00, 2026-11-27T23:00",
            "2026-11-28T02:09, 2026-11-27T23:00",
            "2026-11-28T02:10, 2026-11-28T02:00",
            "2026-11-28T14:30, 2026-11-28T14:00",
            "2026-11-28T23:59, 2026-11-28T23:00"
    })
    @DisplayName("기상청 단기예보 발표 시각과 제공 지연 10분을 반영해 최신 발표 시각을 계산한다")
    void calculatesLatestIssueTime(LocalDateTime now, LocalDateTime expectedIssue) {
        var initializer = new WeatherForecastInitializer(gridConverter, weathers, locations, locationNames,
                forecastClient, locationClient, forecastService, Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL));

        assertThat(initializer.latestIssueTime()).isEqualTo(expectedIssue.atZone(SEOUL));
    }

    private WeatherForecastInitializer initializerAt(int year, int month, int day, int hour, int minute) {
        when(gridConverter.convert(LATITUDE, LONGITUDE)).thenReturn(new WeatherGridConverter.Grid(GRID_X, GRID_Y));
        var now = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, SEOUL);
        return new WeatherForecastInitializer(gridConverter, weathers, locations, locationNames,
                forecastClient, locationClient, forecastService, Clock.fixed(now.toInstant(), SEOUL));
    }

    private void givenNoForecast() {
        when(weathers.findForecastSourcesByGrid(eq(GRID_X), eq(GRID_Y), any())).thenReturn(List.of());
    }

    private void givenLatestForecast(WeatherLocation location, java.time.Instant forecastedAt) {
        var source = mock(ForecastSource.class);
        when(source.getLocation()).thenReturn(location);
        when(source.getForecastedAt()).thenReturn(forecastedAt);
        when(weathers.findForecastSourcesByGrid(eq(GRID_X), eq(GRID_Y), any())).thenReturn(List.of(source));
    }

    private WeatherLocation location(UUID id) {
        var location = new WeatherLocation(LATITUDE, LONGITUDE, GRID_X, GRID_Y);
        ReflectionTestUtils.setField(location, "id", id);
        return location;
    }

    private java.time.Instant issueAt(int year, int month, int day, int hour) {
        return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, SEOUL).toInstant();
    }
}
