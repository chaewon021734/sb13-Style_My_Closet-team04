package com.project.sb13ootbsteam04.weather.dto;

import java.util.List;

public record WeatherLocationResponse(
        double latitude,
        double longitude,
        int x,
        int y,
        List<String> locationNames
) {
    public WeatherLocationResponse {
        locationNames = List.copyOf(locationNames);
    }
}
