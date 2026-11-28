package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.weather.dto.WeatherDto;
import com.project.sb13ootbsteam04.weather.dto.WeatherLocationResponse;
import com.project.sb13ootbsteam04.weather.entity.Weather;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocationName;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationNameRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository.ForecastSource;
import com.project.sb13ootbsteam04.weather.service.condition.WeatherLocationCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeatherQueryService {

    private static final ZoneId KMA_ZONE = ZoneId.of("Asia/Seoul");
    private static final int REPRESENTATIVE_HOUR = 12;

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
        var forecasts = weathers.findAllByLocationIdAndForecastedAtOrderByForecastAtAsc(
                location.getId(), source.getForecastedAt());

        // 화면은 배열 한 칸을 하루로 그리므로 날짜별 대표 예보 하나만 응답한다.
        var forecastsByDate = forecasts.stream().collect(Collectors.groupingBy(
                weather -> weather.getForecastAt().atZone(KMA_ZONE).toLocalDate(),
                LinkedHashMap::new, Collectors.toList()));
        var result = new ArrayList<WeatherDto>();
        for (var daily : forecastsByDate.values()) {
            // 예보 범위 끝처럼 오전 자료만 있는 날은 하루 칸으로 쓰지 않는다
            if (!result.isEmpty() && !reachesNoon(daily)) {
                continue;
            }
            // 첫날은 가장 가까운 예보, 이후 날짜는 정오에 가장 가까운 예보
            var representative = result.isEmpty() ? daily.get(0) : closestToNoon(daily);
            result.add(WeatherDto.from(representative, responseLocation)
                    .withTemperatureRange(dailyMin(daily), dailyMax(daily)));
        }
        return result;
    }

    private boolean reachesNoon(List<Weather> daily) {
        return daily.stream().anyMatch(weather ->
                weather.getForecastAt().atZone(KMA_ZONE).getHour() >= REPRESENTATIVE_HOUR);
    }

    private Weather closestToNoon(List<Weather> daily) {
        return daily.stream()
                .min(Comparator.comparingInt(weather -> Math.abs(
                        weather.getForecastAt().atZone(KMA_ZONE).getHour() - REPRESENTATIVE_HOUR)))
                .orElseThrow();
    }

    // 최저 기온(TMN) 예보가 없는 날은 시간별 기온 중 최저값
    private Double dailyMin(List<Weather> daily) {
        return daily.stream().map(Weather::getTemperatureMin).filter(Objects::nonNull).findFirst()
                .orElseGet(() -> daily.stream().mapToDouble(Weather::getTemperatureCurrent).min().orElseThrow());
    }

    // 최고 기온(TMX) 예보가 없는 날은 시간별 기온 중 최고값
    private Double dailyMax(List<Weather> daily) {
        return daily.stream().map(Weather::getTemperatureMax).filter(Objects::nonNull).findFirst()
                .orElseGet(() -> daily.stream().mapToDouble(Weather::getTemperatureCurrent).max().orElseThrow());
    }
}
