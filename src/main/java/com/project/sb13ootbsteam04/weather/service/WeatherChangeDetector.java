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
        if (previous.getPrecipitationType() == PrecipitationType.NONE
                && current.getPrecipitationType() != PrecipitationType.NONE) {
            changes.add(WeatherChangeType.PRECIPITATION_STARTED);
        }
        if (previous.getWindSpeedAsWord() != WindStrength.STRONG
                && current.getWindSpeedAsWord() == WindStrength.STRONG) {
            changes.add(WeatherChangeType.STRONG_WIND_STARTED);
        }
        if (Math.abs(current.getTemperatureCurrent() - previous.getTemperatureCurrent())
                >= SIGNIFICANT_TEMPERATURE_CHANGE_CELSIUS) {
            changes.add(WeatherChangeType.TEMPERATURE_CHANGED_SIGNIFICANTLY);
        }
        return Set.copyOf(changes);
    }
}
