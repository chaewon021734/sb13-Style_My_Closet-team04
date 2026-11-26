package com.project.sb13ootbsteam04.weather.batch;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.weather.client.KmaForecastClient;
import com.project.sb13ootbsteam04.weather.dto.KmaForecastItem;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocation;
import com.project.sb13ootbsteam04.weather.exception.WeatherForecastException;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "spring.batch.jdbc.initialize-schema=always"
})
class WeatherForecastBatchJobIntegrationTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 11, 24);
    private static final LocalTime BASE_TIME = LocalTime.of(11, 0);
    private static final Map<String, String> DRY_FORECAST = Map.of(
            "SKY", "3", "PTY", "0", "PCP", "강수없음", "POP", "20",
            "REH", "60", "TMP", "12", "WSD", "1.2");

    @Autowired private JobOperator jobOperator;
    @Autowired @Qualifier(WeatherForecastBatchConfig.JOB_NAME) private Job job;
    @Autowired private WeatherLocationRepository locations;
    @Autowired private WeatherRepository weathers;
    @Autowired private MeterRegistry meterRegistry;
    @MockitoBean private KmaForecastClient client;

    @BeforeEach
    void cleanDatabase() {
        weathers.deleteAll();
        locations.deleteAll();
        reset(client);
    }

    @Test
    void collectsEveryLocationAndRecordsSuccessAndSavedCounts() throws Exception {
        locations.saveAndFlush(new WeatherLocation(37.5665, 126.978, 60, 127));
        when(client.getVillageForecast(BASE_DATE, BASE_TIME, 60, 127))
                .thenReturn(forecastItems(60, 127));
        double successBefore = counter(WeatherForecastBatchMetrics.LOCATION_SUCCESS);
        double savedBefore = counter(WeatherForecastBatchMetrics.FORECAST_SAVED);

        var execution = jobOperator.start(job, parameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(weathers.count()).isEqualTo(1);
        assertThat(counter(WeatherForecastBatchMetrics.LOCATION_SUCCESS) - successBefore).isEqualTo(1);
        assertThat(counter(WeatherForecastBatchMetrics.FORECAST_SAVED) - savedBefore).isEqualTo(1);
        var context = execution.getStepExecutions().iterator().next().getExecutionContext();
        assertThat(context.getInt(WeatherForecastBatchTasklet.TARGET_COUNT)).isEqualTo(1);
        assertThat(context.getInt(WeatherForecastBatchTasklet.SUCCESS_COUNT)).isEqualTo(1);
        assertThat(context.getInt(WeatherForecastBatchTasklet.FAILURE_COUNT)).isZero();
        assertThat(context.getInt(WeatherForecastBatchTasklet.SAVED_COUNT)).isEqualTo(1);
    }

    @Test
    void rerunningTheSameIssueDoesNotDuplicateForecasts() throws Exception {
        locations.saveAndFlush(new WeatherLocation(37.5665, 126.978, 60, 127));
        when(client.getVillageForecast(BASE_DATE, BASE_TIME, 60, 127))
                .thenReturn(forecastItems(60, 127));

        var first = jobOperator.start(job, parameters());
        var second = jobOperator.start(job, parameters());

        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(weathers.count()).isEqualTo(1);
        var secondContext = second.getStepExecutions().iterator().next().getExecutionContext();
        assertThat(secondContext.getInt(WeatherForecastBatchTasklet.SAVED_COUNT)).isZero();
    }

    @Test
    void keepsSuccessfulLocationsButFailsTheJobAfterAPartialFailure() throws Exception {
        locations.saveAndFlush(new WeatherLocation(37.5665, 126.978, 60, 127));
        locations.saveAndFlush(new WeatherLocation(35.1796, 129.0756, 98, 76));
        when(client.getVillageForecast(BASE_DATE, BASE_TIME, 60, 127))
                .thenReturn(forecastItems(60, 127));
        when(client.getVillageForecast(BASE_DATE, BASE_TIME, 98, 76))
                .thenThrow(new WeatherForecastException(
                        ErrorCode.WEATHER_FORECAST_SERVICE_UNAVAILABLE, "테스트 장애"));
        double failureBefore = counter(WeatherForecastBatchMetrics.LOCATION_FAILURE);

        var execution = jobOperator.start(job, parameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(weathers.count()).isEqualTo(1);
        assertThat(counter(WeatherForecastBatchMetrics.LOCATION_FAILURE) - failureBefore).isEqualTo(1);
        var context = execution.getStepExecutions().iterator().next().getExecutionContext();
        assertThat(context.getInt(WeatherForecastBatchTasklet.TARGET_COUNT)).isEqualTo(2);
        assertThat(context.getInt(WeatherForecastBatchTasklet.SUCCESS_COUNT)).isEqualTo(1);
        assertThat(context.getInt(WeatherForecastBatchTasklet.FAILURE_COUNT)).isEqualTo(1);
        assertThat(context.getInt(WeatherForecastBatchTasklet.SAVED_COUNT)).isEqualTo(1);
        assertThat(context.getString(WeatherForecastBatchTasklet.LAST_FAILURE_ERROR_CODE))
                .isEqualTo(ErrorCode.WEATHER_FORECAST_SERVICE_UNAVAILABLE.getCode());
        assertThat(execution.getAllFailureExceptions())
                .anySatisfy(exception -> {
                    assertThat(exception).isInstanceOf(WeatherForecastException.class);
                    assertThat(((WeatherForecastException) exception).getDetails().get("reason"))
                            .contains(ErrorCode.WEATHER_FORECAST_SERVICE_UNAVAILABLE.getCode());
                });
    }

    @Test
    void rejectsMissingIssueParametersBeforeStartingTheStep() {
        var invalid = new JobParametersBuilder()
                .addLocalDate(WeatherForecastBatchTasklet.BASE_DATE, BASE_DATE)
                .addString("executionId", UUID.randomUUID().toString())
                .toJobParameters();

        assertThatThrownBy(() -> jobOperator.start(job, invalid))
                .isInstanceOf(InvalidJobParametersException.class);
    }

    private org.springframework.batch.core.job.parameters.JobParameters parameters() {
        return new JobParametersBuilder()
                .addLocalDate(WeatherForecastBatchTasklet.BASE_DATE, BASE_DATE)
                .addLocalTime(WeatherForecastBatchTasklet.BASE_TIME, BASE_TIME)
                .addString("executionId", UUID.randomUUID().toString())
                .toJobParameters();
    }

    private List<KmaForecastItem> forecastItems(int gridX, int gridY) {
        return DRY_FORECAST.entrySet().stream()
                .map(entry -> new KmaForecastItem("20261124", "1100", "20261124", "1200",
                        entry.getKey(), entry.getValue(), gridX, gridY))
                .toList();
    }

    private double counter(String name) {
        return meterRegistry.get(name).counter().count();
    }
}
