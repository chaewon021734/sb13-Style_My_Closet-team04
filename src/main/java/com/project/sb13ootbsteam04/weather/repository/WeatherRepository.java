package com.project.sb13ootbsteam04.weather.repository;

import com.project.sb13ootbsteam04.weather.entity.Weather;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface WeatherRepository extends JpaRepository<Weather, UUID> {
    List<Weather> findAllByLocationIdAndForecastedAtOrderByForecastAtAsc(
            UUID locationId, Instant forecastedAt);

    @Query("""
            select forecast
            from Weather forecast
            where forecast.locationId = :locationId
                and forecast.forecastAt in :forecastTimes
                and forecast.forecastedAt = (
                    select max(previous.forecastedAt)
                    from Weather previous
                    where previous.locationId = :locationId
                        and previous.forecastAt = forecast.forecastAt
                        and previous.forecastedAt < :currentIssue
                )
            """)
    List<Weather> findLatestPreviousForecasts(
            @Param("locationId") UUID locationId,
            @Param("forecastTimes") Collection<Instant> forecastTimes,
            @Param("currentIssue") Instant currentIssue);

    @Query("""
            select location as location, forecast.forecastedAt as forecastedAt
            from Weather forecast, WeatherLocation location
            where forecast.locationId = location.id
                and location.gridX = :gridX and location.gridY = :gridY
            order by forecast.forecastedAt desc, location.id asc
            """)
    List<ForecastSource> findForecastSourcesByGrid(
            @Param("gridX") int gridX, @Param("gridY") int gridY, Pageable pageable);

    interface ForecastSource {
        WeatherLocation getLocation();

        Instant getForecastedAt();
    }
}
