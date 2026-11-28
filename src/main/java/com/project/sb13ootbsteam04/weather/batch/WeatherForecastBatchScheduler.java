package com.project.sb13ootbsteam04.weather.batch;

import java.util.UUID;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final String LOCK_KEY_PREFIX = "weather:forecast-batch:";
    // 한 번의 수집이 끝나기에 충분한 시간
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final JobOperator jobOperator;
    private final Job weatherForecastCollectionJob;
    private final JobRepository jobRepository;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    // Spring Bean 생성 시 사용할 생성자 명시
    @Autowired
    public WeatherForecastBatchScheduler(
            JobOperator jobOperator,
            @Qualifier(WeatherForecastBatchConfig.JOB_NAME) Job weatherForecastCollectionJob,
            JobRepository jobRepository,
            StringRedisTemplate redisTemplate) {
        this(jobOperator, weatherForecastCollectionJob, jobRepository, redisTemplate,
                Clock.system(ZoneId.of(KMA_TIME_ZONE)));
    }

    WeatherForecastBatchScheduler(
            JobOperator jobOperator,
            Job weatherForecastCollectionJob,
            JobRepository jobRepository,
            StringRedisTemplate redisTemplate,
            Clock clock) {
        this.jobOperator = jobOperator;
        this.weatherForecastCollectionJob = weatherForecastCollectionJob;
        this.jobRepository = jobRepository;
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${weather.batch.schedule.cron:0 20 2,5,8,11,14,17,20,23 * * *}",
            zone = KMA_TIME_ZONE)
    public void collectForecasts() {
        var now = ZonedDateTime.now(clock);
        var baseDate = now.toLocalDate();
        var baseTime = now.toLocalTime().withMinute(0).withSecond(0).withNano(0);
        var parameters = new JobParametersBuilder()
                .addLocalDate(WeatherForecastBatchTasklet.BASE_DATE, baseDate)
                .addLocalTime(WeatherForecastBatchTasklet.BASE_TIME, baseTime)
                .toJobParameters();

        // 여러 인스턴스 중 같은 발표 시각의 잠금을 얻은 하나만 실행
        if (!acquireLock(baseDate, baseTime)) {
            log.info("다른 인스턴스가 단기예보 수집을 실행 중이므로 건너뜁니다. baseDate={}, baseTime={}",
                    baseDate, baseTime);
            return;
        }

        if (!jobRepository.findRunningJobExecutions(WeatherForecastBatchConfig.JOB_NAME).isEmpty()) {
            log.warn("단기예보 수집 배치가 이미 실행 중이므로 이번 실행을 건너뜁니다.");
            return;
        }

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

    private boolean acquireLock(LocalDate baseDate, LocalTime baseTime) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    LOCK_KEY_PREFIX + baseDate + "T" + baseTime,
                    UUID.randomUUID().toString(),
                    LOCK_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (DataAccessException exception) {
            // Redis 장애 시에도 수집은 계속한다. 저장 로직이 같은 예보의 중복 저장을 막는다.
            log.warn("단기예보 수집 실행 잠금을 확인하지 못해 그대로 실행합니다. exceptionType={}",
                    exception.getClass().getSimpleName());
            return true;
        }
    }
}
