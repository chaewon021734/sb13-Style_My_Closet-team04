package com.project.sb13ootbsteam04.weather.event;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record WeatherChangeDetectedEvent(
        UUID locationId,
        UUID previousWeatherId,
        UUID currentWeatherId,
        Instant forecastAt,
        Set<WeatherChangeType> changes
) {

    public WeatherChangeDetectedEvent {
        Objects.requireNonNull(locationId, "위치 ID는 필수입니다.");
        Objects.requireNonNull(previousWeatherId, "이전 날씨 ID는 필수입니다.");
        Objects.requireNonNull(currentWeatherId, "현재 날씨 ID는 필수입니다.");
        Objects.requireNonNull(forecastAt, "예보 대상 시각은 필수입니다.");
        changes = Set.copyOf(Objects.requireNonNull(changes, "날씨 변화 목록은 필수입니다."));
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("날씨 변화 목록은 비어 있을 수 없습니다.");
        }
    }
}
