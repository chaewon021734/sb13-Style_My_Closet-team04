package com.project.sb13ootbsteam04.weather.service.condition;

public record WeatherLocationCondition(double latitude, double longitude) {

    public WeatherLocationCondition {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("위도는 -90~90, 경도는 -180~180 범위의 유한한 값이어야 합니다.");
        }
    }
}
