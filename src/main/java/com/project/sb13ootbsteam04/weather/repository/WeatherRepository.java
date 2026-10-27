package com.project.sb13ootbsteam04.weather.repository;

import com.project.sb13ootbsteam04.weather.entity.Weather;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.time.Instant;

public interface WeatherRepository extends JpaRepository<Weather, UUID> {
    List<Weather> findAllByLocationIdAndForecastedAtOrderByForecastAtAsc(
            UUID locationId, Instant forecastedAt);
}
