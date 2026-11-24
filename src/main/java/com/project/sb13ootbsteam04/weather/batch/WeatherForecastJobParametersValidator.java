package com.project.sb13ootbsteam04.weather.batch;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;
import org.springframework.stereotype.Component;

@Component
class WeatherForecastJobParametersValidator implements JobParametersValidator {

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        if (parameters == null
                || parameters.getLocalDate(WeatherForecastBatchTasklet.BASE_DATE) == null
                || parameters.getLocalTime(WeatherForecastBatchTasklet.BASE_TIME) == null) {
            throw new InvalidJobParametersException("baseDate와 baseTime JobParameter는 필수입니다.");
        }
    }
}
