package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.weather.client.KakaoLocationClient;
import com.project.sb13ootbsteam04.weather.dto.WeatherLocationResponse;
import com.project.sb13ootbsteam04.weather.service.condition.WeatherLocationCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeatherLocationService {

    private final KakaoLocationClient kakaoLocationClient;
    private final WeatherGridConverter gridConverter;

    public WeatherLocationResponse getLocation(WeatherLocationCondition condition) {
        var names = kakaoLocationClient.getLocationNames(condition.latitude(), condition.longitude());
        var grid = gridConverter.convert(condition.latitude(), condition.longitude());
        return new WeatherLocationResponse(condition.latitude(), condition.longitude(),
                grid.x(), grid.y(), names);
    }
}
