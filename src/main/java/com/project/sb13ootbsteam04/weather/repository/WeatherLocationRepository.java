package com.project.sb13ootbsteam04.weather.repository;

import com.project.sb13ootbsteam04.weather.entity.WeatherLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WeatherLocationRepository extends JpaRepository<WeatherLocation, UUID> {
}
