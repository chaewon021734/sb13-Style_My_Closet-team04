package com.project.sb13ootbsteam04.weather.batch;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class WeatherForecastBatchMetrics {

    public static final String LOCATION_SUCCESS = "otboo.weather.batch.location.success";
    public static final String LOCATION_FAILURE = "otboo.weather.batch.location.failure";
    public static final String FORECAST_SAVED = "otboo.weather.batch.forecast.saved";

    private final Counter locationSuccess;
    private final Counter locationFailure;
    private final Counter forecastSaved;

    public WeatherForecastBatchMetrics(MeterRegistry registry) {
        this.locationSuccess = Counter.builder(LOCATION_SUCCESS)
                .description("성공한 날씨 위치 수")
                .tag("job", WeatherForecastBatchConfig.JOB_NAME)
                .register(registry);
        this.locationFailure = Counter.builder(LOCATION_FAILURE)
                .description("실패한 날씨 위치 수")
                .tag("job", WeatherForecastBatchConfig.JOB_NAME)
                .register(registry);
        this.forecastSaved = Counter.builder(FORECAST_SAVED)
                .description("새로 저장한 단기예보 수")
                .tag("job", WeatherForecastBatchConfig.JOB_NAME)
                .register(registry);
    }

    void recordSuccess(int savedCount) {
        locationSuccess.increment();
        if (savedCount > 0) {
            forecastSaved.increment(savedCount);
        }
    }

    void recordFailure() {
        locationFailure.increment();
    }
}
