package com.project.sb13ootbsteam04.integration;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import com.project.sb13ootbsteam04.weather.entity.*;
import com.project.sb13ootbsteam04.weather.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "OTBOO_POSTGRES_TEST", matches = "true")
class PostgresRepositoryIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private WeatherRepository weatherRepository;
    @Autowired private WeatherLocationRepository locationRepository;
    @Autowired private WeatherLocationNameRepository nameRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void savesAndReloadsUserAndWeatherInPostgres() {
        String email = "db-test-" + UUID.randomUUID() + "@example.com";
        var user = userRepository.saveAndFlush(new User("DB 테스트", email, "test-only-encoded-password"));
        var location = locationRepository.saveAndFlush(new WeatherLocation(37.5665, 126.978, 60, 127));
        nameRepository.save(new WeatherLocationName(location.getId(), 0, "서울특별시"));
        Instant issued = Instant.parse("2026-10-27T02:00:00Z");
        var weather = weatherRepository.saveAndFlush(Weather.builder()
                .locationId(location.getId()).forecastedAt(issued).forecastAt(issued.plusSeconds(3600))
                .forecastStepMinutes(60).skyStatus(SkyStatus.CLEAR)
                .precipitationType(PrecipitationType.NONE).precipitationAmountRaw("강수없음")
                .humidityCurrent(55.0).temperatureCurrent(18.5)
                .windSpeedRaw("1.5").windSpeed(1.5).windSpeedAsWord(WindStrength.WEAK).build());
        entityManager.clear();

        assertThat(userRepository.findByEmail(email)).get()
                .extracting(User::getId).isEqualTo(user.getId());
        assertThat(locationRepository.findById(location.getId())).get()
                .extracting(WeatherLocation::getGridX).isEqualTo(60);
        assertThat(nameRepository.findAllByLocationIdOrderByDisplayOrderAsc(location.getId()))
                .extracting(WeatherLocationName::getName).containsExactly("서울특별시");
        assertThat(weatherRepository.findAllByLocationIdAndForecastedAtOrderByForecastAtAsc(location.getId(), issued))
                .extracting(Weather::getId).containsExactly(weather.getId());
        assertThat(weatherRepository.findById(weather.getId())).get()
                .extracting(Weather::getPrecipitationAmount).isNull();
    }
}
