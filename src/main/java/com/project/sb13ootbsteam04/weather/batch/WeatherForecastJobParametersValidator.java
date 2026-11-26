package com.project.sb13ootbsteam04.weather.batch;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Set;

@Component
class WeatherForecastJobParametersValidator implements JobParametersValidator {

    private static final Set<LocalTime> SUPPORTED_BASE_TIMES = Set.of(
            LocalTime.of(2, 0), LocalTime.of(5, 0), LocalTime.of(8, 0),
            LocalTime.of(11, 0), LocalTime.of(14, 0), LocalTime.of(17, 0),
            LocalTime.of(20, 0), LocalTime.of(23, 0));

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        if (parameters == null) {
            throw new InvalidJobParametersException("baseDate와 baseTime JobParameter는 필수입니다.");
        }
        var baseDate = parameters.getLocalDate(WeatherForecastBatchTasklet.BASE_DATE);
        var baseTime = parameters.getLocalTime(WeatherForecastBatchTasklet.BASE_TIME);
        if (baseDate == null || baseTime == null) {
            throw new InvalidJobParametersException("baseDate와 baseTime JobParameter는 필수입니다.");
        }
        if (!SUPPORTED_BASE_TIMES.contains(baseTime)) {
            throw new InvalidJobParametersException(
                    "baseTime은 단기예보 발표 시각(02, 05, 08, 11, 14, 17, 20, 23시)이어야 합니다.");
        }
    }
}
