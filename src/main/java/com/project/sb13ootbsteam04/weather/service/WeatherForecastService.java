package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.weather.dto.KmaForecastItem;
import com.project.sb13ootbsteam04.weather.entity.Weather;
import com.project.sb13ootbsteam04.weather.exception.WeatherForecastException;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.project.sb13ootbsteam04.global.exception.ErrorCode.WEATHER_FORECAST_LOCATION_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class WeatherForecastService {

    private final WeatherLocationRepository locations;
    private final WeatherRepository weathers;
    private final KmaForecastMapper mapper;

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
        return weathers.saveAll(newForecasts).size();
    }
}
