package com.project.sb13ootbsteam04.weather.entity;

import com.project.sb13ootbsteam04.global.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "weather_locations")
@AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherLocation extends BaseEntity {

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "grid_x", nullable = false)
    private int gridX;

    @Column(name = "grid_y", nullable = false)
    private int gridY;

    public WeatherLocation(double latitude, double longitude, int gridX, int gridY) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("유효한 위도와 경도가 필요합니다.");
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.gridX = gridX;
        this.gridY = gridY;
    }
}
