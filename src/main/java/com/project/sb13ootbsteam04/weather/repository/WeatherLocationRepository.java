package com.project.sb13ootbsteam04.weather.repository;

import com.project.sb13ootbsteam04.weather.entity.WeatherLocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WeatherLocationRepository extends JpaRepository<WeatherLocation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select location from WeatherLocation location where location.id = :id")
    Optional<WeatherLocation> findByIdForUpdate(@Param("id") UUID id);
}
