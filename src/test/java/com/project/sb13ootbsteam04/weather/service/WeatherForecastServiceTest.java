package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.weather.dto.KmaForecastItem;
import com.project.sb13ootbsteam04.weather.entity.PrecipitationType;
import com.project.sb13ootbsteam04.weather.entity.SkyStatus;
import com.project.sb13ootbsteam04.weather.entity.Weather;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocation;
import com.project.sb13ootbsteam04.weather.entity.WindStrength;
import com.project.sb13ootbsteam04.weather.exception.WeatherForecastException;
import com.project.sb13ootbsteam04.weather.event.WeatherChangeDetectedEvent;
import com.project.sb13ootbsteam04.weather.event.WeatherChangeEventPublisher;
import com.project.sb13ootbsteam04.weather.event.WeatherChangeType;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, WeatherForecastService.class, KmaForecastMapper.class,
        WeatherChangeDetector.class})
class WeatherForecastServiceTest {

    private static final Instant ISSUE = Instant.parse("2026-11-09T02:00:00Z");
    private static final Map<String, String> DRY = Map.of(
            "SKY", "3", "PTY", "0", "PCP", "강수없음", "POP", "20", "REH", "60", "TMP", "12", "WSD", "1.2");

    @Autowired private WeatherForecastService service;
    @Autowired private WeatherLocationRepository locations;
    @Autowired private WeatherRepository weathers;
    @Autowired private EntityManager entityManager;
    @MockitoBean private WeatherChangeEventPublisher eventPublisher;

    private UUID locationId;

    @BeforeEach
    void registerLocation() {
        locationId = locations.saveAndFlush(new WeatherLocation(37.5665, 126.978, 60, 127)).getId();
    }

    @Test
    void groupsUnorderedItemsByForecastTimeAndPersistsConvertedValues() {
        var items = new ArrayList<>(slot("1100", "1500", Map.of(
                "SKY", "4", "PTY", "1", "PCP", "2.0mm", "POP", "80", "REH", "75",
                "TMP", "10", "WSD", "4.5", "TMX", "13.0")));
        items.addAll(slot("1100", "1400", DRY));

        assertThat(service.saveForecast(locationId, items)).isEqualTo(2);

        var saved = forecasts(ISSUE);
        assertThat(saved).extracting(Weather::getForecastAt)
                .containsExactly(Instant.parse("2026-11-09T05:00:00Z"), Instant.parse("2026-11-09T06:00:00Z"));
        var dry = saved.get(0);
        assertThat(dry.getForecastStepMinutes()).isEqualTo(60);
        assertThat(dry.getSkyStatus()).isEqualTo(SkyStatus.MOSTLY_CLOUDY);
        assertThat(dry.getPrecipitationType()).isEqualTo(PrecipitationType.NONE);
        assertThat(dry.getPrecipitationAmountRaw()).isEqualTo("강수없음");
        assertThat(dry.getPrecipitationAmount()).isZero();
        assertThat(dry.getPrecipitationProbability()).isEqualTo(20);
        assertThat(dry.getHumidityCurrent()).isEqualTo(60);
        assertThat(dry.getTemperatureCurrent()).isEqualTo(12);
        assertThat(dry.getTemperatureMin()).isNull();
        assertThat(dry.getTemperatureMax()).isNull();
        assertThat(dry.getWindSpeed()).isEqualTo(1.2);
        assertThat(dry.getWindSpeedAsWord()).isEqualTo(WindStrength.WEAK);

        var rain = saved.get(1);
        assertThat(rain.getSkyStatus()).isEqualTo(SkyStatus.CLOUDY);
        assertThat(rain.getPrecipitationType()).isEqualTo(PrecipitationType.RAIN);
        assertThat(rain.getPrecipitationAmount()).isEqualTo(2.0);
        assertThat(rain.getTemperatureMax()).isEqualTo(13);
        assertThat(rain.getWindSpeedAsWord()).isEqualTo(WindStrength.MODERATE);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("같은 발표 안에 하루 전 같은 시각 예보가 있으면 기온·습도 차이를 저장한다")
    void storesDifferenceFromSameTimeOnPreviousDayInSameIssue() {
        var items = new ArrayList<>(slot("1100", "20261109", "1400", values("5", "40")));
        items.addAll(slot("1100", "20261110", "1400", values("13", "75")));

        service.saveForecast(locationId, items);

        var tomorrow = forecasts(ISSUE).get(1);
        assertThat(tomorrow.getTemperatureComparedToDayBefore()).isEqualTo(8);
        assertThat(tomorrow.getHumidityComparedToDayBefore()).isEqualTo(35);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("새 발표에 하루 전 시각이 없으면 이전 발표의 그 시각 예보와 비교한다")
    void comparesWithPreviousIssueWhenDayBeforeIsNotInThisIssue() {
        service.saveForecast(locationId, slot("1100", "20261109", "2300", values("4", "60")));

        service.saveForecast(locationId, slot("1400", "20261110", "2300", values("2", "50")));

        var today = forecasts(ISSUE.plusSeconds(10800)).get(0);
        assertThat(today.getTemperatureComparedToDayBefore()).isEqualTo(-2);
        assertThat(today.getHumidityComparedToDayBefore()).isEqualTo(-10);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("하루 전 예보가 어디에도 없으면 전날 대비 값은 비워 둔다")
    void leavesComparisonEmptyWithoutPreviousDayForecast() {
        service.saveForecast(locationId, slot("1100", "20261109", "1400", values("5", "40")));

        var saved = forecasts(ISSUE).get(0);
        assertThat(saved.getTemperatureComparedToDayBefore()).isNull();
        assertThat(saved.getHumidityComparedToDayBefore()).isNull();
    }

    @Test
    void rejectsAnIncompleteForecastWithoutWritingTheCompleteSlot() {
        var items = new ArrayList<>(slot("1100", "1400", DRY));
        slot("1100", "1500", DRY).stream().filter(item -> !item.category().equals("TMP")).forEach(items::add);

        assertThatThrownBy(() -> service.saveForecast(locationId, items))
                .isInstanceOfSatisfying(WeatherForecastException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.WEATHER_FORECAST_RESPONSE_INVALID);
                    assertThat(error.getDetails()).hasEntrySatisfying("reason", reason -> assertThat(reason).contains("TMP"));
                });
        assertThat(weathers.findAllByLocationIdAndForecastedAtOrderByForecastAtAsc(locationId, ISSUE)).isEmpty();
    }

    @Test
    void preservesTheExistingIdOnRetryAndStoresANewIssueForTheSameTime() {
        var items = slot("1100", "1600", DRY);
        assertThat(service.saveForecast(locationId, items)).isEqualTo(1);
        UUID originalId = forecasts(ISSUE).get(0).getId();

        assertThat(service.saveForecast(locationId, items)).isZero();
        assertThat(forecasts(ISSUE)).extracting(Weather::getId).containsExactly(originalId);

        assertThat(service.saveForecast(locationId, slot("1400", "1600", DRY))).isEqualTo(1);
        assertThat(forecasts(ISSUE.plusSeconds(10800))).extracting(Weather::getId)
                .hasSize(1).doesNotContain(originalId);
    }

    @Test
    void keepsPrecipitationRangesAndExtendedForecastCodesWithoutInventingMeasurements() {
        var hourly = slot("1100", "20261112", "0000", Map.of(
                "SKY", "4", "PTY", "1", "PCP", "1.0mm 미만", "REH", "75", "TMP", "8", "WSD", "2"));
        var extended = slot("1100", "20261112", "0300", Map.of(
                "SKY", "4", "PTY", "1", "PCP", "2", "REH", "75", "TMP", "7", "WSD", "2"));
        var items = new ArrayList<>(hourly);
        items.addAll(extended);

        assertThat(service.saveForecast(locationId, items)).isEqualTo(2);

        var saved = forecasts(ISSUE);
        var atMidnight = saved.get(0);
        assertThat(atMidnight.getForecastStepMinutes()).isEqualTo(60);
        assertThat(atMidnight.getPrecipitationAmountRaw()).isEqualTo("1.0mm 미만");
        assertThat(atMidnight.getPrecipitationAmount()).isNull();
        assertThat(atMidnight.getWindSpeed()).isEqualTo(2.0);
        assertThat(atMidnight.getPrecipitationProbability()).isNull();

        var afterMidnight = saved.get(1);
        assertThat(afterMidnight.getForecastStepMinutes()).isEqualTo(180);
        assertThat(afterMidnight.getPrecipitationAmountRaw()).isEqualTo("2");
        assertThat(afterMidnight.getPrecipitationAmount()).isNull();
        assertThat(afterMidnight.getWindSpeedRaw()).isEqualTo("2");
        assertThat(afterMidnight.getWindSpeed()).isNull();
        assertThat(afterMidnight.getWindSpeedAsWord()).isEqualTo(WindStrength.MODERATE);
    }

    @Test
    void publishesDetectedChangesAgainstTheLatestPreviousIssueForTheSameTarget() {
        assertThat(service.saveForecast(locationId, slot("1100", "1600", DRY))).isEqualTo(1);
        verifyNoInteractions(eventPublisher);
        reset(eventPublisher);

        var changed = Map.of(
                "SKY", "4", "PTY", "1", "PCP", "2.0mm", "POP", "80", "REH", "75",
                "TMP", "17", "WSD", "10.0");

        assertThat(service.saveForecast(locationId, slot("1400", "1600", changed))).isEqualTo(1);

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(WeatherChangeDetectedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().locationId()).isEqualTo(locationId);
        assertThat(eventCaptor.getValue().forecastAt()).isEqualTo(Instant.parse("2026-11-09T07:00:00Z"));
        assertThat(eventCaptor.getValue().previousWeatherId()).isNotNull();
        assertThat(eventCaptor.getValue().currentWeatherId()).isNotNull();
        assertThat(eventCaptor.getValue().changes()).containsExactlyInAnyOrder(
                WeatherChangeType.PRECIPITATION_STARTED,
                WeatherChangeType.STRONG_WIND_STARTED,
                WeatherChangeType.TEMPERATURE_CHANGED_SIGNIFICANTLY);
    }

    private static Map<String, String> values(String temperature, String humidity) {
        return Map.of("SKY", "1", "PTY", "0", "PCP", "강수없음", "POP", "0", "REH", humidity,
                "TMP", temperature, "WSD", "1.0");
    }

    private List<KmaForecastItem> slot(String baseTime, String forecastTime, Map<String, String> values) {
        return slot(baseTime, "20261109", forecastTime, values);
    }

    private List<KmaForecastItem> slot(String baseTime, String forecastDate, String forecastTime,
                                       Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> new KmaForecastItem("20261109", baseTime, forecastDate, forecastTime,
                        entry.getKey(), entry.getValue(), 60, 127))
                .toList();
    }

    private List<Weather> forecasts(Instant issue) {
        entityManager.flush();
        entityManager.clear();
        return weathers.findAllByLocationIdAndForecastedAtOrderByForecastAtAsc(locationId, issue);
    }
}
