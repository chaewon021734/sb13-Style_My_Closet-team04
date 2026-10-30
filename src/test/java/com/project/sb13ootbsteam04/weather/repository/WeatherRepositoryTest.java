package com.project.sb13ootbsteam04.weather.repository;

import com.project.sb13ootbsteam04.weather.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(WeatherRepositoryTest.Config.class)
@Transactional
class WeatherRepositoryTest {

    @Autowired private WeatherRepository weathers;
    @Autowired private WeatherLocationRepository locations;
    @Autowired private WeatherLocationNameRepository names;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsForecastsAndFindsThemInTimeOrderWithinLocationAndIssueTime() {
        var location = locations.saveAndFlush(new WeatherLocation(37.5665, 126.978, 60, 127));
        var otherLocation = locations.saveAndFlush(new WeatherLocation(33.4996, 126.5312, 53, 38));
        Instant issued = Instant.parse("2026-10-27T02:00:00Z");
        Instant first = issued.plusSeconds(3600);
        Instant second = issued.plusSeconds(7200);
        weathers.save(forecast(location.getId(), issued, second));
        var saved = weathers.save(forecast(location.getId(), issued, first));
        weathers.save(forecast(otherLocation.getId(), issued, first));
        weathers.save(forecast(location.getId(), issued.minusSeconds(10800), first));
        entityManager.flush();
        UUID id = saved.getId();
        entityManager.clear();

        var found = weathers.findAllByLocationIdAndForecastedAtOrderByForecastAtAsc(location.getId(), issued);
        assertThat(found).extracting(Weather::getForecastAt).containsExactly(first, second);
        var loaded = weathers.findById(id).orElseThrow();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
        assertThat(loaded.getPrecipitationAmountRaw()).isEqualTo("강수없음");
        assertThat(loaded.getPrecipitationAmount()).isNull();
        assertThat(loaded.getTemperatureMin()).isNull();
        assertThat(loaded.getWindSpeedRaw()).isEqualTo("1.5");
        assertThat(loaded.getWindSpeedAsWord()).isEqualTo(WindStrength.WEAK);
    }

    @Test
    void persistsCoordinatesAndKeepsAdministrativeNamesInDisplayOrder() {
        var location = locations.saveAndFlush(new WeatherLocation(37.5665, 126.978, 60, 127));
        names.save(new WeatherLocationName(location.getId(), 2, "명동"));
        names.save(new WeatherLocationName(location.getId(), 0, "서울특별시"));
        names.save(new WeatherLocationName(location.getId(), 1, "중구"));
        var otherLocation = locations.saveAndFlush(new WeatherLocation(33.4996, 126.5312, 53, 38));
        names.save(new WeatherLocationName(otherLocation.getId(), 0, "제주특별자치도"));
        entityManager.flush();
        entityManager.clear();

        var loaded = locations.findById(location.getId()).orElseThrow();
        assertThat(loaded.getLatitude()).isEqualTo(37.5665);
        assertThat(loaded.getLongitude()).isEqualTo(126.978);
        assertThat(loaded.getGridX()).isEqualTo(60);
        assertThat(loaded.getGridY()).isEqualTo(127);
        assertThat(names.findAllByLocationIdOrderByDisplayOrderAsc(location.getId()))
                .extracting(WeatherLocationName::getName).containsExactly("서울특별시", "중구", "명동");
    }

    @Test
    void rejectsInvalidLocationAndAdministrativeName() {
        assertThatThrownBy(() -> new WeatherLocation(Double.NaN, 127, 60, 127))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WeatherLocationName(UUID.randomUUID(), 0, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WeatherLocationName(UUID.randomUUID(), -1, "서울특별시"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Weather forecast(UUID locationId, Instant issued, Instant target) {
        return Weather.builder().locationId(locationId).forecastedAt(issued).forecastAt(target)
                .forecastStepMinutes(60).skyStatus(SkyStatus.CLEAR)
                .precipitationType(PrecipitationType.NONE).precipitationAmountRaw("강수없음")
                .humidityCurrent(55.0).temperatureCurrent(18.5)
                .windSpeedRaw("1.5").windSpeed(1.5).windSpeedAsWord(WindStrength.WEAK).build();
    }

    @TestConfiguration
    @EnableJpaRepositories(basePackageClasses = WeatherRepository.class)
    @EnableJpaAuditing
    @EnableTransactionManagement
    static class Config {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:weather;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.project.sb13ootbsteam04.weather.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
