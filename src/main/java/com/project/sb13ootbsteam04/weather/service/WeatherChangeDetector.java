package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.weather.entity.PrecipitationType;
import com.project.sb13ootbsteam04.weather.entity.Weather;
import com.project.sb13ootbsteam04.weather.entity.WindStrength;
import com.project.sb13ootbsteam04.weather.event.WeatherChangeType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

@Component
public class WeatherChangeDetector {

    static final double SIGNIFICANT_TEMPERATURE_CHANGE_CELSIUS = 5.0;

    public Set<WeatherChangeType> detect(Weather previous, Weather current) {
        Objects.requireNonNull(previous, "이전 날씨는 필수입니다.");
        Objects.requireNonNull(current, "현재 날씨는 필수입니다.");

        var changes = EnumSet.noneOf(WeatherChangeType.class);
        if (isPrecipitationStarting(previous, current)) {
            changes.add(WeatherChangeType.PRECIPITATION_STARTED);
        }
        if (isStrongWindStarting(previous, current)) {
            changes.add(WeatherChangeType.STRONG_WIND_STARTED);
        }
        if (isTemperatureChangeSignificant(previous, current)) {
            changes.add(WeatherChangeType.TEMPERATURE_CHANGED_SIGNIFICANTLY);
        }
        return Set.copyOf(changes);
    }

    private boolean isPrecipitationStarting(Weather previous, Weather current) {
        return previous.getPrecipitationType() == PrecipitationType.NONE
                && current.getPrecipitationType() != PrecipitationType.NONE;
    }

    private boolean isStrongWindStarting(Weather previous, Weather current) {
        return previous.getWindSpeedAsWord() != WindStrength.STRONG
                && current.getWindSpeedAsWord() == WindStrength.STRONG;
    }

    private boolean isTemperatureChangeSignificant(Weather previous, Weather current) {
        return Math.abs(current.getTemperatureCurrent() - previous.getTemperatureCurrent())
                >= SIGNIFICANT_TEMPERATURE_CHANGE_CELSIUS;
    }
}
