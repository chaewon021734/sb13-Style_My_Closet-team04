package com.project.sb13ootbsteam04.weather.dto;

public record KmaForecastItem(
        String baseDate,
        String baseTime,
        String fcstDate,
        String fcstTime,
        String category,
        String fcstValue,
        Integer nx,
        Integer ny
) {
}
