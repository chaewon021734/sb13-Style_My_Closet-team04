package com.project.sb13ootbsteam04.weather.controller;

import com.project.sb13ootbsteam04.weather.dto.WeatherLocationResponse;
import com.project.sb13ootbsteam04.weather.service.WeatherLocationService;
import com.project.sb13ootbsteam04.weather.service.condition.WeatherLocationCondition;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weathers")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherLocationService weatherLocationService;

    @GetMapping("/location")
    public ResponseEntity<WeatherLocationResponse> getWeatherLocation(
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude
    ) {
        var condition = new WeatherLocationCondition(latitude, longitude);
        return ResponseEntity.ok(weatherLocationService.getLocation(condition));
    }
}
