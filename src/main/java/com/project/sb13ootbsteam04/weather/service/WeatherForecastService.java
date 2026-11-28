package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.weather.dto.KmaForecastItem;
import com.project.sb13ootbsteam04.weather.entity.Weather;
import com.project.sb13ootbsteam04.weather.exception.WeatherForecastException;
import com.project.sb13ootbsteam04.weather.event.WeatherChangeDetectedEvent;
import com.project.sb13ootbsteam04.weather.event.WeatherChangeEventPublisher;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.function.Function;

import static com.project.sb13ootbsteam04.global.exception.ErrorCode.WEATHER_FORECAST_LOCATION_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class WeatherForecastService {

    private final WeatherLocationRepository locations;
    private final WeatherRepository weathers;
    private final KmaForecastMapper mapper;
    private final WeatherChangeDetector changeDetector;
    private final WeatherChangeEventPublisher changeEventPublisher;

    @Transactional
    public int saveForecast(UUID locationId, List<KmaForecastItem> items) {
        // 같은 위치에 대한 동시 수집은 기존 예보 조회부터 저장까지 직렬화한다.
        var location = locations.findByIdForUpdate(locationId).orElseThrow(() ->
                new WeatherForecastException(WEATHER_FORECAST_LOCATION_NOT_FOUND, "위치 ID: " + locationId));
        var forecasts = mapper.toForecasts(location, items);
        var issue = forecasts.get(0).getForecastedAt();
        var existingTimes = weathers.findAllByLocationIdAndForecastedAtOrderByForecastAtAsc(locationId, issue)
                .stream().map(Weather::getForecastAt).collect(Collectors.toSet());
        var newForecasts = forecasts.stream().filter(forecast -> !existingTimes.contains(forecast.getForecastAt()))
                .toList();
        if (newForecasts.isEmpty()) {
            return 0;
        }

        var previousByTime = findLatestPreviousForecastsByTime(locationId, issue, newForecasts);
        compareWithDayBefore(locationId, issue, forecasts, newForecasts);
        var savedForecasts = weathers.saveAll(newForecasts);
        publishDetectedChanges(previousByTime, savedForecasts);
        return savedForecasts.size();
    }

    // 하루 전 같은 시각 예보: 이번 발표 안에서 먼저 찾고, 없으면 이전 발표의 가장 최근 예보
    private void compareWithDayBefore(UUID locationId, Instant issue, List<Weather> issueForecasts,
                                      List<Weather> newForecasts) {
        var thisIssueByTime = issueForecasts.stream()
                .collect(Collectors.toMap(Weather::getForecastAt, Function.identity()));
        var missingTimes = newForecasts.stream()
                .map(forecast -> forecast.getForecastAt().minus(Duration.ofDays(1)))
                .filter(time -> !thisIssueByTime.containsKey(time))
                .toList();
        var previousIssueByTime = missingTimes.isEmpty()
                ? Map.<Instant, Weather>of()
                : weathers.findLatestPreviousForecasts(locationId, missingTimes, issue).stream()
                        .collect(Collectors.toMap(Weather::getForecastAt, Function.identity()));
        newForecasts.forEach(forecast -> {
            var dayBefore = forecast.getForecastAt().minus(Duration.ofDays(1));
            forecast.compareWithDayBefore(thisIssueByTime.getOrDefault(dayBefore, previousIssueByTime.get(dayBefore)));
        });
    }

    private Map<Instant, Weather> findLatestPreviousForecastsByTime(
            UUID locationId,
            Instant issue,
            List<Weather> newForecasts
    ) {
        var forecastTimes = newForecasts.stream().map(Weather::getForecastAt).toList();
        return weathers.findLatestPreviousForecasts(locationId, forecastTimes, issue).stream()
                .collect(Collectors.toMap(Weather::getForecastAt, Function.identity()));
    }

    private void publishDetectedChanges(
            Map<Instant, Weather> previousByTime,
            List<Weather> currentForecasts
    ) {
        currentForecasts.forEach(current ->
                publishChange(previousByTime.get(current.getForecastAt()), current));
    }

    private void publishChange(Weather previous, Weather current) {
        if (previous == null) {
            return;
        }
        var changes = changeDetector.detect(previous, current);
        if (changes.isEmpty()) {
            return;
        }
        changeEventPublisher.publish(new WeatherChangeDetectedEvent(
                current.getLocationId(),
                previous.getId(),
                current.getId(),
                current.getForecastAt(),
                changes));
    }
}
