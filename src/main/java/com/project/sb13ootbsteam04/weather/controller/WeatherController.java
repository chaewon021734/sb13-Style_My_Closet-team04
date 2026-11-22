package com.project.sb13ootbsteam04.weather.controller;

import com.project.sb13ootbsteam04.global.exception.ApiErrorResponse;
import com.project.sb13ootbsteam04.weather.dto.WeatherDto;
import com.project.sb13ootbsteam04.weather.dto.WeatherLocationResponse;
import com.project.sb13ootbsteam04.weather.service.WeatherLocationService;
import com.project.sb13ootbsteam04.weather.service.WeatherQueryService;
import com.project.sb13ootbsteam04.weather.service.condition.WeatherLocationCondition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/weathers")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherLocationService weatherLocationService;
    private final WeatherQueryService weatherQueryService;

    @Operation(summary = "날씨 정보 조회", responses = {
            @ApiResponse(responseCode = "200", description = "날씨 조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = WeatherDto.class)))),
            @ApiResponse(responseCode = "400", description = "날씨 조회 실패",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<WeatherDto>> getWeather(
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude
    ) {
        var condition = new WeatherLocationCondition(latitude, longitude);
        return ResponseEntity.ok(weatherQueryService.getWeather(condition));
    }

    @GetMapping("/location")
    public ResponseEntity<WeatherLocationResponse> getWeatherLocation(
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude
    ) {
        var condition = new WeatherLocationCondition(latitude, longitude);
        return ResponseEntity.ok(weatherLocationService.getLocation(condition));
    }
}
