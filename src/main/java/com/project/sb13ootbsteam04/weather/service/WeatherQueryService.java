package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.weather.dto.WeatherDto;
import com.project.sb13ootbsteam04.weather.dto.WeatherLocationResponse;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocationName;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationNameRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository.ForecastSource;
import com.project.sb13ootbsteam04.weather.service.condition.WeatherLocationCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WeatherQueryService {

    private final WeatherGridConverter gridConverter;
    private final WeatherRepository weathers;
    private final WeatherLocationNameRepository locationNames;

    @Transactional(readOnly = true)
    public List<WeatherDto> getWeather(WeatherLocationCondition condition) {
        var grid = gridConverter.convert(condition.latitude(), condition.longitude());
        return weathers.findForecastSourcesByGrid(grid.x(), grid.y(), PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(this::readIssue)
                .orElseGet(List::of);
    }

    private List<WeatherDto> readIssue(ForecastSource source) {
        var location = source.getLocation();
        var names = locationNames.findAllByLocationIdOrderByDisplayOrderAsc(location.getId()).stream()
                .map(WeatherLocationName::getName)
                .toList();
        var responseLocation = new WeatherLocationResponse(location.getLatitude(), location.getLongitude(),
                location.getGridX(), location.getGridY(), names);
        return weathers.findAllByLocationIdAndForecastedAtOrderByForecastAtAsc(location.getId(), source.getForecastedAt())
                .stream().map(weather -> WeatherDto.from(weather, responseLocation)).toList();
    }
}
