package com.project.sb13ootbsteam04.weather.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.project.sb13ootbsteam04.weather.entity.PrecipitationType;
import com.project.sb13ootbsteam04.weather.entity.SkyStatus;
import com.project.sb13ootbsteam04.weather.entity.Weather;
import com.project.sb13ootbsteam04.weather.entity.WindStrength;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "WeatherForecastDto")
public record WeatherDto(
        UUID id,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant forecastedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant forecastAt,
        WeatherLocationResponse location,
        SkyStatus skyStatus,
        PrecipitationDto precipitation,
        HumidityDto humidity,
        TemperatureDto temperature,
        WindSpeedDto windSpeed
) {
    public static WeatherDto from(Weather weather, WeatherLocationResponse location) {
        return new WeatherDto(weather.getId(), weather.getForecastedAt(), weather.getForecastAt(), location,
                weather.getSkyStatus(),
                new PrecipitationDto(weather.getPrecipitationType(), weather.getPrecipitationAmount(),
                        weather.getPrecipitationProbability()),
                new HumidityDto(weather.getHumidityCurrent(), weather.getHumidityComparedToDayBefore()),
                new TemperatureDto(weather.getTemperatureCurrent(), weather.getTemperatureComparedToDayBefore(),
                        weather.getTemperatureMin(), weather.getTemperatureMax()),
                new WindSpeedDto(weather.getWindSpeed(), weather.getWindSpeedAsWord()));
    }

    // 하루 대표 예보에 그날의 최저·최고 기온을 채운다.
    public WeatherDto withTemperatureRange(Double min, Double max) {
        return new WeatherDto(id, forecastedAt, forecastAt, location, skyStatus, precipitation, humidity,
                new TemperatureDto(temperature.current(), temperature.comparedToDayBefore(), min, max),
                windSpeed);
    }

    @Schema(name = "WeatherForecastPrecipitationDto")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PrecipitationDto(PrecipitationType type, Double amount, Double probability) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HumidityDto(double current, Double comparedToDayBefore) {
    }

    @Schema(name = "WeatherForecastTemperatureDto")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TemperatureDto(double current, Double comparedToDayBefore, Double min, Double max) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WindSpeedDto(Double speed, WindStrength asWord) {
    }
}
