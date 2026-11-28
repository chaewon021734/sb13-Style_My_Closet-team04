package com.project.sb13ootbsteam04.auth.scheduler;

import com.project.sb13ootbsteam04.auth.service.RefreshTokenCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RefreshTokenCleanupSchedulerConfigTest {

    private static final String ENABLED = "jwt.refresh-token-cleanup.enabled";
    private static final String INITIAL_DELAY = "jwt.refresh-token-cleanup.initial-delay-ms";
    private static final String FIXED_DELAY = "jwt.refresh-token-cleanup.fixed-delay-ms";

    // 실제 DB 없이 조건부 Bean 등록과 예약 실행을 검증한다.
    private ApplicationContextRunner runner() {
        return runner(mock(RefreshTokenCleanupService.class));
    }

    private ApplicationContextRunner runner(RefreshTokenCleanupService service) {
        return new ApplicationContextRunner()
                .withUserConfiguration(SchedulingConfig.class)
                .withBean(RefreshTokenCleanupService.class, () -> service);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @Import(RefreshTokenCleanupScheduler.class)
    static class SchedulingConfig {
    }

    @Test
    void shouldRegisterSchedulerWhenEnabled() {
        runner().withPropertyValues(ENABLED + "=true", INITIAL_DELAY + "=60000")
                .run(context -> assertThat(context)
                        .hasSingleBean(RefreshTokenCleanupScheduler.class));
    }

    @Test
    void shouldNotRegisterSchedulerWhenDisabled() {
        runner().withPropertyValues(ENABLED + "=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(RefreshTokenCleanupScheduler.class));
    }

    @Test
    void shouldNotRegisterSchedulerWhenPropertyIsMissing() {
        runner().run(context -> assertThat(context)
                .doesNotHaveBean(RefreshTokenCleanupScheduler.class));
    }

    @Test
    void shouldExecuteCleanupRepeatedlyWhenEnabled() {
        CountDownLatch calls = new CountDownLatch(2);
        RefreshTokenCleanupService service = mock(RefreshTokenCleanupService.class);
        when(service.cleanupExpiredTokens()).thenAnswer(invocation -> {
            calls.countDown();
            return 0;
        });
        runner(service).withPropertyValues(ENABLED + "=true", INITIAL_DELAY + "=0", FIXED_DELAY + "=50")
                .run(context -> {
                    assertThat(context).hasSingleBean(RefreshTokenCleanupScheduler.class);
                    assertThat(calls.await(10, TimeUnit.SECONDS)).isTrue();
                });
    }

    @Test
    void shouldNotScheduleCleanupWhenDisabled() {
        runner().withPropertyValues(ENABLED + "=false", INITIAL_DELAY + "=0", FIXED_DELAY + "=50")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RefreshTokenCleanupScheduler.class);
                    assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class)
                            .getScheduledTasks()).isEmpty();
                    verifyNoInteractions(context.getBean(RefreshTokenCleanupService.class));
                });
    }
}
