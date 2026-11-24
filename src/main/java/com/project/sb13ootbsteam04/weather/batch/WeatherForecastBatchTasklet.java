package com.project.sb13ootbsteam04.weather.batch;

import com.project.sb13ootbsteam04.weather.client.KmaForecastClient;
import com.project.sb13ootbsteam04.weather.exception.WeatherForecastException;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationRepository;
import com.project.sb13ootbsteam04.weather.service.WeatherForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.UUID;

import static com.project.sb13ootbsteam04.global.exception.ErrorCode.WEATHER_FORECAST_BATCH_PARTIAL_FAILURE;

@Component
@RequiredArgsConstructor
@Slf4j
public class WeatherForecastBatchTasklet implements Tasklet {

    public static final String BASE_DATE = "baseDate";
    public static final String BASE_TIME = "baseTime";
    public static final String TARGET_COUNT = "weather.targetCount";
    public static final String SUCCESS_COUNT = "weather.successCount";
    public static final String FAILURE_COUNT = "weather.failureCount";
    public static final String SAVED_COUNT = "weather.savedCount";

    private final WeatherLocationRepository locations;
    private final KmaForecastClient client;
    private final WeatherForecastService forecastService;
    private final WeatherForecastBatchMetrics metrics;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        var stepExecution = contribution.getStepExecution();
        var parameters = stepExecution.getJobParameters();
        var baseDate = parameters.getLocalDate(BASE_DATE);
        var baseTime = parameters.getLocalTime(BASE_TIME);
        var targets = locations.findAllByOrderByIdAsc();
        var failures = new ArrayList<UUID>();
        int successCount = 0;
        int savedCount = 0;

        for (var location : targets) {
            contribution.incrementReadCount();
            try {
                var items = client.getVillageForecast(
                        baseDate, baseTime, location.getGridX(), location.getGridY());
                int saved = forecastService.saveForecast(location.getId(), items);
                savedCount += saved;
                successCount++;
                contribution.incrementWriteCount(saved);
                metrics.recordSuccess(saved);
            } catch (RuntimeException exception) {
                failures.add(location.getId());
                contribution.incrementProcessSkipCount();
                metrics.recordFailure();
                log.warn("단기예보 위치 수집 실패: locationId={}, error={}",
                        location.getId(), exception.getClass().getSimpleName());
            }
        }

        var context = stepExecution.getExecutionContext();
        context.putInt(TARGET_COUNT, targets.size());
        context.putInt(SUCCESS_COUNT, successCount);
        context.putInt(FAILURE_COUNT, failures.size());
        context.putInt(SAVED_COUNT, savedCount);

        if (!failures.isEmpty()) {
            throw new WeatherForecastException(WEATHER_FORECAST_BATCH_PARTIAL_FAILURE,
                    "실패 위치 수: " + failures.size());
        }
        return RepeatStatus.FINISHED;
    }
}
