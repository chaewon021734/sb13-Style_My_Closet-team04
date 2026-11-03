package com.project.sb13ootbsteam04.weather.entity;

import com.project.sb13ootbsteam04.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "weathers")
@AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Weather extends BaseEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "forecasted_at", nullable = false)
    private Instant forecastedAt;

    @Column(name = "forecast_at", nullable = false)
    private Instant forecastAt;

    @Column(name = "forecast_step_minutes", nullable = false)
    private int forecastStepMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "sky_status", nullable = false, length = 20)
    private SkyStatus skyStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "precipitation_type", nullable = false, length = 20)
    private PrecipitationType precipitationType;

    @Column(name = "precipitation_amount_raw", nullable = false, length = 100)
    private String precipitationAmountRaw;

    @Column(name = "precipitation_amount", nullable = true)
    private Double precipitationAmount;

    @Column(name = "precipitation_probability", nullable = true)
    private Double precipitationProbability;

    @Column(name = "humidity_current", nullable = false)
    private Double humidityCurrent;

    @Column(name = "humidity_compared_to_day_before", nullable = true)
    private Double humidityComparedToDayBefore;

    @Column(name = "temperature_current", nullable = false)
    private Double temperatureCurrent;

    @Column(name = "temperature_compared_to_day_before", nullable = true)
    private Double temperatureComparedToDayBefore;

    @Column(name = "temperature_min", nullable = true)
    private Double temperatureMin;

    @Column(name = "temperature_max", nullable = true)
    private Double temperatureMax;

    @Column(name = "wind_speed_raw", nullable = false, length = 100)
    private String windSpeedRaw;

    @Column(name = "wind_speed", nullable = true)
    private Double windSpeed;

    @Enumerated(EnumType.STRING)
    @Column(name = "wind_speed_as_word", nullable = false, length = 10)
    private WindStrength windSpeedAsWord;

    @Builder
    public Weather(
            UUID locationId,
            Instant forecastedAt,
            Instant forecastAt,
            int forecastStepMinutes,
            SkyStatus skyStatus,
            PrecipitationType precipitationType,
            String precipitationAmountRaw,
            Double precipitationAmount,
            Double precipitationProbability,
            Double humidityCurrent,
            Double humidityComparedToDayBefore,
            Double temperatureCurrent,
            Double temperatureComparedToDayBefore,
            Double temperatureMin,
            Double temperatureMax,
            String windSpeedRaw,
            Double windSpeed,
            WindStrength windSpeedAsWord
    ) {
        if (forecastStepMinutes <= 0) {
            throw new IllegalArgumentException("예보 간격은 0보다 커야 합니다.");
        }
        this.locationId = Objects.requireNonNull(locationId, "위치 ID 필드는 필수입니다.");
        this.forecastedAt = Objects.requireNonNull(forecastedAt, "예보 발표 시각 필드는 필수입니다.");
        this.forecastAt = Objects.requireNonNull(forecastAt, "예보 대상 시각 필드는 필수입니다.");
        this.forecastStepMinutes = forecastStepMinutes;
        this.skyStatus = Objects.requireNonNull(skyStatus, "하늘 상태 필드는 필수입니다.");
        this.precipitationType = Objects.requireNonNull(precipitationType, "강수 형태 필드는 필수입니다.");
        this.precipitationAmountRaw = Objects.requireNonNull(precipitationAmountRaw, "강수량 원문 필드는 필수입니다.");
        this.precipitationAmount = precipitationAmount;
        this.precipitationProbability = precipitationProbability;
        this.humidityCurrent = Objects.requireNonNull(humidityCurrent, "습도 필드는 필수입니다.");
        this.humidityComparedToDayBefore = humidityComparedToDayBefore;
        this.temperatureCurrent = Objects.requireNonNull(temperatureCurrent, "기온 필드는 필수입니다.");
        this.temperatureComparedToDayBefore = temperatureComparedToDayBefore;
        this.temperatureMin = temperatureMin;
        this.temperatureMax = temperatureMax;
        this.windSpeedRaw = Objects.requireNonNull(windSpeedRaw, "풍속 원문 필드는 필수입니다.");
        this.windSpeed = windSpeed;
        this.windSpeedAsWord = Objects.requireNonNull(windSpeedAsWord, "풍속 등급 필드는 필수입니다.");
    }
}
