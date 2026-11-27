package com.project.sb13ootbsteam04.auth.event;

import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordMailService;
import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordRecoveryService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.mockito.Mockito.*;

@SpringJUnitConfig(
        classes = TemporaryPasswordTransactionIntegrationTest.TestConfig.class
)
class TemporaryPasswordTransactionIntegrationTest {

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "TempPassword123!";
    private static final String PASSWORD_HASH = "encodedTemporaryPassword";
    private static final long LOGIN_VERSION = 2L;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private TemporaryPasswordMailService mailService;

    @Autowired
    private TemporaryPasswordRecoveryService recoveryService;

    // 1. 커밋 전에는 이메일을 발송하지 않음
    @Test
    void shouldNotSendEmailBeforeCommit() {

        reset(mailService, recoveryService);

        transactionTemplate.executeWithoutResult(status -> {

            eventPublisher.publishEvent(createEvent());

            verifyNoInteractions(mailService, recoveryService);
        });

        verify(mailService).sendTemporaryPassword(
                EMAIL,
                PASSWORD
        );

        verifyNoInteractions(recoveryService);
    }

    // 2. 정상 커밋 이후 이메일 한 번 발송
    @Test
    void shouldSendEmailAfterSuccessfulCommit() {

        reset(mailService, recoveryService);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(createEvent());
        });

        verify(mailService, times(1))
                .sendTemporaryPassword(EMAIL, PASSWORD);

        verifyNoInteractions(recoveryService);
    }

    // 3. 롤백되면 이메일 발송하지 않음
    @Test
    void shouldNotSendEmailAfterRollback() {

        reset(mailService, recoveryService);

        transactionTemplate.executeWithoutResult(status -> {

            eventPublisher.publishEvent(createEvent());

            status.setRollbackOnly();
        });

        verifyNoInteractions(mailService, recoveryService);
    }

    // 이벤트 생성
    private TemporaryPasswordIssuedEvent createEvent() {

        return new TemporaryPasswordIssuedEvent(
                EMAIL,
                PASSWORD,
                PASSWORD_HASH,
                LOGIN_VERSION
        );
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DataSource dataSource() {

            DriverManagerDataSource dataSource =
                    new DriverManagerDataSource();

            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl(
                    "jdbc:h2:mem:temporary_password_tx;DB_CLOSE_DELAY=-1"
            );
            dataSource.setUsername("sa");
            dataSource.setPassword("");

            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(
                DataSource dataSource
        ) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TransactionTemplate transactionTemplate(
                PlatformTransactionManager transactionManager
        ) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        TemporaryPasswordMailService mailService() {
            return mock(TemporaryPasswordMailService.class);
        }

        @Bean
        TemporaryPasswordRecoveryService recoveryService() {
            return mock(TemporaryPasswordRecoveryService.class);
        }

        @Bean
        TemporaryPasswordMailListener mailListener(
                TemporaryPasswordMailService mailService,
                TemporaryPasswordRecoveryService recoveryService
        ) {
            return new TemporaryPasswordMailListener(
                    mailService,
                    recoveryService
            );
        }
    }
}
