package com.project.sb13ootbsteam04.weather.repository;

import com.project.sb13ootbsteam04.weather.entity.WeatherLocationName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface WeatherLocationNameRepository extends JpaRepository<WeatherLocationName, UUID> {
    List<WeatherLocationName> findAllByLocationIdOrderByDisplayOrderAsc(UUID locationId);
}
