package com.project.sb13ootbsteam04.weather.service;

import org.springframework.stereotype.Component;

@Component
public class WeatherGridConverter {

    // 기상청 단기예보의 Lambert Conformal Conic 격자(5km) 변환 상수.
    private static final int ORIGIN_X = 43;
    private static final int ORIGIN_Y = 136;
    private static final double EARTH_RADIUS = 6371.00877 / 5.0;
    private static final double STANDARD_LATITUDE_1 = Math.toRadians(30.0);
    private static final double STANDARD_LATITUDE_2 = Math.toRadians(60.0);
    private static final double ORIGIN_LONGITUDE = Math.toRadians(126.0);
    private static final double ORIGIN_LATITUDE = Math.toRadians(38.0);

    public Grid convert(double latitude, double longitude) {
        double exponent = Math.log(Math.cos(STANDARD_LATITUDE_1) / Math.cos(STANDARD_LATITUDE_2))
                / Math.log(Math.tan(Math.PI / 4 + STANDARD_LATITUDE_2 / 2)
                / Math.tan(Math.PI / 4 + STANDARD_LATITUDE_1 / 2));
        double scale = Math.pow(Math.tan(Math.PI / 4 + STANDARD_LATITUDE_1 / 2), exponent)
                * Math.cos(STANDARD_LATITUDE_1) / exponent;
        double originRadius = EARTH_RADIUS * scale
                / Math.pow(Math.tan(Math.PI / 4 + ORIGIN_LATITUDE / 2), exponent);
        double radius = EARTH_RADIUS * scale
                / Math.pow(Math.tan(Math.PI / 4 + Math.toRadians(latitude) / 2), exponent);
        double angle = Math.toRadians(longitude) - ORIGIN_LONGITUDE;
        if (angle > Math.PI) angle -= 2 * Math.PI;
        if (angle < -Math.PI) angle += 2 * Math.PI;
        angle *= exponent;
        return new Grid((int) Math.floor(radius * Math.sin(angle) + ORIGIN_X + 0.5),
                (int) Math.floor(originRadius - radius * Math.cos(angle) + ORIGIN_Y + 0.5));
    }

    public record Grid(int x, int y) {
    }
}
