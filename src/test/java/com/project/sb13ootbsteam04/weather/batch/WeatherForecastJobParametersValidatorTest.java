package com.project.sb13ootbsteam04.weather.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeatherForecastJobParametersValidatorTest {

    private final WeatherForecastJobParametersValidator validator =
            new WeatherForecastJobParametersValidator();

    @ParameterizedTest
    @ValueSource(ints = {2, 5, 8, 11, 14, 17, 20, 23})
    void acceptsSupportedIssueTimes(int hour) {
        var parameters = parameters(LocalTime.of(hour, 0));

        assertThatCode(() -> validator.validate(parameters)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedMinute() {
        assertThatThrownBy(() -> validator.validate(parameters(LocalTime.of(2, 30))))
                .isInstanceOf(InvalidJobParametersException.class);
    }

    @Test
    void rejectsNonZeroSecondAndNanosecond() {
        assertThatThrownBy(() -> validator.validate(
                parameters(LocalTime.of(2, 0, 1, 1))))
                .isInstanceOf(InvalidJobParametersException.class);
    }

    private org.springframework.batch.core.job.parameters.JobParameters parameters(LocalTime baseTime) {
        return new JobParametersBuilder()
                .addLocalDate(WeatherForecastBatchTasklet.BASE_DATE, LocalDate.of(2026, 11, 24))
                .addLocalTime(WeatherForecastBatchTasklet.BASE_TIME, baseTime)
                .toJobParameters();
    }
}
