package com.project.sb13ootbsteam04.weather.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
@ConditionalOnProperty(
        prefix = "weather.batch.schedule",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@Slf4j
public class WeatherForecastBatchScheduler {

    private static final String KMA_TIME_ZONE = "Asia/Seoul";

    private final JobOperator jobOperator;
    private final Job weatherForecastCollectionJob;
    private final JobRepository jobRepository;
    private final Clock clock;

    public WeatherForecastBatchScheduler(
            JobOperator jobOperator,
            @Qualifier(WeatherForecastBatchConfig.JOB_NAME) Job weatherForecastCollectionJob,
            JobRepository jobRepository) {
        this(jobOperator, weatherForecastCollectionJob, jobRepository,
                Clock.system(ZoneId.of(KMA_TIME_ZONE)));
    }

    WeatherForecastBatchScheduler(
            JobOperator jobOperator,
            Job weatherForecastCollectionJob,
            JobRepository jobRepository,
            Clock clock) {
        this.jobOperator = jobOperator;
        this.weatherForecastCollectionJob = weatherForecastCollectionJob;
        this.jobRepository = jobRepository;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${weather.batch.schedule.cron:0 20 2,5,8,11,14,17,20,23 * * *}",
            zone = KMA_TIME_ZONE)
    public void collectForecasts() {
        if (!jobRepository.findRunningJobExecutions(WeatherForecastBatchConfig.JOB_NAME).isEmpty()) {
            log.warn("단기예보 수집 배치가 이미 실행 중이므로 이번 실행을 건너뜁니다.");
            return;
        }

        var now = ZonedDateTime.now(clock);
        var baseDate = now.toLocalDate();
        var baseTime = now.toLocalTime().withMinute(0).withSecond(0).withNano(0);
        var parameters = new JobParametersBuilder()
                .addLocalDate(WeatherForecastBatchTasklet.BASE_DATE, baseDate)
                .addLocalTime(WeatherForecastBatchTasklet.BASE_TIME, baseTime)
                .toJobParameters();

        try {
            var execution = jobOperator.start(weatherForecastCollectionJob, parameters);
            if (execution.getStatus() == BatchStatus.FAILED) {
                log.warn("단기예보 수집 배치 실패: jobExecutionId={}, baseDate={}, baseTime={}",
                        execution.getId(), baseDate, baseTime);
                return;
            }
            log.info("단기예보 수집 배치 실행 완료: jobExecutionId={}, status={}, baseDate={}, baseTime={}",
                    execution.getId(), execution.getStatus(), baseDate, baseTime);
        } catch (Exception exception) {
            log.error("단기예보 수집 배치 시작 실패: baseDate={}, baseTime={}, exceptionType={}",
                    baseDate, baseTime, exception.getClass().getSimpleName());
        }
    }
}
