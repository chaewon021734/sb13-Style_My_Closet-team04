package com.project.sb13ootbsteam04.weather.batch;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import static org.mockito.ArgumentMatchers.anyString;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeatherForecastBatchSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock SCHEDULED_TIME = Clock.fixed(
            Instant.parse("2026-11-27T05:20:00Z"), SEOUL);

    private final JobOperator jobOperator = mock(JobOperator.class);
    private final Job job = mock(Job.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    // 기본은 이 인스턴스가 실행 잠금을 얻은 상태
    @BeforeEach
    void givenLockAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    void skipsWhenAnotherInstanceHoldsTheLock() throws Exception {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        scheduler().collectForecasts();

        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    void runsWhenRedisIsUnavailable() throws Exception {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("연결 실패"));
        when(jobRepository.findRunningJobExecutions(WeatherForecastBatchConfig.JOB_NAME))
                .thenReturn(Set.of());
        var execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobOperator.start(any(Job.class), any(JobParameters.class))).thenReturn(execution);

        scheduler().collectForecasts();

        // 저장 로직이 중복을 막으므로 수집이 멈추는 것보다 실행이 낫다
        verify(jobOperator).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    void startsJobWithScheduledKstIssueParameters() throws Exception {
        var execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobRepository.findRunningJobExecutions(WeatherForecastBatchConfig.JOB_NAME))
                .thenReturn(Set.of());
        when(jobOperator.start(any(Job.class), any(JobParameters.class)))
                .thenReturn(execution);
        var scheduler = scheduler();

        scheduler.collectForecasts();

        var parametersCaptor = org.mockito.ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(org.mockito.ArgumentMatchers.same(job),
                parametersCaptor.capture());
        assertThat(parametersCaptor.getValue()
                .getLocalDate(WeatherForecastBatchTasklet.BASE_DATE))
                .isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(parametersCaptor.getValue()
                .getLocalTime(WeatherForecastBatchTasklet.BASE_TIME))
                .isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    void skipsWhenCollectionJobIsAlreadyRunning() throws Exception {
        when(jobRepository.findRunningJobExecutions(WeatherForecastBatchConfig.JOB_NAME))
                .thenReturn(Set.of(mock(JobExecution.class)));
        var scheduler = scheduler();

        scheduler.collectForecasts();

        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    void containsLaunchFailureSoFutureSchedulesCanContinue() throws Exception {
        when(jobRepository.findRunningJobExecutions(WeatherForecastBatchConfig.JOB_NAME))
                .thenReturn(Set.of());
        when(jobOperator.start(any(Job.class), any(JobParameters.class)))
                .thenThrow(new IllegalStateException("test failure"));
        var scheduler = scheduler();

        scheduler.collectForecasts();

        verify(jobOperator).start(any(Job.class), any(JobParameters.class));
    }

    private WeatherForecastBatchScheduler scheduler() {
        return new WeatherForecastBatchScheduler(
                jobOperator, job, jobRepository, redisTemplate, SCHEDULED_TIME);
    }
}
