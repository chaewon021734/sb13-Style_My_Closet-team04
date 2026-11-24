package com.project.sb13ootbsteam04.weather.batch;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WeatherForecastBatchConfig {

    public static final String JOB_NAME = "weatherForecastCollectionJob";
    public static final String STEP_NAME = "weatherForecastCollectionStep";

    @Bean(name = STEP_NAME)
    public Step weatherForecastCollectionStep(
            JobRepository jobRepository,
            WeatherForecastBatchTasklet tasklet) {
        // Spring Batch 6의 기본 무자원 트랜잭션을 사용해 위치별 서비스 트랜잭션을 독립 커밋한다.
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(tasklet)
                .build();
    }

    @Bean(name = JOB_NAME)
    public Job weatherForecastCollectionJob(
            JobRepository jobRepository,
            @Qualifier(STEP_NAME) Step step,
            WeatherForecastJobParametersValidator validator) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(validator)
                .start(step)
                .build();
    }
}
