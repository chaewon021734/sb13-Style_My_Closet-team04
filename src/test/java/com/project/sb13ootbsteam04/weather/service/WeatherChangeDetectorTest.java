package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.weather.entity.PrecipitationType;
import com.project.sb13ootbsteam04.weather.entity.SkyStatus;
import com.project.sb13ootbsteam04.weather.entity.Weather;
import com.project.sb13ootbsteam04.weather.entity.WindStrength;
import com.project.sb13ootbsteam04.weather.event.WeatherChangeType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherChangeDetectorTest {

    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final Instant TARGET = Instant.parse("2026-11-28T09:00:00Z");

    private final WeatherChangeDetector detector = new WeatherChangeDetector();

    @Test
    void detectsPrecipitationStrongWindAndSignificantTemperatureChanges() {
        var previous = forecast(PrecipitationType.NONE, WindStrength.WEAK, 10.0);
        var current = forecast(PrecipitationType.RAIN, WindStrength.STRONG, 15.0);

        assertThat(detector.detect(previous, current)).containsExactlyInAnyOrder(
                WeatherChangeType.PRECIPITATION_STARTED,
                WeatherChangeType.STRONG_WIND_STARTED,
                WeatherChangeType.TEMPERATURE_CHANGED_SIGNIFICANTLY);
    }

    @Test
    void ignoresOrdinaryForecastAdjustments() {
        var previous = forecast(PrecipitationType.RAIN, WindStrength.MODERATE, 10.0);
        var current = forecast(PrecipitationType.SNOW, WindStrength.MODERATE, 14.9);

        assertThat(detector.detect(previous, current)).isEmpty();
    }

    @Test
    void reportsOnlyTransitionsIntoAlertConditions() {
        var previous = forecast(PrecipitationType.RAIN, WindStrength.STRONG, 10.0);
        var current = forecast(PrecipitationType.SHOWER, WindStrength.STRONG, 15.0);

        assertThat(detector.detect(previous, current))
                .containsExactly(WeatherChangeType.TEMPERATURE_CHANGED_SIGNIFICANTLY);
    }

    private Weather forecast(
            PrecipitationType precipitationType,
            WindStrength windStrength,
            double temperature
    ) {
        return Weather.builder()
                .locationId(LOCATION_ID)
                .forecastedAt(Instant.parse("2026-11-28T05:00:00Z"))
                .forecastAt(TARGET)
                .forecastStepMinutes(60)
                .skyStatus(SkyStatus.CLEAR)
                .precipitationType(precipitationType)
                .precipitationAmountRaw(precipitationType == PrecipitationType.NONE ? "강수없음" : "1.0mm")
                .humidityCurrent(50.0)
                .temperatureCurrent(temperature)
                .windSpeedRaw(windStrength == WindStrength.STRONG ? "10.0" : "4.0")
                .windSpeed(windStrength == WindStrength.STRONG ? 10.0 : 4.0)
                .windSpeedAsWord(windStrength)
                .build();
    }
}
