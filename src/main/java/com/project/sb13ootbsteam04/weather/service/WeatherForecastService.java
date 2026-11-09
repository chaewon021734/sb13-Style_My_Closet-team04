package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.weather.dto.KmaForecastItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class WeatherForecastService {

    public int saveForecast(UUID locationId, List<KmaForecastItem> items) {
        throw new UnsupportedOperationException("단기예보 저장 구현 전");
    }
}
