package com.project.sb13ootbsteam04.weather.entity;

import com.project.sb13ootbsteam04.global.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "weather_location_names")
@AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherLocationName extends BaseEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false, length = 100)
    private String name;

    public WeatherLocationName(UUID locationId, int displayOrder, String name) {
        this.locationId = Objects.requireNonNull(locationId, "위치 ID는 필수입니다.");
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("행정구역명은 1~100자여야 합니다.");
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException("표시 순서는 0 이상이어야 합니다.");
        }
        this.displayOrder = displayOrder;
        this.name = name;
    }
}
