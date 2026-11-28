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

import java.util.List;
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

        var forecastTimes = newForecasts.stream().map(Weather::getForecastAt).toList();
        var previousByTime = weathers.findLatestPreviousForecasts(locationId, forecastTimes, issue).stream()
                .collect(Collectors.toMap(Weather::getForecastAt, Function.identity()));
        var savedForecasts = weathers.saveAll(newForecasts);
        savedForecasts.forEach(current -> publishChange(previousByTime.get(current.getForecastAt()), current));
        return savedForecasts.size();
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
