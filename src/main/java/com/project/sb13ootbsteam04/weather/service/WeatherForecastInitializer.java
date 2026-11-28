package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.weather.client.KakaoLocationClient;
import com.project.sb13ootbsteam04.weather.client.KmaForecastClient;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocation;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocationName;
import com.project.sb13ootbsteam04.weather.exception.WeatherForecastException;
import com.project.sb13ootbsteam04.weather.exception.WeatherLocationException;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationNameRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository.ForecastSource;
import com.project.sb13ootbsteam04.weather.service.condition.WeatherLocationCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.IntStream;

@Service
@Slf4j
public class WeatherForecastInitializer {

    private static final String KMA_TIME_ZONE = "Asia/Seoul";
    // 단기예보 발표 시각(02시부터 3시간 간격)
    private static final int FIRST_ISSUE_HOUR = 2;
    private static final int ISSUE_INTERVAL_HOURS = 3;
    // 발표 후 API 제공까지 걸리는 시간
    private static final int PUBLISH_DELAY_MINUTES = 10;

    private final WeatherGridConverter gridConverter;
    private final WeatherRepository weathers;
    private final WeatherLocationRepository locations;
    private final WeatherLocationNameRepository locationNames;
    private final KmaForecastClient forecastClient;
    private final KakaoLocationClient locationClient;
    private final WeatherForecastService forecastService;
    private final Clock clock;

    @Autowired
    public WeatherForecastInitializer(
            WeatherGridConverter gridConverter,
            WeatherRepository weathers,
            WeatherLocationRepository locations,
            WeatherLocationNameRepository locationNames,
            KmaForecastClient forecastClient,
            KakaoLocationClient locationClient,
            WeatherForecastService forecastService) {
        this(gridConverter, weathers, locations, locationNames, forecastClient, locationClient,
                forecastService, Clock.system(ZoneId.of(KMA_TIME_ZONE)));
    }

    WeatherForecastInitializer(
            WeatherGridConverter gridConverter,
            WeatherRepository weathers,
            WeatherLocationRepository locations,
            WeatherLocationNameRepository locationNames,
            KmaForecastClient forecastClient,
            KakaoLocationClient locationClient,
            WeatherForecastService forecastService,
            Clock clock) {
        this.gridConverter = gridConverter;
        this.weathers = weathers;
        this.locations = locations;
        this.locationNames = locationNames;
        this.forecastClient = forecastClient;
        this.locationClient = locationClient;
        this.forecastService = forecastService;
        this.clock = clock;
    }

    // 조회 좌표의 최신 예보가 없으면 위치를 등록하고 기상청 예보를 한 번 수집한다.
    // 외부 API 호출이 DB 트랜잭션을 붙잡지 않도록 이 메서드에는 트랜잭션을 걸지 않는다.
    public void ensureForecast(WeatherLocationCondition condition) {
        var grid = gridConverter.convert(condition.latitude(), condition.longitude());
        var issue = latestIssueTime();
        var latest = weathers.findForecastSourcesByGrid(grid.x(), grid.y(), PageRequest.of(0, 1)).stream()
                .findFirst();

        if (latest.map(source -> !source.getForecastedAt().isBefore(issue.toInstant())).orElse(false)) {
            return;
        }

        var location = latest.map(ForecastSource::getLocation)
                .orElseGet(() -> registerLocation(condition, grid));

        try {
            var items = forecastClient.getVillageForecast(
                    issue.toLocalDate(), issue.toLocalTime(), grid.x(), grid.y());
            forecastService.saveForecast(location.getId(), items);
        } catch (WeatherForecastException exception) {
            // 예보 수집 실패는 기존 데이터 조회를 막지 않는다.
            log.warn("날씨 조회 전 예보 수집 실패: locationId={}, errorCode={}",
                    location.getId(), exception.getErrorCode().getCode());
        }
    }

    ZonedDateTime latestIssueTime() {
        var available = ZonedDateTime.now(clock).minusMinutes(PUBLISH_DELAY_MINUTES);
        int hour = available.getHour();
        if (hour < FIRST_ISSUE_HOUR) {
            return available.minusDays(1).withHour(23).truncatedTo(ChronoUnit.HOURS);
        }
        int issueHour = hour - (hour - FIRST_ISSUE_HOUR) % ISSUE_INTERVAL_HOURS;
        return available.withHour(issueHour).truncatedTo(ChronoUnit.HOURS);
    }

    private WeatherLocation registerLocation(WeatherLocationCondition condition, WeatherGridConverter.Grid grid) {
        var location = locations.save(new WeatherLocation(
                condition.latitude(), condition.longitude(), grid.x(), grid.y()));
        try {
            var names = locationClient.getLocationNames(condition.latitude(), condition.longitude());
            locationNames.saveAll(IntStream.range(0, names.size())
                    .mapToObj(order -> new WeatherLocationName(location.getId(), order, names.get(order)))
                    .toList());
        } catch (WeatherLocationException exception) {
            // 행정구역 이름이 없어도 날씨 조회는 가능하다.
            log.warn("날씨 위치 행정구역 조회 실패: locationId={}, errorCode={}",
                    location.getId(), exception.getErrorCode().getCode());
        }
        return location;
    }
}
